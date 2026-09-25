package net.srv.eloranks.core;

import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import net.srv.eloranks.data.EloDatabase;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.elo.EloCalculator;
import net.srv.eloranks.elo.EloSettings;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.rank.TierLadder;

/**
 * The ranking rules on top of the database: kills, admin ELO changes and unlocking. No server
 * API, so it is unit tested. Must only be called on the database thread; every method runs in one
 * transaction.
 */
public final class RankingLogic {
   private final EloDatabase db;

   public RankingLogic(EloDatabase db) {
      this.db = db;
   }

   public EloDatabase db() {
      return this.db;
   }

   /** One player killed another. Anti-farming decides whether it counts. */
   public KillOutcome kill(UUID killerId, String killerName, UUID victimId, String victimName, boolean sameIp,
                           long now, EloSettings elo, AntiFarmSettings farm, TierLadder ladder) throws SQLException {
      return this.db.inTransaction(() -> {
         PlayerRecord killer = this.db.loadOrCreate(killerId, killerName, elo.startingElo(), now);
         PlayerRecord victim = this.db.loadOrCreate(victimId, victimName, elo.startingElo(), now);

         KillOutcome.FarmRule rule = null;
         long detail = 0;
         int gainCap = -1;
         if (farm.blockSameIp() && sameIp) {
            rule = KillOutcome.FarmRule.SAME_IP;
         }
         if (rule == null && farm.sameVictimCooldownMillis() > 0) {
            OptionalLong last = this.db.lastCountedKill(killerId, victimId);
            if (last.isPresent() && now - last.getAsLong() < farm.sameVictimCooldownMillis()) {
               rule = KillOutcome.FarmRule.SAME_VICTIM_COOLDOWN;
               detail = farm.sameVictimCooldownMillis() - (now - last.getAsLong());
            }
         }
         if (rule == null && farm.sameVictimLimit() > 0
               && this.db.countedKills(killerId, victimId, now - farm.sameVictimWindowMillis()) >= farm.sameVictimLimit()) {
            rule = KillOutcome.FarmRule.SAME_VICTIM_LIMIT;
            detail = farm.sameVictimLimit();
         }
         if (rule == null && farm.gainLimit() > 0) {
            long gained = this.db.gainSince(killerId, now - farm.gainWindowMillis());
            gainCap = (int) Math.max(0, farm.gainLimit() - gained);
            if (gainCap == 0) {
               rule = KillOutcome.FarmRule.GAIN_LIMIT;
               detail = farm.gainLimit();
            }
         }
         if (rule != null) {
            this.db.insertKill(killerId, victimId, now, 0, 0, false, rule.name());
            return new KillOutcome(rule, detail, null, killer, victim, List.of());
         }

         EloCalculator.Change change = EloCalculator.kill(killer.elo(), victim.elo(), elo, gainCap);
         this.db.updateElo(killerId, change.killerAfter(), change.gain(), 0, 1, 0, now);
         this.db.updateElo(victimId, change.victimAfter(), 0, change.loss(), 0, 1, now);
         this.db.insertKill(killerId, victimId, now, change.gain(), change.loss(), true, null);
         List<String> unlocked = this.unlockReached(killerId, change.killerAfter(), ladder, now);
         return new KillOutcome(null, 0, change, this.db.load(killerId).orElseThrow(), this.db.load(victimId).orElseThrow(), unlocked);
      });
   }

   /** Result of an admin ELO change. */
   public record EloUpdate(int before, PlayerRecord after, List<String> unlocked) {
   }

   /** Sets ELO (clamped to the configured floor/maximum, never negative). */
   public EloUpdate setElo(UUID uuid, long elo, EloSettings settings, TierLadder ladder, long now) throws SQLException {
      return this.db.inTransaction(() -> {
         PlayerRecord before = this.db.load(uuid).orElseThrow(() -> new SQLException("unknown player " + uuid));
         int after = settings.clamp(elo);
         int delta = after - before.elo();
         this.db.updateElo(uuid, after, Math.max(0, delta), Math.max(0, -delta), 0, 0, now);
         List<String> unlocked = this.unlockReached(uuid, after, ladder, now);
         return new EloUpdate(before.elo(), this.db.load(uuid).orElseThrow(), unlocked);
      });
   }

   /** Loads a joining player (creating them), and unlocks what their ELO reaches (e.g. after a config change). */
   public EloUpdate join(UUID uuid, String name, EloSettings settings, TierLadder ladder, long now) throws SQLException {
      return this.db.inTransaction(() -> {
         PlayerRecord record = this.db.loadOrCreate(uuid, name, settings.startingElo(), now);
         List<String> unlocked = this.unlockReached(uuid, record.elo(), ladder, now);
         return new EloUpdate(record.elo(), unlocked.isEmpty() ? record : this.db.load(uuid).orElseThrow(), unlocked);
      });
   }

   /** Locks every reward again, then unlocks what the current ELO still reaches. */
   public PlayerRecord resetProgress(UUID uuid, TierLadder ladder, long now) throws SQLException {
      return this.db.inTransaction(() -> {
         PlayerRecord record = this.db.load(uuid).orElseThrow(() -> new SQLException("unknown player " + uuid));
         this.db.resetUnlocks(uuid);
         this.unlockReached(uuid, record.elo(), ladder, now);
         return this.db.load(uuid).orElseThrow();
      });
   }

   private List<String> unlockReached(UUID uuid, int elo, TierLadder ladder, long now) throws SQLException {
      return this.db.unlock(uuid, ladder.reachedBy(elo).stream().map(Tier::id).toList(), now);
   }
}
