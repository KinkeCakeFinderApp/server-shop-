package net.srv.eloranks.gui;

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
 * A read-only chest GUI. What a click does is decided only by the server-side {@link #actions}
 * map built when the screen was rendered - never by the clicked item - so a modified client
 * cannot trigger anything that was not placed there by the server.
 */
public final class Menu implements InventoryHolder {
   @FunctionalInterface
   public interface Action {
      void run(Player player, ClickType click);
   }

   private final UUID viewer;
   private final Inventory inventory;
   private final Map<Integer, Action> actions = new HashMap<>();

   public Menu(UUID viewer, int rows, Component title) {
      this.viewer = viewer;
      this.inventory = Bukkit.createInventory(this, rows * 9, title);
   }

   public void set(int slot, ItemStack item) {
      if (slot >= 0 && slot < this.inventory.getSize()) {
         this.inventory.setItem(slot, item);
      }
   }

   public void set(int slot, ItemStack item, Action action) {
      if (slot >= 0 && slot < this.inventory.getSize()) {
         this.inventory.setItem(slot, item);
         this.actions.put(slot, action);
      }
   }

   /** Fills every empty slot. */
   public void fill(ItemStack filler) {
      for (int i = 0; i < this.inventory.getSize(); i++) {
         if (this.inventory.getItem(i) == null) {
            this.inventory.setItem(i, filler);
         }
      }
   }

   Action action(int slot) {
      return this.actions.get(slot);
   }

   public UUID viewer() {
      return this.viewer;
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }
}
