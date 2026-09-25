package net.srv.eloranks.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import net.srv.eloranks.EloRanksPlugin;
import net.srv.eloranks.config.Messages;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.core.KillOutcome;
import net.srv.eloranks.core.RankingLogic;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.rank.RankFormat;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.rank.TierLadder;
import net.srv.eloranks.util.Durations;
import net.srv.eloranks.util.Placeholders;
import net.srv.eloranks.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Joins, PvP kills and admin ELO changes, and what players see when their rank changes. */
public final class RankService {
   private final EloRanksPlugin plugin;

   public RankService(EloRanksPlugin plugin) {
      this.plugin = plugin;
   }

   private DataService data() {
      return this.plugin.data();
   }

   /** Loads (or creates) a joining player, syncs their LuckPerms rank and tells them about pending rewards. */
   public void join(Player player) {
      Settings settings = this.plugin.settings();
      UUID id = player.getUniqueId();
      String name = player.getName();
      this.data().submit(logic -> logic.join(id, name, settings.elo(), settings.ladder(), System.currentTimeMillis()))
            .whenComplete((update, error) -> {
               if (error != null) {
                  Tasks.onEntity(this.plugin, player, () -> this.plugin.messages().send(player, "database-error"), null);
                  return;
               }
               if (!player.isOnline()) {
                  return;
               }
               this.data().cache(update.after());
               this.plugin.display().apply(id, RankFormat.displayed(settings, update.after()));
               Tasks.onEntity(this.plugin, player, () -> {
                  this.announceUnlocks(player, update.unlocked());
                  if (update.after().pending() > 0) {
                     this.plugin.claims().deliverPending(player, true);
                  }
               }, null);
            });
   }

   /** A player kill. Runs the ELO calculation and anti-farming on the database thread. */
   public void kill(UUID killerId, String killerName, UUID victimId, String victimName, boolean sameIp) {
      Settings settings = this.plugin.settings();
      long now = System.currentTimeMillis();
      this.data().submit(logic -> logic.kill(killerId, killerName, victimId, victimName, sameIp, now,
                  settings.elo(), settings.antiFarm(), settings.ladder()))
            .thenAccept(outcome -> this.afterKill(settings, outcome, killerName, victimName));
   }

   private void afterKill(Settings settings, KillOutcome outcome, String killerName, String victimName) {
      Messages messages = this.plugin.messages();
      Player killer = Bukkit.getPlayer(outcome.killer().uuid());
      Player victim = Bukkit.getPlayer(outcome.victim().uuid());
      if (!outcome.counted()) {
         if (killer != null) {
            String reasonKey = switch (outcome.blocked()) {
               case SAME_VICTIM_COOLDOWN -> "same-victim-cooldown";
               case SAME_VICTIM_LIMIT -> "same-victim-limit";
               case GAIN_LIMIT -> "gain-limit";
               case SAME_IP -> "same-ip";
            };
            Placeholders p = new Placeholders().with("victim", victimName).with("killer", killerName)
                  .with("remaining", Durations.format(outcome.blockedDetail())).with("limit", outcome.blockedDetail());
            String reason = Text.fill(messages.raw("reasons." + reasonKey), p.map());
            Tasks.onEntity(this.plugin, killer, () -> messages.send(killer, "kill-not-counted", p.with("reason", reason)), null);
         }
         return;
      }
      if (killer != null) {
         this.data().cache(outcome.killer());
         Placeholders p = new Placeholders().with("amount", outcome.change().gain()).with("victim", victimName)
               .with("killer", killerName).with("elo", outcome.killer().elo());
         Tasks.onEntity(this.plugin, killer, () -> messages.send(killer, "elo-gained", p), null);
      }
      if (victim != null) {
         this.data().cache(outcome.victim());
         Placeholders p = new Placeholders().with("amount", outcome.change().loss()).with("victim", victimName)
               .with("killer", killerName).with("elo", outcome.victim().elo());
         Tasks.onEntity(this.plugin, victim, () -> messages.send(victim, "elo-lost", p), null);
      }
      this.rankChanged(settings, outcome.change().killerBefore(), outcome.killer(), outcome.unlocked());
      this.rankChanged(settings, outcome.change().victimBefore(), outcome.victim(), List.of());
   }

   /** Admin: set ELO. Clamped between the configured floor (never below 0) and maximum. */
   public CompletableFuture<RankingLogic.EloUpdate> setElo(UUID player, long elo) {
      Settings settings = this.plugin.settings();
      return this.data().submit(logic -> logic.setElo(player, elo, settings.elo(), settings.ladder(), System.currentTimeMillis()))
            .thenApply(update -> {
               this.rankChanged(settings, update.before(), update.after(), update.unlocked());
               this.plugin.leaderboard().refresh();
               return update;
            });
   }

   /** Admin: add (or with a negative amount remove) ELO, atomically on the database thread. */
   public CompletableFuture<RankingLogic.EloUpdate> addElo(UUID player, long amount) {
      Settings settings = this.plugin.settings();
      return this.data().submit(logic -> {
               PlayerRecord record = logic.db().load(player).orElseThrow(() -> new IllegalStateException("unknown player"));
               return logic.setElo(player, record.elo() + amount, settings.elo(), settings.ladder(), System.currentTimeMillis());
            })
            .thenApply(update -> {
               this.rankChanged(settings, update.before(), update.after(), update.unlocked());
               this.plugin.leaderboard().refresh();
               return update;
            });
   }

