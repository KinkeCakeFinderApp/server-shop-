package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.AdminSettings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Law-Nuke Shot: after a 90-tick fuse with a descending payload cloud, a carpet of TNT-strength
 * blasts ripples outward across a wide radius. Each blast is scheduled on the region that owns
 * its own location, so no entities are spawned and no region touches another's blocks.
 */
public final class LawNukeEffect {
   private LawNukeEffect() {
   }

   public static void launch(Plugin plugin, Location target, AdminSettings.LawNuke settings, UUID owner) {
      Location center = target.clone();
      World world = center.getWorld();
      Particle.DustOptions marker = new Particle.DustOptions(Color.fromRGB(255, 170, 0), 2.0F);
      int warning = settings.warningTicks();
      double radius = settings.radius();

      Fx.sound(center, Sound.ENTITY_TNT_PRIMED, SoundCategory.MASTER, 8F, 0.5F);
      int[] tick = {0};
      Bukkit.getRegionScheduler().runAtFixedRate(plugin, center, task -> {
         if (!EffectGuards.worldStillLoaded(world)) {
            task.cancel();
            return;
         }
         int t = tick[0]++;
         if (t >= warning) {
            task.cancel();
            detonate(plugin, center, settings, owner);
            return;
         }
         double progress = (double) t / warning;
         if (t % 3 == 0) {
            Fx.ring(center, radius, 0.2, Particle.DUST, marker, 150);
            Fx.ring(center, radius * 0.5, 0.2, Particle.DUST, marker, 80);
            // The payload cloud descends from 86 blocks up toward the target.
            Location payload = center.clone().add(0, 86 * (1.0 - progress), 0);
            Fx.particle(payload, Particle.LARGE_SMOKE, 20, radius * 0.15 * (0.3 + progress), 0.01);
            Fx.particle(payload, Particle.FLAME, 10, radius * 0.1, 0.01);
         }
         if (t % 20 == 0) {
            Fx.sound(center, Sound.ENTITY_TNT_PRIMED, SoundCategory.MASTER, 6F, (float) (0.5 + progress));
         }
      }, 1L, 1L);
   }

   private static void detonate(Plugin plugin, Location center, AdminSettings.LawNuke settings, UUID owner) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      UUID protectedOwner = settings.damageOwner() ? null : owner;
      int count = settings.explosions();
      int spread = settings.detonationTicks();
      World world = center.getWorld();

      Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 12F, 0.4F);
      for (int i = 0; i < count; i++) {
         // sqrt keeps the blast density even across the whole disc.
         double distance = Math.sqrt(random.nextDouble()) * settings.radius();
         double angle = random.nextDouble(Math.PI * 2);
         double x = center.getX() + Math.cos(angle) * distance;
         double z = center.getZ() + Math.sin(angle) * distance;
         Location at = new Location(world, x, center.getY(), z);
         long delay = 1L + (long) (spread * (distance / settings.radius())) + random.nextInt(3);
         Bukkit.getRegionScheduler().runDelayed(plugin, at, t -> {
            at.setY(Fx.surfaceY(world, x, z, center.getY()));
            ExplosionGuard.explode(at, settings.explosionPower(), settings.createFire(), settings.destroyBlocks(), protectedOwner);
         }, delay);
      }
   }
}
