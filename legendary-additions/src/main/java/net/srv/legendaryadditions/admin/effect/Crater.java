package net.srv.legendaryadditions.admin.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Silently removes a sphere of blocks: no explosion packet, so no explosion sound or particles.
 * Each chunk's share is done on the region that owns that chunk. Unbreakable blocks (bedrock,
 * barriers, portal frames...) are left alone, and nothing drops.
 */
public final class Crater {
   private Crater() {
   }

   public static void carve(Plugin plugin, Location center, double radius) {
      World world = center.getWorld();
      int r = (int) Math.ceil(radius);
      int cx = center.getBlockX();
      int cy = center.getBlockY();
      int cz = center.getBlockZ();
      double r2 = radius * radius;
      int minY = world.getMinHeight();
      int maxY = world.getMaxHeight();

      Map<Long, List<int[]>> byChunk = new HashMap<>();
      for (int x = cx - r; x <= cx + r; x++) {
         for (int y = Math.max(minY, cy - r); y <= Math.min(maxY - 1, cy + r); y++) {
            for (int z = cz - r; z <= cz + r; z++) {
               double dx = x + 0.5 - center.getX();
               double dy = y + 0.5 - center.getY();
               double dz = z + 0.5 - center.getZ();
               if (dx * dx + dy * dy + dz * dz <= r2) {
                  long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
                  byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[] {x, y, z});
               }
            }
         }
      }
      for (Map.Entry<Long, List<int[]>> entry : byChunk.entrySet()) {
         int chunkX = (int) (entry.getKey() >> 32);
         int chunkZ = (int) (long) entry.getKey();
         List<int[]> blocks = entry.getValue();
         Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
            if (!EffectGuards.worldStillLoaded(world) || !world.isChunkLoaded(chunkX, chunkZ)) {
               return;
            }
            for (int[] p : blocks) {
               Block block = world.getBlockAt(p[0], p[1], p[2]);
               Material type = block.getType();
               if (type.isAir() || type.getHardness() < 0) {
                  continue;
               }
               block.setType(Material.AIR, false);
            }
         });
      }
   }
}
