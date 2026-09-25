package net.srv.eloranks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.rank.TierLadder;
import org.junit.jupiter.api.Test;

class TierLadderTest {
   static TierLadder defaults() {
      return new TierLadder(List.of(
            new Tier("tier-1", 1, "Tier 1", "&6", 1000, "tnt_minecarts", 16),
            new Tier("tier-3", 3, "Tier 3", "&d", 640, "golden_carrots", 14),
            new Tier("tier-6", 6, "Tier 6", "&7", 100, "potions", 10),
            new Tier("tier-2", 2, "Tier 2", "&e", 820, "golden_apples", 15),
            new Tier("tier-5", 5, "Tier 5", "&a", 280, "pearls", 11),
            new Tier("tier-4", 4, "Tier 4", "&b", 460, "breeze_rods", 12)));
   }

   @Test
   void tierBoundaries() {
      TierLadder ladder = defaults();
      assertTrue(ladder.tierFor(99).isEmpty());
      assertEquals(6, ladder.tierFor(100).orElseThrow().number());
      assertEquals(6, ladder.tierFor(279).orElseThrow().number());
      assertEquals(5, ladder.tierFor(280).orElseThrow().number());
      assertEquals(4, ladder.tierFor(639).orElseThrow().number());
      assertEquals(3, ladder.tierFor(640).orElseThrow().number());
      assertEquals(2, ladder.tierFor(999).orElseThrow().number());
      assertEquals(1, ladder.tierFor(1000).orElseThrow().number());
      assertEquals(1, ladder.tierFor(Integer.MAX_VALUE).orElseThrow().number());
   }

   @Test
   void nextTierAndProgress() {
      TierLadder ladder = defaults();
      assertEquals(280, ladder.nextAfter(100).orElseThrow().elo());
      assertEquals(100, ladder.nextAfter(0).orElseThrow().elo());
      assertTrue(ladder.nextAfter(1000).isEmpty());
      assertEquals(0.5, ladder.progress(190), 1e-9);
      assertEquals(1.0, ladder.progress(5000), 1e-9);
      assertEquals(0.0, ladder.progress(0), 1e-9);
   }

   @Test
   void reachedAndParse() {
      TierLadder ladder = defaults();
      assertEquals(List.of("tier-6", "tier-5", "tier-4"), ladder.reachedBy(500).stream().map(Tier::id).toList());
      assertEquals("tier-1", ladder.parse("1").orElseThrow().id());
      assertEquals("tier-4", ladder.parse("T4").orElseThrow().id());
      assertEquals("tier-2", ladder.parse("tier2").orElseThrow().id());
      assertEquals("tier-6", ladder.parse("tier-6").orElseThrow().id());
      assertTrue(ladder.parse("7").isEmpty());
   }
}
