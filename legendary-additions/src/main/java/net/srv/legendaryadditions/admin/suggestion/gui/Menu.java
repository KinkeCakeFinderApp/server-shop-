package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest GUI screen. What a click does is decided only by the server-side {@link #actions} map
 * built when the screen was rendered - never by the clicked item's name, lore or data - so a
 * modified client item cannot trigger anything that was not placed there by the server.
 */
public final class Menu implements InventoryHolder {
   @FunctionalInterface
   public interface Action {
      void run(Player player, ClickType click);
   }

   private final UUID viewer;
   private final boolean adminOnly;
   private final Inventory inventory;
   private final Map<Integer, Action> actions = new HashMap<>();

   public Menu(UUID viewer, int rows, Component title, boolean adminOnly) {
      this.viewer = viewer;
      this.adminOnly = adminOnly;
      this.inventory = Bukkit.createInventory(this, rows * 9, title);
   }

   public void set(int slot, ItemStack item) {
      this.inventory.setItem(slot, item);
   }

   public void set(int slot, ItemStack item, Action action) {
      this.inventory.setItem(slot, item);
      this.actions.put(slot, action);
   }

   Action action(int slot) {
      return this.actions.get(slot);
   }

   public UUID viewer() {
      return this.viewer;
   }

   /** Admin screens: every click re-checks the admin permission before anything runs. */
   public boolean adminOnly() {
      return this.adminOnly;
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }
}
