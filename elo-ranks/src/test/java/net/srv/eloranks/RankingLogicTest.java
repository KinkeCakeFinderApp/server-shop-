package net.srv.eloranks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.srv.eloranks.core.AntiFarmSettings;
import net.srv.eloranks.core.KillOutcome;
import net.srv.eloranks.core.RankingLogic;
import net.srv.eloranks.data.EloDatabase;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.elo.EloSettings;
import net.srv.eloranks.rank.TierLadder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankingLogicTest {
   private static final EloSettings ELO = new EloSettings(0, 0, 0, true, 32, 1.0, 1, 0);
   private static final long H = TimeUnit.HOURS.toMillis(1);
   private static final AntiFarmSettings FARM = new AntiFarmSettings(10 * 60_000, 3, 24 * H, 400, 24 * H, true);
   private static final AntiFarmSettings NO_FARM_RULES = new AntiFarmSettings(0, 0, 0, 0, 0, false);

   private final UUID a = UUID.randomUUID();
   private final UUID b = UUID.randomUUID();
   private final TierLadder ladder = TierLadderTest.defaults();
   private Path file;
   private EloDatabase db;
   private RankingLogic logic;

   @BeforeEach
   void open() throws Exception {
      this.file = Files.createTempFile("eloranks", ".db");
      this.db = new EloDatabase("jdbc:sqlite:" + this.file);
      this.logic = new RankingLogic(this.db);
   }

   @AfterEach
   void close() throws Exception {
      this.db.close();
      Files.deleteIfExists(this.file);
   }

   @Test
   void killChangesEloAndStats() throws Exception {
      KillOutcome out = this.logic.kill(this.a, "Alice", this.b, "Bob", false, 1000, ELO, FARM, this.ladder);
      assertTrue(out.counted());
      assertEquals(16, out.killer().elo());
      assertEquals(0, out.victim().elo(), "never negative");
      assertEquals(1, out.killer().kills());
      assertEquals(1, out.victim().deaths());
      assertEquals(16, out.killer().eloGained());
      assertEquals(0, out.victim().eloLost());
   }

   @Test
   void sameVictimCooldownAndLimit() throws Exception {
      long t = 0;
      assertTrue(this.logic.kill(this.a, "Alice", this.b, "Bob", false, t, ELO, FARM, this.ladder).counted());
      KillOutcome again = this.logic.kill(this.a, "Alice", this.b, "Bob", false, t + 60_000, ELO, FARM, this.ladder);
      assertEquals(KillOutcome.FarmRule.SAME_VICTIM_COOLDOWN, again.blocked());
      assertEquals(9 * 60_000, again.blockedDetail());
      assertEquals(16, this.db.load(this.a).orElseThrow().elo(), "blocked kill changes nothing");
      assertTrue(this.logic.kill(this.a, "Alice", this.b, "Bob", false, t + 11 * 60_000, ELO, FARM, this.ladder).counted());
      assertTrue(this.logic.kill(this.a, "Alice", this.b, "Bob", false, t + 22 * 60_000, ELO, FARM, this.ladder).counted());
      assertEquals(KillOutcome.FarmRule.SAME_VICTIM_LIMIT,
            this.logic.kill(this.a, "Alice", this.b, "Bob", false, t + 33 * 60_000, ELO, FARM, this.ladder).blocked());
      assertTrue(this.logic.kill(this.a, "Alice", this.b, "Bob", false, t + 25 * H, ELO, FARM, this.ladder).counted(),
            "window passed");
   }

   @Test
   void sameIpBlocked() throws Exception {
      assertEquals(KillOutcome.FarmRule.SAME_IP,
            this.logic.kill(this.a, "Alice", this.b, "Bob", true, 0, ELO, FARM, this.ladder).blocked());
      assertNull(this.logic.kill(this.a, "Alice", this.b, "Bob", true, 0, ELO, NO_FARM_RULES, this.ladder).blocked());
   }

   @Test
   void gainLimitCapsThenBlocks() throws Exception {
      AntiFarmSettings farm = new AntiFarmSettings(0, 0, 0, 20, 24 * H, false);
      assertEquals(16, this.logic.kill(this.a, "Alice", this.b, "Bob", false, 0, ELO, farm, this.ladder).change().gain());
      assertEquals(4, this.logic.kill(this.a, "Alice", this.b, "Bob", false, 1, ELO, farm, this.ladder).change().gain());
      assertEquals(KillOutcome.FarmRule.GAIN_LIMIT, this.logic.kill(this.a, "Alice", this.b, "Bob", false, 2, ELO, farm, this.ladder).blocked());
   }

   @Test
   void unlocksStayAfterEloDrops() throws Exception {
      this.logic.join(this.a, "Alice", ELO, this.ladder, 0);
      RankingLogic.EloUpdate up = this.logic.setElo(this.a, 500, ELO, this.ladder, 0);
      assertEquals(List.of("tier-6", "tier-5", "tier-4"), up.unlocked());
      PlayerRecord down = this.logic.setElo(this.a, 50, ELO, this.ladder, 0).after();
      assertEquals(Set.of("tier-6", "tier-5", "tier-4"), down.unlocked());
      assertEquals(50, down.elo());
      assertEquals(Set.of(), this.logic.resetProgress(this.a, this.ladder, 0).unlocked());
      assertEquals(0, this.logic.setElo(this.a, -30, ELO, this.ladder, 0).after().elo(), "admin cannot set negative ELO");
   }

   @Test
   void claimCooldownIsAtomicAndPersistent() throws Exception {
      this.logic.join(this.a, "Alice", ELO, this.ladder, 0);
      long cooldown = 48 * H;
      assertEquals(EloDatabase.ClaimStatus.LOCKED, this.db.tryClaim(this.a, "tier-6", "potions", 0, cooldown).status());
      this.logic.setElo(this.a, 300, ELO, this.ladder, 0);
      EloDatabase.ClaimResult first = this.db.tryClaim(this.a, "tier-6", "potions", 1000, cooldown);
      assertEquals(EloDatabase.ClaimStatus.OK, first.status());
      assertEquals(EloDatabase.ClaimStatus.COOLDOWN, this.db.tryClaim(this.a, "tier-6", "potions", 1001, cooldown).status());
      assertEquals(EloDatabase.ClaimStatus.OK, this.db.tryClaim(this.a, "tier-5", "pearls", 1001, cooldown).status(),
            "every tier has its own cooldown");

      this.db.close();
      this.db = new EloDatabase("jdbc:sqlite:" + this.file);
      assertEquals(EloDatabase.ClaimStatus.COOLDOWN, this.db.tryClaim(this.a, "tier-6", "potions", 1000 + cooldown - 1, cooldown).status(),
            "cooldown survives a restart");
      assertEquals(2, this.db.pending(this.a).size(), "claims are queued for delivery");
      assertTrue(this.db.deletePending(first.pendingId()));
      assertFalse(this.db.deletePending(first.pendingId()), "a reward is delivered once");
      assertEquals(EloDatabase.ClaimStatus.OK, this.db.tryClaim(this.a, "tier-6", "potions", 1000 + cooldown, cooldown).status());
      this.db.resetClaims(this.a);
      assertEquals(EloDatabase.ClaimStatus.OK, this.db.tryClaim(this.a, "tier-6", "potions", 1000 + cooldown + 1, cooldown).status());
      assertEquals(300, this.db.load(this.a).orElseThrow().elo(), "resetclaims keeps ELO");
   }

   @Test
   void nameChangesAndLeaderboard() throws Exception {
      this.logic.join(this.a, "Alice", ELO, this.ladder, 0);
      this.logic.join(this.b, "Bob", ELO, this.ladder, 0);
      this.logic.setElo(this.b, 900, ELO, this.ladder, 0);
      this.logic.join(this.a, "Alicia", ELO, this.ladder, 5);
      assertEquals(this.a, this.db.findByName("ALICIA").orElseThrow());
      List<EloDatabase.LeaderboardRow> top = this.db.top(10);
      assertEquals("Bob", top.get(0).name());
      assertEquals("Alicia", top.get(1).name());
      assertTrue(top.get(0).unlocked().contains("tier-2"));
      assertTrue(this.db.wipe(this.b));
      assertEquals(1, this.db.top(10).size());
   }
}
