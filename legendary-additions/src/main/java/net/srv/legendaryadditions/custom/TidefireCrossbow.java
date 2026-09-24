package net.srv.legendaryadditions.custom;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TidefireCrossbow {
   public static final String ITEM_ID = "tidefire_crossbow";

   public static ItemStack create() {
      ItemStack crossbow = new ItemStack(Material.CROSSBOW);
      ItemMeta meta = crossbow.getItemMeta();
      meta.setDisplayName("" + ChatColor.GOLD + ChatColor.BOLD + "Tidefire Crossbow");
      meta.addEnchant(Enchantment.FLAME, 1, true);
      meta.addEnchant(Enchantment.INFINITY, 1, true);
      meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
      meta.addEnchant(Enchantment.QUICK_CHARGE, 6, true);
      meta.addEnchant(Enchantment.PIERCING, 1, true);
      meta.setUnbreakable(true);
      crossbow.setItemMeta(meta);
      return crossbow;
   }
}
