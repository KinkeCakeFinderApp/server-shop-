package net.srv.legendaryadditions.admin.rod;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Crosshair ray trace from the player's eyes, done at the instant a rod is cast.
 *
 * <p>This walks the ray block by block (Amanatides-Woo traversal) and only reads chunks that are
 * loaded <em>and</em> owned by the current Folia region, so it can never touch another region's
 * data or force a synchronous chunk load. Entities are tested against the same chunks.</p>
 */
public final class RayTargeting {
   private RayTargeting() {
   }

   /**
    * @param position exact hit position
    * @param block    block that was hit, or null for an entity hit
    * @param face     face of the block that was hit, or null
    * @param entity   entity that was hit, or null for a block hit
    */
   public record Target(Location position, Block block, BlockFace face, Entity entity) {
      /** Where an area effect should be centred: an entity's feet, or the exact block hit point. */
      public Location center() {
         return this.entity != null ? this.entity.getLocation() : this.position.clone();
      }
   }

   public static Target trace(Player player, double maxDistance) {
      World world = player.getWorld();
      Location eye = player.getEyeLocation();
      Vector origin = eye.toVector();
      Vector dir = eye.getDirection();
      if (dir.lengthSquared() < 1.0E-9) {
         return null;
      }
      dir.normalize();

      int minY = world.getMinHeight();
      int maxY = world.getMaxHeight();
      int x = floor(origin.getX());
      int y = floor(origin.getY());
      int z = floor(origin.getZ());
      int stepX = (int) Math.signum(dir.getX());
      int stepY = (int) Math.signum(dir.getY());
      int stepZ = (int) Math.signum(dir.getZ());
      double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.getX());
      double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.getY());
      double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.getZ());
      double tMaxX = boundary(origin.getX(), x, stepX, dir.getX());
      double tMaxY = boundary(origin.getY(), y, stepY, dir.getY());
      double tMaxZ = boundary(origin.getZ(), z, stepZ, dir.getZ());

      double limit = maxDistance;
      double t = 0.0;
      RayTraceResult blockHit = null;
      Set<Long> chunks = new LinkedHashSet<>();

      while (t <= limit) {
         if ((y < minY && stepY <= 0) || (y >= maxY && stepY >= 0)) {
            limit = t;
            break;
         }
         int cx = x >> 4;
         int cz = z >> 4;
         if (!world.isChunkLoaded(cx, cz) || !Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
            limit = t;
            break;
         }
         chunks.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
         if (y >= minY && y < maxY) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir() && !block.isLiquid()) {
               RayTraceResult hit = block.rayTrace(eye, dir, maxDistance, FluidCollisionMode.NEVER);
               if (hit != null) {
                  blockHit = hit;
                  limit = hit.getHitPosition().distance(origin);
                  break;
               }
            }
         }
         if (tMaxX < tMaxY && tMaxX < tMaxZ) {
            t = tMaxX;
            tMaxX += tDeltaX;
            x += stepX;
         } else if (tMaxY < tMaxZ) {
            t = tMaxY;
            tMaxY += tDeltaY;
            y += stepY;
         } else {
            t = tMaxZ;
            tMaxZ += tDeltaZ;
            z += stepZ;
         }
      }

      Entity bestEntity = null;
      Vector bestPoint = null;
      double bestDistance = Math.min(limit, maxDistance);
      for (long key : chunks) {
         int cx = (int) (key >> 32);
         int cz = (int) key;
         for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
            if (!isTargetable(entity, player)) {
               continue;
            }
            BoundingBox box = entity.getBoundingBox().expand(0.1);
            RayTraceResult hit = box.rayTrace(origin, dir, bestDistance);
            if (hit != null) {
               double distance = hit.getHitPosition().distance(origin);
               if (distance <= bestDistance) {
                  bestDistance = distance;
                  bestEntity = entity;
                  bestPoint = hit.getHitPosition();
               }
            }
         }
      }

      if (bestEntity != null) {
         return new Target(bestPoint.toLocation(world), null, null, bestEntity);
      }
      if (blockHit != null) {
         return new Target(blockHit.getHitPosition().toLocation(world), blockHit.getHitBlock(), blockHit.getHitBlockFace(), null);
      }
      return null;
   }

   private static boolean isTargetable(Entity entity, Player caster) {
      if (entity.equals(caster) || !entity.isValid()) {
         return false;
      }
      if (entity instanceof Projectile || entity instanceof Item || entity instanceof FishHook) {
         return false;
      }
      return !(entity instanceof Player player) || player.getGameMode() != GameMode.SPECTATOR;
   }

   private static double boundary(double origin, int cell, int step, double dir) {
      if (step > 0) {
         return (cell + 1 - origin) / dir;
      }
      if (step < 0) {
         return (origin - cell) / -dir;
      }
      return Double.POSITIVE_INFINITY;
   }

   private static int floor(double value) {
      return (int) Math.floor(value);
   }
}
