package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Small builder for GUI icons using Adventure components. */
public final class Items {
   private Items() {
   }

   public static Component text(String text, TextColor color) {
      return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
   }

   public static ItemStack icon(Material material, Component name, List<Component> lore, boolean glint) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.customName(name.decoration(TextDecoration.ITALIC, false));
      if (lore != null && !lore.isEmpty()) {
         meta.lore(lore);
      }
      if (glint) {
         meta.setEnchantmentGlintOverride(true);
      }
      item.setItemMeta(meta);
      return item;
   }

   public static ItemStack icon(Material material, String name, TextColor color, String... lore) {
      List<Component> lines = new ArrayList<>();
      for (String line : lore) {
         lines.add(text(line, NamedTextColor.GRAY));
      }
      return icon(material, text(name, color), lines, false);
   }

   public static ItemStack filler() {
      return icon(Material.GRAY_STAINED_GLASS_PANE, text(" ", NamedTextColor.GRAY), List.of(), false);
   }
}
