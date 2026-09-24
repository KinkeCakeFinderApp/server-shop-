package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops rapid repeated clicks and overlapping actions: one click per {@link #MIN_INTERVAL_MS}
 * and at most one database operation in flight per player.
 */
public final class ClickGuard {
   private static final long MIN_INTERVAL_MS = 200;

   private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
   private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

   public boolean tryClick(UUID player) {
      if (this.busy.contains(player)) {
         return false;
      }
      long now = System.currentTimeMillis();
      Long previous = this.lastClick.put(player, now);
      return previous == null || now - previous >= MIN_INTERVAL_MS;
   }

   /** @return false if another operation for this player is still running. */
   public boolean begin(UUID player) {
      return this.busy.add(player);
   }

   public void end(UUID player) {
      this.busy.remove(player);
   }

   public void forget(UUID player) {
      this.busy.remove(player);
      this.lastClick.remove(player);
   }
}
