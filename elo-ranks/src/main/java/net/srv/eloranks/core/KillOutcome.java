package net.srv.eloranks.core;

import java.util.List;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.elo.EloCalculator;

/**
 * What a player kill did.
 *
 * @param blocked null when the kill counted, otherwise the anti-farming rule that stopped it
 * @param blockedDetail remaining cooldown (millis) or the limit that was hit, for the message
 * @param change null when blocked
 * @param unlocked tiers the killer reached for the first time
 */
public record KillOutcome(FarmRule blocked, long blockedDetail, EloCalculator.Change change,
                          PlayerRecord killer, PlayerRecord victim, List<String> unlocked) {

   public enum FarmRule { SAME_VICTIM_COOLDOWN, SAME_VICTIM_LIMIT, GAIN_LIMIT, SAME_IP }

   public boolean counted() {
      return this.blocked == null;
   }
}
