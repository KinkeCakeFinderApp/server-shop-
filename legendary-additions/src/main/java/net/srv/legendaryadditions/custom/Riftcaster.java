package net.srv.legendaryadditions.custom;

import java.util.Collections;
import net.srv.legendaryadditions.LegendaryAdditionsMod;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class Riftcaster {
   public static final String ITEM_ID = "riftcaster";
   public static final NamespacedKey ITEM_KEY = new NamespacedKey(LegendaryAdditionsMod.plugin, "item_id");

   public static ItemStack create() {
      ItemStack rod = new ItemStack(Material.FISHING_ROD);
      ItemMeta meta = rod.getItemMeta();
      meta.setDisplayName("" + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Riftcaster");
      meta.setLore(Collections.singletonList(ChatColor.LIGHT_PURPLE + "Each cast tears the world apart, stitching your path where none existed."));
      meta.setUnbreakable(true);
      meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, "riftcaster");
      rod.setItemMeta(meta);
      return rod;
   }

   public static boolean isRiftcaster(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         String id = (String)meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
         return "riftcaster".equals(id);
      } else {
         return false;
      }
   }
}
