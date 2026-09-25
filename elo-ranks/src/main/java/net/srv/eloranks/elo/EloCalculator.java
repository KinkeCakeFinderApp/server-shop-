package net.srv.eloranks.elo;

/** Standard ELO: the winner gains k * (1 - expected), the loser loses that times loss-multiplier. */
public final class EloCalculator {
   private EloCalculator() {
   }

   /** The result of one counted kill. Deltas are what really changed after clamping. */
   public record Change(int killerBefore, int killerAfter, int victimBefore, int victimAfter) {
      public int gain() {
         return this.killerAfter - this.killerBefore;
      }

      public int loss() {
         return this.victimBefore - this.victimAfter;
      }
   }

   /** Chance (0..1) that a player rated {@code rating} beats one rated {@code opponent}. */
   public static double expected(int rating, int opponent) {
      return 1.0 / (1.0 + Math.pow(10.0, (opponent - (double) rating) / 400.0));
   }

   /** The gain before any limits other than minimum/maximum-gain. */
   public static int rawGain(int killerElo, int victimElo, EloSettings settings) {
      long gain = Math.round(settings.kFactor() * (1.0 - expected(killerElo, victimElo)));
      gain = Math.max(settings.minimumGain(), gain);
      if (settings.maximumGain() > 0) {
         gain = Math.min(settings.maximumGain(), gain);
      }
      return (int) Math.max(0, gain);
   }

   /**
    * @param gainCap the most the killer may gain (anti-farming gain limit), or -1 for no cap
    */
   public static Change kill(int killerElo, int victimElo, EloSettings settings, int gainCap) {
      int gain = rawGain(killerElo, victimElo, settings);
      int loss = (int) Math.max(0, Math.round(gain * settings.lossMultiplier()));
      if (gainCap >= 0) {
         gain = Math.min(gain, gainCap);
      }
      // A player already above the maximum (e.g. set by an admin before the maximum was lowered)
      // never loses ELO by winning; one below the floor never gains by losing.
      int killerAfter = Math.max(killerElo, settings.clamp((long) killerElo + gain));
      int victimAfter = Math.min(victimElo, settings.clamp((long) victimElo - loss));
      return new Change(killerElo, killerAfter, victimElo, victimAfter);
   }
}
