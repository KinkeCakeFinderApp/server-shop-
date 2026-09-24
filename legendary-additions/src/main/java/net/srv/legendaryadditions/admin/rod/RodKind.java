package net.srv.legendaryadditions.admin.rod;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/**
 * Every special rod design. Each kind is its own authenticated rod type; the single-use variant
 * is handed out as a full shulker, the reusable variant as one enchanted rod.
 */
public enum RodKind {
   ORBITAL("orbital", "Orbital Strike Rod", "stab", "stabshot", "admindimension.orbitalrod", true,
         NamedTextColor.AQUA, Material.LIGHT_BLUE_SHULKER_BOX, "Calls an orbital strike where you look"),
   NUKE("nuke", "Nuke Shot", "nuke", "nukeshot", "admindimension.nukerod", true,
         NamedTextColor.RED, Material.RED_SHULKER_BOX, "Drops a nuke where you look"),
   TELEPORT("teleport", "Admin Teleport Rod", null, "teleportshot", "admindimension.teleportrod", false,
         NamedTextColor.LIGHT_PURPLE, Material.PURPLE_SHULKER_BOX, "Teleports you to where you look"),
   LAW_NUKE("lawnuke", "Law-Nuke Shot", "lawnuke", "lawnukeshot", "admindimension.lawnukerod", true,
         NamedTextColor.GOLD, Material.ORANGE_SHULKER_BOX, "Carpet-bombs the area you look at"),
   WITHER_NUKE("withernuke", "Wither Nuke Shot", "withernuke", "withernukeshot", "admindimension.withernukerod", true,
         NamedTextColor.DARK_GRAY, Material.BLACK_SHULKER_BOX, "Rains wither skulls where you look"),
   /** Reusable variant is "/wolfrod shot" (subcommand), not a separate command. */
   WOLF_ROD("wolfrod", "Wolf Rod", "wolfrod", null, "admindimension.wolfrod", true,
         NamedTextColor.WHITE, Material.WHITE_SHULKER_BOX, "Summons a tamed wolf pack where you look"),
   ARROW_ROD("arrowrod", "Arrow Rod", "arrowrod", "arrowrodshot", "admindimension.arrowrod", true,
         NamedTextColor.YELLOW, Material.YELLOW_SHULKER_BOX, "Rains arrows where you look");

   private final String id;
   private final String displayName;
   private final String singleCommand;
   private final String reusableCommand;
   private final String permission;
   private final boolean hasSingleUse;
   private final TextColor color;
   private final Material shulkerMaterial;
   private final String description;

   RodKind(String id, String displayName, String singleCommand, String reusableCommand, String permission,
           boolean hasSingleUse, TextColor color, Material shulkerMaterial, String description) {
      this.id = id;
      this.displayName = displayName;
      this.singleCommand = singleCommand;
      this.reusableCommand = reusableCommand;
      this.permission = permission;
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

   /** Command that gives the single-use shulker, or null. */
   public String singleCommand() {
      return this.singleCommand;
   }

   /** Standalone command that gives the reusable rod, or null (the Wolf Rod uses "/wolfrod shot"). */
   public String reusableCommand() {
      return this.reusableCommand;
   }

   public String permission() {
      return this.permission;
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

   /** Resolves a stored rod type id; returns null for unknown ids or a single-use id on a reusable-only kind. */
   public static Variant fromTypeId(String typeId) {
      if (typeId == null) {
         return null;
      }
      for (RodKind kind : values()) {
         if (kind.typeId(true).equals(typeId)) {
            return new Variant(kind, true);
         }
         if (kind.hasSingleUse && kind.typeId(false).equals(typeId)) {
            return new Variant(kind, false);
         }
      }
      return null;
   }

   public record Variant(RodKind kind, boolean reusable) {}
}
