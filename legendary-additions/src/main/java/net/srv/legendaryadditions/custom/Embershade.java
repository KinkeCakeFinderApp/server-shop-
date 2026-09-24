package net.srv.legendaryadditions.custom;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Embershade {
   public static final String ITEM_ID = "embershade";

   public static ItemStack create() {
      ItemStack bow = new ItemStack(Material.BOW);
      ItemMeta meta = bow.getItemMeta();
      meta.setDisplayName("" + ChatColor.DARK_RED + ChatColor.BOLD + "Embershade");
      meta.addEnchant(Enchantment.POWER, 10, true);
      meta.addEnchant(Enchantment.PUNCH, 5, true);
      meta.addEnchant(Enchantment.FLAME, 2, true);
      meta.addEnchant(Enchantment.INFINITY, 1, true);
      meta.addEnchant(Enchantment.PIERCING, 2, true);
      meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
      meta.setUnbreakable(true);
      bow.setItemMeta(meta);
      return bow;
   }
}
