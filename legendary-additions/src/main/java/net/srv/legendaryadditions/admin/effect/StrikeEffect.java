package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
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
 * Orbital Strike (/stab, /stabshot) and Nuke (/nuke, /nukeshot).
 *
 * <p>One region-scheduled task per strike runs the warning, the impact and a short aftermath,
 * then cancels itself. The target is locked when the rod is cast.</p>
 */
public final class StrikeEffect {
   public enum Style {
      ORBITAL, NUKE
   }

   private StrikeEffect() {
   }

   public static void launch(Plugin plugin, Location target, AdminSettings.Strike settings, Style style, UUID owner) {
      Location center = target.clone();
      boolean nuke = style == Style.NUKE;
      int warning = settings.warningTicks();
      int aftermath = nuke ? 60 : 16;
      double radius = settings.radius();
      double beamHeight = nuke ? 90 : 60;
      Particle.DustOptions markerDust = new Particle.DustOptions(nuke ? Color.fromRGB(255, 90, 0) : Color.fromRGB(255, 20, 20), nuke ? 2.2F : 1.6F);
      Particle.DustOptions innerDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2F);
      World world = center.getWorld();

      world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, nuke ? 6F : 4F, 0.6F);
      if (nuke) {
         world.playSound(center, Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 6F, 0.5F);
      }

      int[] tick = {0};
      Bukkit.getRegionScheduler().runAtFixedRate(plugin, center, task -> {
         if (!EffectGuards.worldStillLoaded(world)) {
            task.cancel();
            return;
         }
         int t = tick[0]++;
         if (t < warning) {
            double progress = (double) t / warning;
            if (t % 2 == 0) {
               Fx.ring(center, radius, 0.15, Particle.DUST, markerDust, nuke ? 120 : 64);
               Fx.ring(center, Math.max(0.6, radius * (1.0 - progress)), 0.2, Particle.DUST, innerDust, 48);
               // The beam charges downward from the sky toward the target.
               double beamBottom = beamHeight * (1.0 - progress);
               Fx.column(center, beamBottom, beamHeight, nuke ? 1.0 : 1.5, Particle.END_ROD, null, nuke ? 0.6 : 0.05);
               if (nuke) {
                  Fx.column(center, beamBottom, beamHeight, 2.5, Particle.SOUL_FIRE_FLAME, null, 1.6);
               }
            }
            if (t % 10 == 0) {
               world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, nuke ? 5F : 3F,
                     (float) (0.5 + progress * 1.5));
            }
            if (warning - t <= 10) {
               Fx.particle(center.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, nuke ? 40 : 15, radius * 0.3, 0.2);
            }
         } else if (t == warning) {
            impact(plugin, center, settings, nuke, owner);
         } else if (t <= warning + aftermath) {
            int a = t - warning;
            double shock = radius * 1.4 * a / aftermath + 1.0;
            if (a % 2 == 0 || !nuke) {
               Fx.ring(center, shock, 0.4, Particle.CLOUD, null, nuke ? 140 : 72);
            }
            if (nuke && a % 2 == 0) {
               // Rising mushroom cloud.
               double stem = Math.min(40, a * 1.2);
               Fx.column(center, 0, stem, 2.0, Particle.CAMPFIRE_SIGNAL_SMOKE, null, 1.5);
               Location cap = center.clone().add(0, stem, 0);
               Fx.ring(cap, radius * 0.45, 0, Particle.LARGE_SMOKE, null, 60);
               Fx.particle(cap, Particle.FLAME, 25, radius * 0.25, 0.02);
            }
         } else {
            task.cancel();
         }
      }, 1L, 1L);
   }

   private static void impact(Plugin plugin, Location center, AdminSettings.Strike settings, boolean nuke, UUID owner) {
      World world = center.getWorld();
      double radius = settings.radius();

      Fx.column(center, 0, nuke ? 120 : 80, 0.5, Particle.END_ROD, null, nuke ? 1.2 : 0.2);
      Fx.particle(center, Particle.EXPLOSION_EMITTER, nuke ? 6 : 2, radius * 0.25, 0);
      Fx.particle(center, Particle.ELECTRIC_SPARK, nuke ? 200 : 100, radius * 0.4, 0.6);
      Fx.particle(center, Particle.LARGE_SMOKE, nuke ? 160 : 70, radius * 0.35, 0.08);
      Fx.particle(center, Particle.LAVA, nuke ? 60 : 25, radius * 0.3, 0);
      Fx.particle(center, Particle.FIREWORK, nuke ? 120 : 50, 0.5, 0.6);
      if (nuke) {
         Fx.particle(center, Particle.FLAME, 150, radius * 0.4, 0.15);
         Fx.ring(center, radius * 0.5, 0.5, Particle.EXPLOSION, null, 24);
      }

      world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, nuke ? 10F : 6F, nuke ? 0.5F : 0.8F);
      world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, nuke ? 10F : 6F, nuke ? 0.4F : 0.7F);
      world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, nuke ? 8F : 4F, nuke ? 0.5F : 1.0F);

      Fx.damageArea(center, radius, settings.damage(), settings.knockback(), owner, settings.damageOwner());

      UUID protectedOwner = settings.damageOwner() ? null : owner;
      if (settings.destroyBlocks()) {
         ExplosionGuard.explode(center, settings.blockDamagePower(), settings.createFire(), true, protectedOwner);
         if (nuke) {
            // A ring of secondary blasts widens the crater without scanning blocks ourselves.
            int blasts = 8;
            for (int i = 0; i < blasts; i++) {
               double angle = 2 * Math.PI * i / blasts;
               Location at = center.clone().add(Math.cos(angle) * radius * 0.55, 0, Math.sin(angle) * radius * 0.55);
               float power = settings.blockDamagePower() * 0.6F;
               Bukkit.getRegionScheduler().runDelayed(plugin, at,
                     t -> ExplosionGuard.explode(at, power, settings.createFire(), true, protectedOwner), 2L + i);
            }
         }
      } else if (settings.createFire()) {
         Fx.scatterFire(center, radius, (int) Math.min(64, radius * 3));
      }
   }
}
