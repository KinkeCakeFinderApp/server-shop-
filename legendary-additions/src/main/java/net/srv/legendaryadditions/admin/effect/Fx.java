package net.srv.legendaryadditions.admin.effect;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Shared, bounded particle/damage helpers. Everything here must run on the region owning {@code center}. */
public final class Fx {
   private Fx() {
   }

   public static <T> void particle(Location at, Particle particle, int count, double spread, double speed, T data) {
      at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, speed, data, true);
   }

   public static void particle(Location at, Particle particle, int count, double spread, double speed) {
      particle(at, particle, count, spread, speed, null);
   }

   /** Horizontal ring. Point count is capped so large radii don't flood clients. */
   public static <T> void ring(Location center, double radius, double yOffset, Particle particle, T data, int maxPoints) {
      World world = center.getWorld();
      int points = Math.max(12, Math.min(maxPoints, (int) (radius * 6)));
      for (int i = 0; i < points; i++) {
         double angle = 2 * Math.PI * i / points;
         double x = center.getX() + Math.cos(angle) * radius;
         double z = center.getZ() + Math.sin(angle) * radius;
         world.spawnParticle(particle, x, center.getY() + yOffset, z, 1, 0, 0, 0, 0, data, true);
      }
   }

   /** Vertical column of particles from {@code bottom} up to {@code bottom + height}. */
   public static <T> void column(Location bottom, double fromHeight, double toHeight, double step, Particle particle, T data,
                                 double spread) {
      World world = bottom.getWorld();
      double top = Math.min(toHeight, world.getMaxHeight() - bottom.getY());
      for (double h = Math.max(0, fromHeight); h <= top; h += step) {
         world.spawnParticle(particle, bottom.getX(), bottom.getY() + h, bottom.getZ(), 1, spread, 0, spread, 0, data, true);
      }
   }

   /**
    * Damages and knocks back living entities in range. Entities outside the current region are skipped,
    * so this never mutates another region's state.
    */
   public static void damageArea(Location center, double radius, double damage, double knockback, UUID owner,
                                 boolean damageOwner) {
      Collection<Entity> nearby;
      try {
         nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
      } catch (RuntimeException ex) {
         return;
      }
      DamageSource source = DamageSource.builder(DamageType.EXPLOSION).withDamageLocation(center).build();
      for (Entity entity : nearby) {
         if (!(entity instanceof LivingEntity living) || living.isDead() || !Bukkit.isOwnedByCurrentRegion(living)) {
            continue;
         }
         if (!damageOwner && living.getUniqueId().equals(owner)) {
            continue;
         }
         if (living instanceof Player player
               && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            continue;
         }
         double distance = living.getLocation().distance(center);
         if (distance > radius) {
            continue;
         }
         double closeness = 1.0 - distance / radius;
         if (damage > 0) {
            living.damage(damage * (0.5 + 0.5 * closeness), source);
         }
         if (knockback > 0) {
            Vector push = living.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
               push = new Vector(0, 1, 0);
            }
            push.normalize().multiply(knockback * closeness + 0.2);
            push.setY(Math.max(push.getY(), 0.3 + 0.2 * knockback * closeness));
            living.setVelocity(living.getVelocity().add(push));
         }
      }
   }

   /** Sets a bounded number of fires on exposed ground inside the radius (loaded, owned chunks only). */
   public static void scatterFire(Location center, double radius, int attempts) {
      World world = center.getWorld();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      for (int i = 0; i < attempts; i++) {
         double angle = random.nextDouble(Math.PI * 2);
         double distance = Math.sqrt(random.nextDouble()) * radius;
         int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
         int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
         if (!world.isChunkLoaded(x >> 4, z >> 4) || !Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            continue;
         }
         Block ground = world.getHighestBlockAt(x, z);
         Block above = ground.getRelative(BlockFace.UP);
         if (ground.getType().isSolid() && above.getType().isAir() && above.getY() < world.getMaxHeight()) {
            above.setType(Material.FIRE);
         }
      }
   }

   /** Surface height at x/z if the chunk is loaded and owned here, otherwise {@code fallbackY}. */
   public static double surfaceY(World world, double x, double z, double fallbackY) {
      int bx = (int) Math.floor(x);
      int bz = (int) Math.floor(z);
      if (!world.isChunkLoaded(bx >> 4, bz >> 4) || !Bukkit.isOwnedByCurrentRegion(world, bx >> 4, bz >> 4)) {
         return fallbackY;
      }
      return world.getHighestBlockYAt(bx, bz) + 1.0;
   }
}
