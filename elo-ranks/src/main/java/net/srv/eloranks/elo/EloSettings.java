package net.srv.eloranks.elo;

/**
 * The ELO rules from config.yml.
 *
 * @param maximumElo 0 = no maximum
 * @param maximumGain 0 = no cap
 */
public record EloSettings(int startingElo, int minimumElo, int maximumElo, boolean allowBelowStartingElo,
                          int kFactor, double lossMultiplier, int minimumGain, int maximumGain) {

   /** The lowest ELO anyone can have. Never negative. */
   public int floor() {
      int floor = Math.max(0, this.minimumElo);
      return this.allowBelowStartingElo ? floor : Math.max(floor, this.startingElo);
   }

   /** Keeps {@code elo} between {@link #floor()} and the maximum. */
   public int clamp(long elo) {
      long clamped = Math.max(this.floor(), elo);
      if (this.maximumElo > 0) {
         clamped = Math.min(clamped, this.maximumElo);
      }
      return (int) Math.min(Integer.MAX_VALUE, clamped);
   }
}
