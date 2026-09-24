package net.srv.legendaryadditions.admin.effect;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Finds a standing spot with solid ground and two blocks of headroom. Region-local reads only. */
public final class SafeLocations {
   private static final Set<Material> HAZARDS = EnumSet.of(
         Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CAMPFIRE,
         Material.SOUL_CAMPFIRE, Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
         Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.END_PORTAL, Material.NETHER_PORTAL,
         Material.END_GATEWAY, Material.COBWEB);

   private SafeLocations() {
   }

   /**
    * Searches outward from {@code start} (a block the player would stand in) for a safe position.
    *
    * @return a block-centred location, or null when nothing safe is nearby
    */
   public static Location find(Block start, int horizontalRadius, int verticalRadius) {
      for (int r = 0; r <= horizontalRadius; r++) {
         for (int dy : verticalOrder(verticalRadius)) {
            for (int dx = -r; dx <= r; dx++) {
               for (int dz = -r; dz <= r; dz++) {
                  if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                     continue;
                  }
                  Block feet = start.getRelative(dx, dy, dz);
                  if (isSafe(feet)) {
                     return feet.getLocation().add(0.5, 0.0, 0.5);
                  }
               }
            }
         }
      }
      return null;
   }

   /** The block a player would stand in when aiming at {@code hit} on {@code face}. */
   public static Block standingBlock(Block hit, BlockFace face) {
      if (face == null) {
         return hit.getRelative(BlockFace.UP);
      }
      return hit.getRelative(face);
   }

   public static boolean isSafe(Block feet) {
      World world = feet.getWorld();
      int y = feet.getY();
      if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
         return false;
      }
      int cx = feet.getX() >> 4;
      int cz = feet.getZ() >> 4;
      if (!world.isChunkLoaded(cx, cz) || !Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
         return false;
      }
      Location centre = feet.getLocation().add(0.5, 0.0, 0.5);
      if (!world.getWorldBorder().isInside(centre)) {
         return false;
      }
      Block head = feet.getRelative(BlockFace.UP);
      Block ground = feet.getRelative(BlockFace.DOWN);
      return isClear(feet) && isClear(head)
            && ground.getType().isSolid()
            && !HAZARDS.contains(ground.getType());
   }

   private static boolean isClear(Block block) {
      return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(block.getType());
   }

   private static int[] verticalOrder(int radius) {
      int[] order = new int[radius * 2 + 1];
      int i = 0;
      order[i++] = 0;
      for (int d = 1; d <= radius; d++) {
         order[i++] = d;
         order[i++] = -d;
      }
      return order;
   }
}
