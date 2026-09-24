package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.AdminSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.WitherSkull;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * "Wither Nuke Shot" from the datapack: wither skulls rain down from 70 blocks above the target
 * with outward horizontal motion and steep downward speed. Skulls are released in small batches
 * per tick instead of all at once.
 */
public final class WitherNukeEffect {
   private WitherNukeEffect() {
   }

   public static void launch(Plugin plugin, Location target, AdminSettings.WitherNuke settings, UUID owner) {
      World world = target.getWorld();
      double spawnY = Math.min(target.getY() + settings.spawnHeight(), world.getMaxHeight() - 2);
      Location origin = new Location(world, target.getX(), spawnY, target.getZ());
      world.playSound(target, Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 8F, 0.7F);

      int[] spawned = {0};
      Bukkit.getRegionScheduler().runAtFixedRate(plugin, origin, task -> {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         int batch = Math.min(settings.skullsPerTick(), settings.skullCount() - spawned[0]);
         for (int i = 0; i < batch; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double speed = 0.2 + random.nextDouble() * (settings.maxHorizontalSpeed() - 0.2);
            Vector velocity = new Vector(Math.cos(angle) * speed, -(1.2 + random.nextDouble() * 10.0), Math.sin(angle) * speed);
            world.spawn(origin, WitherSkull.class, skull -> {
               skull.setCharged(settings.charged());
               skull.setAcceleration(velocity.clone().normalize().multiply(0.1));
               skull.setVelocity(velocity);
               ExplosionGuard.tag(skull, owner, !settings.damageOwner(), !settings.destroyBlocks());
            });
         }
         spawned[0] += batch;
         world.playSound(origin, Sound.ENTITY_WITHER_SHOOT, SoundCategory.MASTER, 6F, 0.6F);
         Fx.particle(origin, Particle.LARGE_SMOKE, 15, 2.0, 0.02);
         if (spawned[0] >= settings.skullCount()) {
            task.cancel();
         }
      }, 1L, 1L);
   }
}
