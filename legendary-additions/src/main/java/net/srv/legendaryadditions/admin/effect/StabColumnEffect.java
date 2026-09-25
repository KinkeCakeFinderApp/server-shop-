package net.srv.legendaryadditions.admin.effect;

import java.util.ArrayList;
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
 * The Unstable SMP / Orbital Strike Cannon stab: a column of primed TNT at the target from the
 * world's build limit down to bedrock. The top TNT goes off first and the blast runs down the
 * column, drilling a shaft to bedrock.
 *
 * <p>The TNT floats (no gravity) and is held in place every tick, so neither falling nor the
 * knockback of the explosions above can move it out of the column. The whole column is in one
 * chunk, so one region task owns every TNT.</p>
 */
public final class StabColumnEffect {
   private StabColumnEffect() {
   }

   public static void launch(Plugin plugin, Location target, AdminSettings.StabColumn settings, UUID owner) {
      Location center = target.clone();
      World world = center.getWorld();
      Bukkit.getRegionScheduler().execute(plugin, center, () -> {
         if (!EffectGuards.worldStillLoaded(world)) {
            return;
         }
         double x = center.getBlockX() + 0.5;
         double z = center.getBlockZ() + 0.5;
         int top = world.getMaxHeight() - 1;
         int bottom = world.getMinHeight();
         ThreadLocalRandom random = ThreadLocalRandom.current();
         List<TNTPrimed> column = new ArrayList<>();
         int lastFuse = 0;
         for (int y = top; y >= bottom; y -= settings.spacing()) {
            int fuse = settings.fuseTicks() + (settings.blocksPerTick() == 0 ? 0 : (top - y) / settings.blocksPerTick());
            lastFuse = Math.max(lastFuse, fuse);
            for (int i = 0; i < settings.tntPerLayer(); i++) {
               // Extra TNT in a layer sits slightly off-centre so the shaft gets wider, not deeper.
               double dx = i == 0 ? 0.0 : (random.nextDouble() - 0.5) * 0.8;
               double dz = i == 0 ? 0.0 : (random.nextDouble() - 0.5) * 0.8;
               Location at = new Location(world, x + dx, y, z + dz);
               column.add(world.spawn(at, TNTPrimed.class, tnt -> {
                  tnt.setFuseTicks(fuse);
                  tnt.setYield(settings.power());
                  tnt.setGravity(false);
                  tnt.setVelocity(new Vector());
                  ExplosionGuard.tag(tnt, owner, !settings.damageOwner(), !settings.destroyBlocks());
               }));
            }
         }

         int spawned = column.size();
         int endTick = lastFuse + 2;
         int[] tick = {0};
         Bukkit.getRegionScheduler().runAtFixedRate(plugin, center, task -> {
            column.removeIf(tnt -> !tnt.isValid());
            if (column.isEmpty() || tick[0]++ > endTick) {
               task.cancel();
               return;
            }
            for (TNTPrimed tnt : column) {
               if (Bukkit.isOwnedByCurrentRegion(tnt)) {
                  tnt.setVelocity(new Vector());
               }
            }
         }, 1L, 1L);

         Player caster = Bukkit.getPlayer(owner);
         if (caster != null) {
            String report = "Stab launched: " + spawned + " TNT from y=" + top + " down to bedrock (y=" + bottom + ").";
            caster.getScheduler().run(plugin, t -> Messages.info(caster, report), null);
         }
      });
   }
}
