package net.srv.legendaryadditions.admin.effect;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * The Unstable SMP / Orbital Strike Cannon nuke: every TNT is spawned at one point high above the
 * target with the same fuse and a horizontal push, so while it falls air drag spreads it into
 * concentric rings that land and detonate together.
 *
 * <p>Same maths as RelmesStudios' Orbital Strike Cannon: TNT keeps 98% of its speed each tick, so
 * after {@code fuse} ticks it has travelled {@code v * (1 - 0.98^fuse) / 0.02} blocks sideways.
 * Dividing each ring radius by that factor gives the start speed that lands it on its ring.</p>
 */
public final class NukeRingsEffect {
   private NukeRingsEffect() {
   }

   public static void launch(Plugin plugin, Location target, AdminSettings.NukeRings settings, UUID owner) {
      Location center = target.clone();
      World world = center.getWorld();
      Bukkit.getRegionScheduler().execute(plugin, center, () -> {
         if (!EffectGuards.worldStillLoaded(world)) {
            return;
         }
         double top = world.getMaxHeight() - 1.0;
         Location spawn = new Location(world, center.getBlockX() + 0.5,
               Math.min(center.getY() + settings.spawnHeight(), top), center.getBlockZ() + 0.5);
         int fuse = settings.fuseTicks();
         double scale = (1.0 - Math.pow(0.98, fuse)) / 0.02;
         List<Double> radii = settings.ringRadii();
         List<Integer> counts = settings.ringCounts();
         ThreadLocalRandom random = ThreadLocalRandom.current();

         int spawned = 0;
         if (settings.centerTnt()) {
            spawnTnt(spawn, new Vector(), fuse, owner, settings);
            spawned++;
         }
         for (int ring = 0; ring < radii.size(); ring++) {
            double radius = radii.get(ring);
            int count = counts.get(ring);
            // A TNT may wander from its ring by "misalign" blocks but never past halfway to the next ring.
            double minRadius = ring == 0 ? 0.0 : (radii.get(ring - 1) + radius) / 2.0;
            double maxRadius = ring == radii.size() - 1 ? radius + settings.misalign() : (radius + radii.get(ring + 1)) / 2.0;
            for (int i = 0; i < count; i++) {
               double angle = 2.0 * Math.PI * i / count;
               double offsetAngle = random.nextDouble() * 2.0 * Math.PI;
               double offset = Math.sqrt(random.nextDouble()) * settings.misalign();
               double x = radius * Math.cos(angle) + offset * Math.cos(offsetAngle);
               double z = radius * Math.sin(angle) + offset * Math.sin(offsetAngle);
               double r = Math.sqrt(x * x + z * z);
               if (r > 0 && (r < minRadius || r > maxRadius)) {
                  double clamped = Math.max(minRadius, Math.min(maxRadius, r)) / r;
                  x *= clamped;
                  z *= clamped;
               }
               spawnTnt(spawn, new Vector(x / scale, 0.0, z / scale), fuse, owner, settings);
               spawned++;
            }
         }

         Player caster = Bukkit.getPlayer(owner);
         if (caster != null) {
            String report = "Nuke launched: " + spawned + " TNT in " + radii.size() + " rings, impact in "
                  + String.format("%.1f", fuse / 20.0) + "s.";
            caster.getScheduler().run(plugin, t -> Messages.info(caster, report), null);
         }
      });
   }

   private static void spawnTnt(Location at, Vector velocity, int fuse, UUID owner, AdminSettings.NukeRings settings) {
      at.getWorld().spawn(at, TNTPrimed.class, tnt -> {
         tnt.setFuseTicks(fuse);
         tnt.setYield(settings.power());
         tnt.setVelocity(velocity);
         ExplosionGuard.tag(tnt, owner, !settings.damageOwner(), !settings.destroyBlocks());
      });
   }
}
