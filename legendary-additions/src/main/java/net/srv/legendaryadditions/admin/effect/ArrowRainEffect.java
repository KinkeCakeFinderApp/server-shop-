package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
import net.srv.legendaryadditions.admin.AdminSettings;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.util.Vector;

/**
 * "Arrow Shot" from the datapack: a 5x5 grid of arrows, two layers deep (damage 70 and 80),
 * fired straight down at 10 blocks/tick from 86 blocks above the target.
 */
public final class ArrowRainEffect {
   /** Arrows vanish this many ticks after landing (vanilla despawns stuck arrows at 1200). */
   private static final int LANDED_LIFETIME_START = 1100;

   private ArrowRainEffect() {
   }

   /** Runs on the caster's region; the target column is inside that region. */
   public static void launch(Location target, AdminSettings.ArrowShot settings, UUID owner) {
      World world = target.getWorld();
      double y = Math.min(target.getY() + settings.spawnHeight(), world.getMaxHeight() - 2);
      int half = settings.gridSize() / 2;
      Vector velocity = new Vector(0, -settings.speed(), 0);

      for (int layer = 0; layer < settings.layers(); layer++) {
         double damage = layer == 0 ? settings.damage() : settings.secondLayerDamage();
         double layerY = y + layer * 0.5;
         for (int dx = -half; dx <= settings.gridSize() - 1 - half; dx++) {
            for (int dz = -half; dz <= settings.gridSize() - 1 - half; dz++) {
               Location at = new Location(world, Math.floor(target.getX()) + 0.5 + dx, layerY, Math.floor(target.getZ()) + 0.5 + dz);
               world.spawn(at, Arrow.class, arrow -> {
                  arrow.setDamage(damage);
                  arrow.setCritical(true);
                  arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                  arrow.setLifetimeTicks(LANDED_LIFETIME_START);
                  arrow.setVelocity(velocity);
                  ExplosionGuard.tag(arrow, owner, !settings.damageOwner(), false);
               });
            }
         }
      }
      Location top = new Location(world, target.getX(), y, target.getZ());
      Fx.particle(top, Particle.CLOUD, 30, settings.gridSize() / 2.0, 0.01);
      world.playSound(target, Sound.ENTITY_ARROW_SHOOT, SoundCategory.MASTER, 4F, 0.5F);
   }
}
