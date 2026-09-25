package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Law-Nuke Shot: after an optional warning with a descending payload cloud, TNT is summoned high
 * in the sky above the target like the Nuke and falls spread evenly over a wide radius, where it
 * explodes when its fuse runs out.
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

   /**
    * Drops the TNT like the Nuke does: every TNT appears at one point high above the target with a
    * sideways push worked out so that, after air drag, it lands spread evenly over the radius when
    * its fuse runs out (same maths as {@link NukeRingsEffect}).
    */
   private static void detonate(Plugin plugin, Location center, AdminSettings.LawNuke settings, UUID owner) {
      World world = center.getWorld();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int count = settings.explosions();
      int fuse = settings.fuseTicks();
      double scale = (1.0 - Math.pow(0.98, fuse)) / 0.02;
      Location spawn = new Location(world, center.getBlockX() + 0.5,
            Math.min(center.getY() + settings.spawnHeight(), world.getMaxHeight() - 1.0), center.getBlockZ() + 0.5);
      boolean primed = TntSpawner.primedFits(count);

      Fx.sound(center, Sound.ENTITY_TNT_PRIMED, SoundCategory.MASTER, 12F, 0.4F);
      for (int i = 0; i < count; i++) {
         // The first TNT always lands on the exact target; sqrt keeps the rest evenly spread over the disc.
         double distance = i == 0 ? 0.0 : Math.sqrt(random.nextDouble()) * settings.radius();
         double angle = random.nextDouble(Math.PI * 2);
         Vector push = new Vector(Math.cos(angle) * distance / scale, 0.0, Math.sin(angle) * distance / scale);
         TntSpawner.spawn(plugin, spawn, push, true, fuse, settings.explosionPower(), settings.createFire(), owner,
               !settings.damageOwner(), !settings.destroyBlocks(), primed);
      }

      Player caster = Bukkit.getPlayer(owner);
      if (caster != null) {
         String report = "Law Nuke launched: " + count + " TNT over " + (int) settings.radius() + " blocks, impact in "
               + String.format("%.1f", fuse / 20.0) + "s.";
         caster.getScheduler().run(plugin, t -> Messages.info(caster, report), null);
      }
   }
}
