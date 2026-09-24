package net.srv.legendaryadditions.admin.rod;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/**
 * Every special rod design. The single-use variant is given by /{command} as a full shulker,
 * the reusable variant by /{command}shot.
 */
public enum RodKind {
   ORBITAL("orbital", "Orbital Strike Rod", "stab", "admindimension.orbitalrod", RodOrigin.PLUGIN,
         "orbital_strike_cannon:stab (Stab Shot)", true, NamedTextColor.AQUA, Material.LIGHT_BLUE_SHULKER_BOX,
         "Calls an orbital strike where you look"),
   NUKE("nuke", "Nuke Shot", "nuke", "admindimension.nukerod", RodOrigin.PLUGIN,
         "orbital_strike_cannon:nuke (Nuke Shot)", true, NamedTextColor.RED, Material.RED_SHULKER_BOX,
         "Drops a nuke where you look"),
   TELEPORT("teleport", "Admin Teleport Rod", "teleport", "admindimension.teleportrod", RodOrigin.PLUGIN,
         null, false, NamedTextColor.LIGHT_PURPLE, Material.PURPLE_SHULKER_BOX,
         "Teleports you to where you look"),
   LAW_NUKE("lawnuke", "Law-Nuke Shot", "lawnuke", "admindimension.lawnukerod", RodOrigin.DATAPACK,
         "orbital_strike_cannon:lawnuke (Law-Nuke Shot)", true, NamedTextColor.GOLD, Material.RED_SHULKER_BOX,
         "Carpet-bombs the area you look at"),
   WITHER_NUKE("withernuke", "Wither Nuke Shot", "withernuke", "admindimension.withernukerod", RodOrigin.DATAPACK,
         "orbital_strike_cannon:wither_nuke (Wither Nuke Shot)", true, NamedTextColor.DARK_GRAY, Material.RED_SHULKER_BOX,
         "Rains wither skulls where you look"),
   WOLF("wolf", "Wolf Rod", "wolf", "admindimension.wolfrod", RodOrigin.DATAPACK,
         "orbital_strike_cannon:spawn_wolf (Wolf Rod)", true, NamedTextColor.WHITE, Material.RED_SHULKER_BOX,
         "Summons a tamed wolf pack where you look"),
   ARROW("arrow", "Arrow Shot", "arrow", "admindimension.arrowrod", RodOrigin.DATAPACK,
         "orbital_strike_cannon:arrow_shot (Arrow Shot)", true, NamedTextColor.YELLOW, Material.RED_SHULKER_BOX,
         "Rains arrows where you look");

   private final String id;
   private final String displayName;
   private final String command;
   private final String permission;
   private final RodOrigin origin;
   private final String datapackSource;
   private final boolean hasSingleUse;
   private final TextColor color;
   private final Material shulkerMaterial;
   private final String description;

   RodKind(String id, String displayName, String command, String permission, RodOrigin origin, String datapackSource,
           boolean hasSingleUse, TextColor color, Material shulkerMaterial, String description) {
      this.id = id;
      this.displayName = displayName;
      this.command = command;
      this.permission = permission;
      this.origin = origin;
      this.datapackSource = datapackSource;
      this.hasSingleUse = hasSingleUse;
      this.color = color;
      this.shulkerMaterial = shulkerMaterial;
      this.description = description;
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   /** Base command: /{command} gives the single-use shulker, /{command}shot the reusable rod. */
   public String command() {
      return this.command;
   }

   public String permission() {
      return this.permission;
   }

   public RodOrigin origin() {
      return this.origin;
   }

   /** Identifier of the original datapack function, or null when the rod is plugin-only. */
   public String datapackSource() {
      return this.datapackSource;
   }

   public boolean hasSingleUse() {
      return this.hasSingleUse;
   }

   public TextColor color() {
      return this.color;
   }

   public Material shulkerMaterial() {
      return this.shulkerMaterial;
   }

   public String description() {
      return this.description;
   }

   /** Internal rod type id stored on the item, e.g. {@code orbital_single} or {@code teleport_reusable}. */
   public String typeId(boolean reusable) {
      return this.id + (reusable ? "_reusable" : "_single");
   }
}
