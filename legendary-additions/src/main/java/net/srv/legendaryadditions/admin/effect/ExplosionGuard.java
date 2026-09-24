package net.srv.legendaryadditions.admin.effect;

import java.util.UUID;
import net.srv.legendaryadditions.admin.rod.RodKeys;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Implements the {@code damage-owner: false} and {@code destroy-blocks: false} options for
 * rod explosions and rod projectiles.
 *
 * <p>Plugin explosions are created through {@link #explode}; the damage events they fire run
 * synchronously on the same region thread, so a thread-local is enough to know whose explosion
 * it is without holding any cross-region entity reference.</p>
 */
public final class ExplosionGuard implements Listener {
   private static final ThreadLocal<UUID> PROTECTED_OWNER = new ThreadLocal<>();

   /** Must be called on the region that owns {@code at}. */
   public static void explode(Location at, float power, boolean fire, boolean breakBlocks, UUID protectedOwner) {
      PROTECTED_OWNER.set(protectedOwner);
      try {
         at.getWorld().createExplosion(at, power, fire, breakBlocks);
      } finally {
         PROTECTED_OWNER.remove();
      }
   }

   /** Tags a rod-spawned entity with its owner and behaviour flags. */
   public static void tag(Entity entity, UUID owner, boolean protectOwner, boolean noBlockDamage) {
      PersistentDataContainer pdc = entity.getPersistentDataContainer();
      pdc.set(RodKeys.OWNER, PersistentDataType.STRING, owner.toString());
      pdc.set(RodKeys.PROTECT_OWNER, PersistentDataType.BYTE, (byte) (protectOwner ? 1 : 0));
      pdc.set(RodKeys.NO_BLOCK_DAMAGE, PersistentDataType.BYTE, (byte) (noBlockDamage ? 1 : 0));
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onExplosionDamage(EntityDamageEvent event) {
      UUID owner = PROTECTED_OWNER.get();
      if (owner == null) {
         return;
      }
      EntityDamageEvent.DamageCause cause = event.getCause();
      if ((cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
            && owner.equals(event.getEntity().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onProjectileDamage(EntityDamageByEntityEvent event) {
      PersistentDataContainer pdc = event.getDamager().getPersistentDataContainer();
      Byte protect = pdc.get(RodKeys.PROTECT_OWNER, PersistentDataType.BYTE);
      if (protect == null || protect != 1) {
         return;
      }
      String owner = pdc.get(RodKeys.OWNER, PersistentDataType.STRING);
      if (owner != null && owner.equals(event.getEntity().getUniqueId().toString())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onRodProjectileExplode(EntityExplodeEvent event) {
      Byte noBlocks = event.getEntity().getPersistentDataContainer().get(RodKeys.NO_BLOCK_DAMAGE, PersistentDataType.BYTE);
      if (noBlocks != null && noBlocks == 1) {
         event.blockList().clear();
      }
   }
}
