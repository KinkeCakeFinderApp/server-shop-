package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The stab: a column of TNT-strength explosions at the target from the world's build limit down
 * to bedrock, drilling a shaft to bedrock.
 *
 * <p>Like the Law Nuke it creates the explosions directly instead of spawning TNT, so there is no
 * fuse and nothing can be pushed out of the column: by default the whole column goes off the
 * moment the rod is used. The column is in one chunk, so one region task owns every explosion.</p>
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
         int top = world.getMaxHeight() - 1;
         int bottom = world.getMinHeight();
         UUID protectedOwner = settings.damageOwner() ? null : owner;
         int explosions = 0;
         for (int y = top; y >= bottom; y -= settings.spacing()) {
            long delay = settings.fuseTicks() + (settings.blocksPerTick() == 0 ? 0 : (top - y) / settings.blocksPerTick());
            for (int i = 0; i < settings.tntPerLayer(); i++) {
               explosions++;
               Location at = layerPoint(center, y, i);
               if (delay <= 0) {
                  ExplosionGuard.explode(at, settings.power(), false, settings.destroyBlocks(), protectedOwner);
               } else {
                  Bukkit.getRegionScheduler().runDelayed(plugin, at, t -> {
                     if (EffectGuards.worldStillLoaded(world)) {
                        ExplosionGuard.explode(at, settings.power(), false, settings.destroyBlocks(), protectedOwner);
                     }
                  }, delay);
               }
            }
         }

         Player caster = Bukkit.getPlayer(owner);
         if (caster != null) {
            String report = "Stab: " + explosions + " explosions from y=" + top + " down to bedrock (y=" + bottom + ").";
            caster.getScheduler().run(plugin, t -> Messages.info(caster, report), null);
         }
      });
   }

   /** Extra explosions in a layer sit slightly off-centre so the shaft gets wider, not deeper. */
   private static Location layerPoint(Location center, int y, int index) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      double dx = index == 0 ? 0.0 : (random.nextDouble() - 0.5) * 0.8;
      double dz = index == 0 ? 0.0 : (random.nextDouble() - 0.5) * 0.8;
      return new Location(center.getWorld(), center.getBlockX() + 0.5 + dx, y, center.getBlockZ() + 0.5 + dz);
   }
}
