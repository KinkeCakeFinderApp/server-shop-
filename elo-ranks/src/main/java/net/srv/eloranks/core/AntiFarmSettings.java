package net.srv.eloranks.core;

/**
 * Anti ELO farming rules. Every 0 turns that rule off.
 *
 * @param sameVictimCooldownMillis minimum time between two counted kills of the same victim
 * @param sameVictimLimit most counted kills of the same victim within {@code sameVictimWindowMillis}
 * @param gainLimit most ELO a player can gain within {@code gainWindowMillis}
 */
public record AntiFarmSettings(long sameVictimCooldownMillis, int sameVictimLimit, long sameVictimWindowMillis,
                               int gainLimit, long gainWindowMillis, boolean blockSameIp) {

   /** How long kill history must be kept for these rules. */
   public long historyMillis() {
      return Math.max(this.sameVictimCooldownMillis, Math.max(this.sameVictimWindowMillis, this.gainWindowMillis));
   }
}
