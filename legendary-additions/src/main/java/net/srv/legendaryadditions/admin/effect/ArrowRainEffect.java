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
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Arrow Rod: a sphere of arrows around the target, every one flying straight at its centre, in
 * several waves. The arrow start points cover the whole sphere evenly (a Fibonacci sphere), and
 * each wave is turned a little so its arrows fill the gaps between the previous wave's.
 *
 * <p>Every arrow's owner (shooter) is the player who used the rod, so hits and kills count as
 * theirs. Start points inside solid blocks (the part of the sphere under the ground) are skipped,
 * because an arrow spawned inside a block is stuck at once.</p>
 */
public final class ArrowRainEffect {
   /** Arrows vanish this many ticks after landing (vanilla despawns stuck arrows at 1200). */
   private static final int LANDED_LIFETIME_START = 1100;
   private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

   private ArrowRainEffect() {
   }

   public static void launch(Plugin plugin, Player caster, Location target, AdminSettings.ArrowRod settings) {
      // Aim at body height above the looked-at spot.
      Location center = target.clone().add(0, 1.0, 0);
      double spin = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
      for (int wave = 0; wave < settings.waves(); wave++) {
         int number = wave;
         double damage = wave == 0 ? settings.damage() : settings.laterWaveDamage();
         // Turn each wave by a fraction of the golden angle so its points land between the last wave's.
         double rotation = spin + wave * GOLDEN_ANGLE / settings.waves();
         Runnable fire = () -> fireWave(plugin, caster, center, settings, damage, rotation);
         if (number == 0) {
            Bukkit.getRegionScheduler().execute(plugin, center, fire);
         } else {
            Bukkit.getRegionScheduler().runDelayed(plugin, center, t -> fire.run(), (long) number * settings.waveIntervalTicks());
         }
      }
      Fx.sound(center, Sound.ENTITY_ARROW_SHOOT, SoundCategory.MASTER, 4F, 0.5F);
      Fx.particle(center, Particle.CLOUD, 30, settings.sphereRadius(), 0.01);
   }

   /** Runs on the region owning the centre; points in other regions are spawned on their own region. */
   private static void fireWave(Plugin plugin, Player caster, Location center, AdminSettings.ArrowRod settings, double damage,
                                double rotation) {
      World world = center.getWorld();
      if (!EffectGuards.worldStillLoaded(world)) {
         return;
      }
      int n = settings.arrowsPerWave();
      double radius = settings.sphereRadius();
      for (int i = 0; i < n; i++) {
         double y = 1.0 - 2.0 * (i + 0.5) / n;
         double ring = Math.sqrt(1.0 - y * y);
         double theta = GOLDEN_ANGLE * i + rotation;
         Vector out = new Vector(Math.cos(theta) * ring, y, Math.sin(theta) * ring);
         Location at = center.clone().add(out.clone().multiply(radius));
         if (at.getY() < world.getMinHeight() || at.getY() >= world.getMaxHeight()) {
            continue;
         }
         Vector velocity = out.multiply(-settings.speed());
         if (Bukkit.isOwnedByCurrentRegion(at)) {
            spawnArrow(caster, at, velocity, damage, settings);
         } else {
            Bukkit.getRegionScheduler().execute(plugin, at, () -> spawnArrow(caster, at, velocity, damage, settings));
         }
      }
   }

   private static void spawnArrow(Player caster, Location at, Vector velocity, double damage, AdminSettings.ArrowRod settings) {
      Block block = at.getBlock();
      if (block.getType().isSolid()) {
         return;
      }
      at.setDirection(velocity);
      at.getWorld().spawn(at, Arrow.class, arrow -> {
         arrow.setShooter(caster);
         arrow.setDamage(damage);
         arrow.setCritical(true);
         arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
         arrow.setLifetimeTicks(LANDED_LIFETIME_START);
         arrow.setVelocity(velocity);
         ExplosionGuard.tag(arrow, caster.getUniqueId(), !settings.damageOwner(), false);
      });
   }
}
