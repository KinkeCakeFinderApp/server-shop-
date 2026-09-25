package net.srv.eloranks.gui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops rapid repeated clicks and overlapping actions: one click per {@link #MIN_INTERVAL_MS}
 * and at most one claim/delivery in flight per player.
 */
public final class ClickGuard {
   private static final long MIN_INTERVAL_MS = 250;

   private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
   private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

   public boolean tryClick(UUID player) {
      long now = System.currentTimeMillis();
      Long previous = this.lastClick.put(player, now);
      return previous == null || now - previous >= MIN_INTERVAL_MS;
   }

   /** @return false if another claim/delivery for this player is still running */
   public boolean begin(UUID player) {
      return this.busy.add(player);
   }

   public void end(UUID player) {
      this.busy.remove(player);
   }

   /** On quit. The busy flag is left to the running operation, which always ends it. */
   public void forgetClicks(UUID player) {
      this.lastClick.remove(player);
   }
}