   /** Admin: re-lock rewards (then unlock what the current ELO still reaches). */
   public CompletableFuture<PlayerRecord> resetProgress(UUID player) {
      Settings settings = this.plugin.settings();
      return this.data().submit(logic -> logic.resetProgress(player, settings.ladder(), System.currentTimeMillis()))
            .thenApply(record -> {
               this.refreshOnline(settings, record);
               return record;
            });
   }

   public CompletableFuture<PlayerRecord> resetClaims(UUID player) {
      return this.data().submit(logic -> {
         logic.db().resetClaims(player);
         return logic.db().load(player).orElseThrow();
      }).thenApply(record -> {
         this.refreshOnline(this.plugin.settings(), record);
         return record;
      });
   }

   /** Admin: delete every piece of ranking data. An online player starts over at the starting ELO. */
   public CompletableFuture<Boolean> wipe(UUID player) {
      return this.data().submit(logic -> logic.db().wipe(player)).thenApply(removed -> {
         this.data().forget(player);
         Player online = Bukkit.getPlayer(player);
         if (online != null) {
            this.join(online);
         } else {
            this.plugin.display().apply(player, Optional.empty());
         }
         this.plugin.leaderboard().refresh();
         return removed;
      });
   }

   private void refreshOnline(Settings settings, PlayerRecord record) {
      if (Bukkit.getPlayer(record.uuid()) != null) {
         this.data().cache(record);
      }
      this.plugin.display().apply(record.uuid(), RankFormat.displayed(settings, record));
      this.plugin.leaderboard().refresh();
   }

   /** Updates the cache and LuckPerms, and shows rank up / rank down / unlock messages. */
   private void rankChanged(Settings settings, int eloBefore, PlayerRecord after, List<String> unlocked) {
      TierLadder ladder = settings.ladder();
      java.util.Set<String> unlockedBefore = new java.util.HashSet<>(after.unlocked());
      unlocked.forEach(unlockedBefore::remove);
      Optional<Tier> before = RankFormat.displayed(settings, eloBefore, unlockedBefore);
      Optional<Tier> now = RankFormat.displayed(settings, after);
      Player player = Bukkit.getPlayer(after.uuid());
      if (player != null) {
         this.data().cache(after);
      }
      if (!before.equals(now)) {
         this.plugin.display().apply(after.uuid(), now);
      }
      if (player == null) {
         return;
      }
      int from = ladder.rankIndex(before);
      int to = ladder.rankIndex(now);
      Tasks.onEntity(this.plugin, player, () -> {
         if (to > from && now.isPresent()) {
            this.rankUp(settings, player, now.get());
            List<String> others = unlocked.stream().filter(id -> !id.equals(now.get().id())).toList();
            this.announceUnlocks(player, others);
         } else {
            if (to < from) {
               Placeholders p = new Placeholders().with("tier_name", RankFormat.name(settings, now))
                     .with("tier_color", RankFormat.color(settings, now));
               this.plugin.messages().send(player, "rank-down", p);
            }
            this.announceUnlocks(player, unlocked);
         }
      }, null);
   }

   private void announceUnlocks(Player player, List<String> tierIds) {
      Settings settings = this.plugin.settings();
      for (String id : tierIds) {
         settings.ladder().byId(id).ifPresent(tier ->
               this.plugin.messages().send(player, "reward-unlocked", RankFormat.tier(settings, tier)));
      }
   }

   /** Rank-up message, broadcast, title, sound and particles; each can be turned off. Player thread. */
   private void rankUp(Settings settings, Player player, Tier tier) {
      Settings.RankUp fx = settings.rankUp();
      Placeholders p = RankFormat.tier(settings, tier).with("player", player.getName());
      if (fx.message()) {
         this.plugin.messages().sendLines(player, fx.messageLines(), p);
      }
      if (fx.broadcast() && !fx.broadcastText().isBlank()) {
         var line = Text.chat(fx.broadcastText(), new Placeholders().with("prefix", this.plugin.messages().raw("prefix")).with(p).map());
         Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> Bukkit.broadcast(line));
      }
      Settings.Title title = fx.title();
      if (title.enabled()) {
         player.showTitle(Title.title(Text.chat(title.title(), p.map()), Text.chat(title.subtitle(), p.map()),
               Title.Times.times(Duration.ofMillis(title.fadeIn() * 50L), Duration.ofMillis(title.stay() * 50L),
                     Duration.ofMillis(title.fadeOut() * 50L))));
      }
      Settings.SoundFx sound = fx.sound();
      if (sound.enabled() && sound.sound() != null) {
         player.playSound(Sound.sound(sound.sound(), Sound.Source.MASTER, sound.volume(), sound.pitch()), Sound.Emitter.self());
      }
      Settings.ParticleFx particles = fx.particles();
      if (particles.enabled() && particles.particle() != null && particles.count() > 0) {
         Location at = player.getLocation().add(0, 1, 0);
         player.getWorld().spawnParticle(particles.particle(), at, particles.count(), particles.spread(), particles.spread(),
               particles.spread(), particles.speed());
      }
   }
}
