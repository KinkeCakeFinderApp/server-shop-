package net.srv.legendaryadditions.admin.effect;

import java.io.File;
import java.util.UUID;
import net.srv.legendaryadditions.admin.rod.RodKeys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Spawns the TNT for the Stab and the Nuke.
 *
 * <p>spigot.yml's {@code max-tnt-per-tick} (default 100) lets only that many primed TNT move and
 * count down each tick; the rest freeze. A 1169-TNT nuke would then hang in the sky and go off in
 * slow waves. When a strike needs more TNT than the limit, it uses falling TNT blocks instead:
 * they fall with exactly the same gravity (0.04) and air drag (0.98) as primed TNT and are not
 * limited, and each one explodes like TNT when it lands or when its fuse runs out.</p>
 */
public final class TntSpawner implements Listener {
   private static volatile int tntLimit = 100;

   /** Reads max-tnt-per-tick from spigot.yml. @return the limit (0 or less = unlimited). */
   public static int loadLimit() {
      try {
         File spigot = new File("spigot.yml");
         if (spigot.isFile()) {
            tntLimit = YamlConfiguration.loadConfiguration(spigot).getInt("world-settings.default.max-tnt-per-tick", 100);
         }
      } catch (RuntimeException ignored) {
         // Keep the default.
      }
      return tntLimit;
   }

   /** True when {@code count} primed TNT can all tick together. */
   public static boolean primedFits(int count) {
      int limit = tntLimit;
      return limit <= 0 || count <= limit;
   }

   private TntSpawner() {
   }

   public static TntSpawner listener() {
      return new TntSpawner();
   }

   /**
    * Spawns one TNT. Must run on the region that owns {@code at}.
    *
    * @param primed true = real primed TNT, false = a falling TNT block with the same motion
    */
   public static void spawn(Plugin plugin, Location at, Vector velocity, boolean gravity, int fuse, float power,
                            UUID owner, boolean protectOwner, boolean noBlockDamage, boolean primed) {
      if (primed) {
         at.getWorld().spawn(at, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(fuse);
            tnt.setYield(power);
            tnt.setGravity(gravity);
            tnt.setVelocity(velocity);
            ExplosionGuard.tag(tnt, owner, protectOwner, noBlockDamage);
         });
         return;
      }
      FallingBlock block = at.getWorld().spawn(at, FallingBlock.class, fb -> {
         fb.setBlockData(Material.TNT.createBlockData());
         fb.setDropItem(false);
         fb.setHurtEntities(false);
         fb.setGravity(gravity);
         fb.setVelocity(velocity);
         fb.getPersistentDataContainer().set(RodKeys.FAKE_TNT, PersistentDataType.FLOAT, power);
         ExplosionGuard.tag(fb, owner, protectOwner, noBlockDamage);
      });
      // The entity scheduler follows the block into whichever region it flies to.
      block.getScheduler().runDelayed(plugin, task -> detonate(block), null, Math.max(1, fuse));
   }

   /** A falling TNT block that lands explodes there instead of turning into a TNT block. */
   @EventHandler(priority = EventPriority.LOWEST)
   public void onLand(EntityChangeBlockEvent event) {
      if (event.getEntity() instanceof FallingBlock block && block.getPersistentDataContainer().has(RodKeys.FAKE_TNT)) {
         event.setCancelled(true);
         detonate(block);
      }
   }

   private static void detonate(Entity block) {
      if (!block.isValid()) {
         return;
      }
      Float power = block.getPersistentDataContainer().get(RodKeys.FAKE_TNT, PersistentDataType.FLOAT);
      Byte noBlocks = block.getPersistentDataContainer().get(RodKeys.NO_BLOCK_DAMAGE, PersistentDataType.BYTE);
      Byte protect = block.getPersistentDataContainer().get(RodKeys.PROTECT_OWNER, PersistentDataType.BYTE);
      String owner = block.getPersistentDataContainer().get(RodKeys.OWNER, PersistentDataType.STRING);
      Location at = block.getLocation();
      block.remove();
      ExplosionGuard.explode(at, power == null ? 4F : power, false, noBlocks == null || noBlocks != 1,
            protect != null && protect == 1 && owner != null ? UUID.fromString(owner) : null);
   }
}
