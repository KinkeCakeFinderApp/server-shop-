package net.srv.legendaryadditions.forge;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

/**
 * Everything a custom legendary can do. Stored in legendaries.db by {@link #name()}, so constants
 * may be added but never renamed. Passive potion abilities work while the item is held in either
 * hand or worn as armour.
 */
public enum Ability {
   // Tools
   VEIN_MINE("Vein Miner", Material.IRON_ORE, 5, "Mining an ore mines every connected", "ore of the same type (16 per level)."),
   TREE_CAPITATOR("Tree Capitator", Material.OAK_LOG, 5, "Chopping a log fells the whole tree", "(64 logs per level)."),
   AREA_MINE("Area Miner", Material.TNT, 3, "Mines a 3x3 area (level 2: 5x5,", "level 3: 7x7) facing where you mine."),
   AUTO_SMELT("Auto Smelt", Material.FURNACE, 1, "Block drops come out smelted", "(raw ores to ingots, sand to glass...)."),
   TELEKINESIS("Telekinesis", Material.ENDER_PEARL, 1, "Block and mob drops go straight", "into your inventory."),
   REPLANT("Replant", Material.WHEAT_SEEDS, 1, "Harvesting a fully grown crop", "replants it automatically."),
   WISDOM("Wisdom", Material.EXPERIENCE_BOTTLE, 5, "+50% experience per level from", "blocks and mobs."),
   MAGNET("Magnet", Material.LODESTONE, 5, "Pulls dropped items to you", "(4 blocks per level)."),
   // Weapons
   LIFESTEAL("Lifesteal", Material.REDSTONE, 5, "Heals you for 5% of the damage", "you deal per level."),
   CRITICAL("Critical Strike", Material.DIAMOND_SWORD, 5, "10% chance per level to deal", "double damage."),
   THUNDERLORD("Thunderlord", Material.LIGHTNING_ROD, 5, "10% chance per level to strike", "your target with lightning."),
   VENOM("Venom", Material.SPIDER_EYE, 5, "Poisons what you hit", "(stronger and longer per level)."),
   WITHERING("Withering", Material.WITHER_ROSE, 5, "Withers what you hit."),
   FROST("Frost", Material.BLUE_ICE, 5, "Slows and freezes what you hit."),
   IGNITE("Ignite", Material.BLAZE_POWDER, 5, "Sets what you hit on fire", "(2 seconds per level)."),
   BEHEADING("Beheading", Material.WITHER_SKELETON_SKULL, 5, "10% chance per level for a kill", "to drop the victim's head."),
   EXPLOSIVE_ARROWS("Explosive Arrows", Material.FIRE_CHARGE, 5, "Arrows and bolts explode where", "they land (no block damage)."),
   // Movement and survival
   DASH("Dash", Material.PHANTOM_MEMBRANE, 5, "Right click to dash forward", "(further per level)."),
   FEATHERWEIGHT("Featherweight", Material.FEATHER, 1, "No fall damage while held or worn."),
   SOULBOUND("Soulbound", Material.TOTEM_OF_UNDYING, 1, "Stays in your inventory when you die."),
   // Passive potion effects
   SPEED("Speed", Material.SUGAR, 5, PotionEffectType.SPEED),
   HASTE("Haste", Material.GOLDEN_PICKAXE, 5, PotionEffectType.HASTE),
   STRENGTH("Strength", Material.BLAZE_ROD, 5, PotionEffectType.STRENGTH),
   RESISTANCE("Resistance", Material.IRON_CHESTPLATE, 4, PotionEffectType.RESISTANCE),
   JUMP_BOOST("Jump Boost", Material.RABBIT_FOOT, 5, PotionEffectType.JUMP_BOOST),
   REGENERATION("Regeneration", Material.GHAST_TEAR, 5, PotionEffectType.REGENERATION),
   NIGHT_VISION("Night Vision", Material.ENDER_EYE, 1, PotionEffectType.NIGHT_VISION),
   WATER_BREATHING("Water Breathing", Material.TURTLE_HELMET, 1, PotionEffectType.WATER_BREATHING),
   FIRE_RESISTANCE("Fire Resistance", Material.MAGMA_CREAM, 1, PotionEffectType.FIRE_RESISTANCE),
   SATURATION("Saturation", Material.COOKED_BEEF, 1, PotionEffectType.SATURATION),
   DOLPHINS_GRACE("Dolphin's Grace", Material.HEART_OF_THE_SEA, 3, PotionEffectType.DOLPHINS_GRACE);

   private final String label;
   private final Material icon;
   private final int maxLevel;
   private final List<String> description;
   private final PotionEffectType potion;

   Ability(String label, Material icon, int maxLevel, String... description) {
      this.label = label;
      this.icon = icon;
      this.maxLevel = maxLevel;
      this.description = List.of(description);
      this.potion = null;
   }

   Ability(String label, Material icon, int maxLevel, PotionEffectType potion) {
      this.label = label;
      this.icon = icon;
      this.maxLevel = maxLevel;
      this.description = List.of("Gives " + label + " while held or worn.");
      this.potion = potion;
   }

   public String label() {
      return this.label;
   }

   public Material icon() {
      return this.icon;
   }

   public int maxLevel() {
      return this.maxLevel;
   }

   public List<String> description() {
      return this.description;
   }

   /** Non-null for passive potion abilities. */
   public PotionEffectType potion() {
      return this.potion;
   }

   public static Ability byId(String id) {
      try {
         return id == null ? null : valueOf(id);
      } catch (IllegalArgumentException ex) {
         return null;
      }
   }

   public static String roman(int level) {
      return switch (level) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         default -> Integer.toString(level);
      };
   }
}
