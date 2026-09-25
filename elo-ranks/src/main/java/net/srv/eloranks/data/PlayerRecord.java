package net.srv.eloranks.data;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A player's saved ranking data. Immutable; the database thread publishes a new one after every
 * change.
 *
 * @param unlocked ids of tiers the player has reached at some point (claimable until reset)
 * @param claims tier id -> time of the last claim (epoch millis)
 * @param pending number of claimed rewards still waiting to be delivered
 */
public record PlayerRecord(UUID uuid, String name, int elo, int kills, int deaths, long eloGained, long eloLost,
                           Set<String> unlocked, Map<String, Long> claims, int pending) {
   public PlayerRecord {
      unlocked = Set.copyOf(unlocked);
      claims = Map.copyOf(claims);
   }
}
