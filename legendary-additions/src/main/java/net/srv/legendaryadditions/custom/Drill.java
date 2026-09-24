package net.srv.legendaryadditions.custom;

import java.util.Collections;
import net.md_5.bungee.api.ChatColor;
import net.srv.legendaryadditions.LegendaryAdditionsMod;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class Drill {
   public static final String ITEM_ID = "drill";
   public static final NamespacedKey ITEM_KEY = new NamespacedKey(LegendaryAdditionsMod.plugin, "item_id");
   public static final NamespacedKey MODE_KEY = new NamespacedKey(LegendaryAdditionsMod.plugin, "drill_mode");
   public static final String MODE_NORMAL = "NORMAL";
   public static final String MODE_DRILL = "DRILL";

   public static ItemStack create() {
      ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
      ItemMeta meta = pickaxe.getItemMeta();
      meta.setDisplayName(ChatColor.of("#FF4500").toString() + org.bukkit.ChatColor.BOLD + "Drill");
      meta.setUnbreakable(true);
      meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, "drill");
      meta.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, "NORMAL");
      pickaxe.setItemMeta(meta);
      applyModeLore(pickaxe, "NORMAL");
      return pickaxe;
   }

   public static boolean isDrill(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         String id = (String)meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
         return "drill".equals(id);
      } else {
         return false;
      }
   }

   public static String getMode(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         String mode = (String)meta.getPersistentDataContainer().get(MODE_KEY, PersistentDataType.STRING);
         return mode == null ? "NORMAL" : mode;
      } else {
         return "NORMAL";
      }
   }

   public static void toggleMode(ItemStack item) {
      String current = getMode(item);
      String next = "NORMAL".equals(current) ? "DRILL" : "NORMAL";
      ItemMeta meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, next);
      item.setItemMeta(meta);
      applyModeLore(item, next);
   }

   private static void applyModeLore(ItemStack item, String mode) {
      ItemMeta meta = item.getItemMeta();
      String label = "DRILL".equals(mode) ? "3x3" : "Normal";
      meta.setLore(Collections.singletonList(org.bukkit.ChatColor.BLUE + "Mode: " + label));
      item.setItemMeta(meta);
   }
}
