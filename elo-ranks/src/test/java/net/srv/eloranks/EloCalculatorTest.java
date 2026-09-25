package net.srv.eloranks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.srv.eloranks.elo.EloCalculator;
import net.srv.eloranks.elo.EloSettings;
import org.junit.jupiter.api.Test;

class EloCalculatorTest {
   private static final EloSettings DEFAULT = new EloSettings(0, 0, 0, true, 32, 1.0, 1, 0);

   @Test
   void equalPlayersTradeHalfTheKFactor() {
      EloCalculator.Change c = EloCalculator.kill(500, 500, DEFAULT, -1);
      assertEquals(16, c.gain());
      assertEquals(16, c.loss());
      assertEquals(516, c.killerAfter());
      assertEquals(484, c.victimAfter());
   }

   @Test
   void beatingAStrongerPlayerGivesMore() {
      int upset = EloCalculator.kill(100, 900, DEFAULT, -1).gain();
      int expected = EloCalculator.kill(900, 100, DEFAULT, -1).gain();
      assertTrue(upset > 16 && upset <= 32, "upset gain " + upset);
      assertEquals(1, expected, "minimum gain applies");
   }

   @Test
   void eloNeverGoesNegative() {
      EloSettings negativeMinimum = new EloSettings(0, -500, 0, true, 32, 1.0, 1, 0);
      EloCalculator.Change c = EloCalculator.kill(10, 5, negativeMinimum, -1);
      assertEquals(0, c.victimAfter());
      assertEquals(5, c.loss());
   }

   @Test
   void startingEloFloorAndMaximum() {
      EloSettings s = new EloSettings(100, 0, 1000, false, 32, 1.0, 1, 0);
      EloCalculator.Change c = EloCalculator.kill(990, 990, s, -1);
      assertEquals(1000, c.killerAfter());
      assertEquals(10, c.gain());
      assertEquals(100, EloCalculator.kill(100, 110, s, -1).victimAfter(), "cannot drop below starting ELO");
      assertEquals(100, s.floor());
      assertEquals(0, s.clamp(-50) - 100);
   }

   @Test
   void gainCapLimitsOnlyTheWinner() {
      EloCalculator.Change c = EloCalculator.kill(500, 500, DEFAULT, 5);
      assertEquals(5, c.gain());
      assertEquals(16, c.loss());
   }

   @Test
   void lossMultiplierAndMaxGain() {
      EloSettings s = new EloSettings(0, 0, 0, true, 32, 0.5, 1, 10);
      EloCalculator.Change c = EloCalculator.kill(500, 500, s, -1);
      assertEquals(10, c.gain());
      assertEquals(5, c.loss());
   }

   @Test
   void playerAboveMaximumNeverLosesByWinning() {
      EloSettings s = new EloSettings(0, 0, 1000, true, 32, 1.0, 1, 0);
      assertEquals(1500, EloCalculator.kill(1500, 500, s, -1).killerAfter());
   }
}
