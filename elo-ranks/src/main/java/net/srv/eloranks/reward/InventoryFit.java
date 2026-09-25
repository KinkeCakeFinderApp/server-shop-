package net.srv.eloranks.reward;

import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Works out, without touching the inventory, whether items fit into a player's storage slots. */
public final class InventoryFit {
   private InventoryFit() {
   }

   /** @return how many more empty slots would be needed (0 = everything fits) */
   public static int missingSlots(PlayerInventory inventory, List<ItemStack> items) {
      ItemStack[] slots = inventory.getStorageContents();
      ItemStack[] copy = new ItemStack[slots.length];
      for (int i = 0; i < slots.length; i++) {
         copy[i] = slots[i] == null || slots[i].getType().isAir() ? null : slots[i].clone();
      }
      int missing = 0;
      for (ItemStack item : items) {
         int left = item.getAmount();
         for (int i = 0; i < copy.length && left > 0; i++) {
            if (copy[i] != null && copy[i].isSimilar(item)) {
               int room = copy[i].getMaxStackSize() - copy[i].getAmount();
               int moved = Math.max(0, Math.min(room, left));
               copy[i].setAmount(copy[i].getAmount() + moved);
               left -= moved;
            }
         }
         for (int i = 0; i < copy.length && left > 0; i++) {
            if (copy[i] == null) {
               int moved = Math.min(item.getMaxStackSize(), left);
               copy[i] = item.clone();
               copy[i].setAmount(moved);
               left -= moved;
            }
         }
         if (left > 0) {
            missing += (left + item.getMaxStackSize() - 1) / item.getMaxStackSize();
         }
      }
      return missing;
   }
}
