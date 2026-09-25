package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
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
   private final Predicate<Player> access;
   private final Inventory inventory;
   private final Map<Integer, Action> actions = new HashMap<>();
   private BottomAction bottom;

   /** A click on an item in the viewer's own inventory while this screen is open. */
   @FunctionalInterface
   public interface BottomAction {
      void run(Player player, ItemStack clicked, ClickType click);
   }

   public Menu(UUID viewer, int rows, Component title, boolean adminOnly) {
      this(viewer, rows, title, adminOnly ? SuggestionAccess::isAdmin : null);
   }

   /** {@code access}: re-checked before every click; null means anyone may click. */
   public Menu(UUID viewer, int rows, Component title, Predicate<Player> access) {
      this.viewer = viewer;
      this.access = access;
      this.inventory = Bukkit.createInventory(this, rows * 9, title);
   }

   public void set(int slot, ItemStack item) {
      this.inventory.setItem(slot, item);
   }

   public void set(int slot, ItemStack item, Action action) {
      this.inventory.setItem(slot, item);
      this.actions.put(slot, action);
   }

   /** Lets the viewer pick items from their own inventory (the items never move). */
   public void onBottomClick(BottomAction action) {
      this.bottom = action;
   }

   BottomAction bottomAction() {
      return this.bottom;
   }

   Action action(int slot) {
      return this.actions.get(slot);
   }

   public UUID viewer() {
      return this.viewer;
   }

   /** Admin screens: every click re-checks the permission before anything runs. */
   public boolean allowed(Player player) {
      return this.access == null || (player.isOnline() && this.access.test(player));
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }
}
