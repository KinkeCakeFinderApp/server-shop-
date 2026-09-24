package net.srv.legendaryadditions.custom;

import java.util.ArrayList;
import java.util.List;
import net.srv.legendaryadditions.LegendaryAdditionsMod;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;

public class Paxel {
   public static final String ITEM_ID = "paxel";
   public static final NamespacedKey ITEM_KEY = new NamespacedKey(LegendaryAdditionsMod.plugin, "item_id");
   public static final NamespacedKey MODE_KEY = new NamespacedKey(LegendaryAdditionsMod.plugin, "paxel_mode");
   public static final String MODE_NORMAL = "NORMAL";
   public static final String MODE_3X3 = "AREA_3X3";
   public static final String MODE_VEIN_MINER = "VEIN_MINER";
   public static final String MODE_CHAINSAW = "CHAINSAW";
   private static final String[] MODE_ORDER = new String[]{"NORMAL", "AREA_3X3", "VEIN_MINER", "CHAINSAW"};

   public static ItemStack create() {
      ItemStack paxel = new ItemStack(Material.NETHERITE_PICKAXE);
      ItemMeta meta = paxel.getItemMeta();
      meta.setDisplayName(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Paxel");
      meta.setUnbreakable(true);
      meta.addEnchant(Enchantment.FORTUNE, 5, true);
      meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_UNBREAKABLE});
      ToolComponent tool = meta.getTool();
      tool.addRule(Tag.MINEABLE_PICKAXE, 10.0F, true);
      tool.addRule(Tag.MINEABLE_AXE, 10.0F, true);
      tool.addRule(Tag.MINEABLE_SHOVEL, 10.0F, true);
      meta.setTool(tool);
      meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, "paxel");
      meta.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, "NORMAL");
      paxel.setItemMeta(meta);
      applyModeLore(paxel, "NORMAL");
      return paxel;
   }

   public static boolean isPaxel(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         String id = (String)meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
         return "paxel".equals(id);
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
      String next = getNextMode(current);
      ItemMeta meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, next);
      item.setItemMeta(meta);
      applyModeLore(item, next);
   }

   private static String getNextMode(String current) {
      for (int i = 0; i < MODE_ORDER.length; i++) {
         if (MODE_ORDER[i].equals(current)) {
            return MODE_ORDER[(i + 1) % MODE_ORDER.length];
         }
      }

      return "NORMAL";
   }

   private static String getModeLabel(String mode) {
      switch (mode) {
         case "AREA_3X3":
            return "3x3";
         case "VEIN_MINER":
            return "Vein Miner";
         case "CHAINSAW":
            return "Chainsaw";
         case "NORMAL":
         default:
            return "Normal";
      }
   }

   private static void applyModeLore(ItemStack item, String mode) {
      ItemMeta meta = item.getItemMeta();
      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.GRAY + "A legendary all-in-one tool.");
      lore.add(ChatColor.BLUE + "Fells entire trees in one chop");
      lore.add(ChatColor.BLUE + "Vein mines connected ores");
      lore.add(ChatColor.BLUE + "Mines in a 3x3 area");
      lore.add(ChatColor.BLUE + "Breaks dirt & gravel instantly");
      lore.add(ChatColor.AQUA + "Grants Haste V while held");
      lore.add("");
      lore.add(ChatColor.BLUE + "Mode: " + getModeLabel(mode));
      lore.add(ChatColor.DARK_GRAY + "Shift right click to change mode");
      meta.setLore(lore);
      item.setItemMeta(meta);
   }
}
