package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.logging.Level;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/**
 * Routes clicks in plugin GUIs. Every click in a plugin GUI is cancelled, so items can never be
 * taken out or put in; only a plain left/right (or shift) click on a top-inventory slot that the
 * server registered an action for does anything, and admin screens re-check the permission first.
 */
public final class MenuListener implements Listener {
   private final Plugin plugin;
   private final ClickGuard guard;

   public MenuListener(Plugin plugin, ClickGuard guard) {
      this.plugin = plugin;
      this.guard = guard;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
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
      if (!menu.allowed(player)) {
         player.closeInventory();
         Messages.error(player, Messages.NO_PERMISSION);
         return;
      }
      Menu.Action action = menu.action(slot);
      if (action == null || !this.guard.tryClick(player.getUniqueId())) {
         return;
      }
      try {
         action.run(player, click);
      } catch (RuntimeException ex) {
         this.plugin.getLogger().log(Level.SEVERE, "GUI action failed for " + player.getName(), ex);
         Messages.error(player, Messages.ACTION_FAILED);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.guard.forget(event.getPlayer().getUniqueId());
   }
}
