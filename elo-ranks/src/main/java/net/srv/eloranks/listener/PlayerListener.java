package net.srv.eloranks.listener;

import net.srv.eloranks.EloRanksPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads ranking data on join (off the player's thread) and drops the cache on quit. */
public final class PlayerListener implements Listener {
   private final EloRanksPlugin plugin;

   public PlayerListener(EloRanksPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.plugin.ranks().join(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      this.plugin.data().forget(event.getPlayer().getUniqueId());
   }
}
