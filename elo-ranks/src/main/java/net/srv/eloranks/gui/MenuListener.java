package net.srv.eloranks.gui;

import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/**
 * Makes plugin GUIs read-only. Every click while one is open is cancelled - in the GUI and in the
 * player's own inventory, so shift-clicks, number keys, double-click collecting, off-hand swaps,
 * drops and drags can never move an item in or out. Only a plain left/right (or shift) click on a
 * top slot the server registered an action for does anything.
 */
public final class MenuListener implements Listener {
   private final Plugin plugin;
   private final ClickGuard guard;

   public MenuListener(Plugin plugin, ClickGuard guard) {
      this.plugin = plugin;
      this.guard = guard;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (!(top.getHolder(false) instanceof Menu menu)) {
         return;
      }
      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      if (!player.getUniqueId().equals(menu.viewer())) {
         player.closeInventory();
         return;
      }
      if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
         return;
      }
      int slot = event.getRawSlot();
      if (slot < 0 || slot >= top.getSize()) {
         return;
      }
      ClickType click = event.getClick();
      if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
         return;
      }
      Menu.Action action = menu.action(slot);
      if (action == null || !this.guard.tryClick(player.getUniqueId())) {
         return;
      }
      // Next tick on the player's thread: opening another inventory inside a click event is unsafe.
      player.getScheduler().run(this.plugin, task -> {
         if (player.getOpenInventory().getTopInventory().getHolder(false) != menu) {
            return;
         }
         try {
            action.run(player, click);
         } catch (RuntimeException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "GUI action failed for " + player.getName(), ex);
         }
      }, null);
   }

   /** Also keeps the cancelled result if another plugin un-cancels at a later priority. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onClickMonitor(InventoryClickEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof Menu && !event.isCancelled()) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onSwap(PlayerSwapHandItemsEvent event) {
      if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.guard.forgetClicks(event.getPlayer().getUniqueId());
   }
}
