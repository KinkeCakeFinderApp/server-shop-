package net.srv.legendaryadditions.admin.rod;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.effect.Fx;
import net.srv.legendaryadditions.admin.effect.RodEffects;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Cast → ray trace from the eyes → lock target → activate. Only casts of rods that carry rod data
 * are touched; a plain fishing rod never reaches past {@link RodRegistry#inspect}, so vanilla
 * fishing keeps working. The cast of a special rod is cancelled, so no hook is thrown.
 */
public final class RodListener implements Listener {
   private final Plugin plugin;
   private final RodRegistry registry;
   private final RodEffects effects;
   private final Supplier<AdminSettings> settings;

   public RodListener(Plugin plugin, RodRegistry registry, RodEffects effects, Supplier<AdminSettings> settings) {
      this.plugin = plugin;
      this.registry = registry;
      this.effects = effects;
      this.settings = settings;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCast(PlayerFishEvent event) {
      if (event.getState() != PlayerFishEvent.State.FISHING) {
         return;
      }
      Player player = event.getPlayer();
      EquipmentSlot hand = this.castingHand(player, event.getHand());
      if (hand == null) {
         return;
      }
      ItemStack item = player.getInventory().getItem(hand);
      RodRegistry.Inspection inspection = this.registry.inspect(item);
      if (inspection.status() == RodRegistry.Status.NORMAL) {
         return;
      }

      event.setCancelled(true);
      if (inspection.status() == RodRegistry.Status.FORGED) {
         Messages.error(player, Messages.FAKE_ROD);
         return;
      }
      AdminSettings config = this.settings.get();
      if (config.targeting().disabledWorlds().contains(player.getWorld().getName().toLowerCase())) {
         Messages.error(player, Messages.DISABLED_WORLD);
         return;
      }

      RodKind kind = inspection.kind();
      double range = kind == RodKind.TELEPORT
            ? Math.min(config.teleport().maxDistance(), config.targeting().maxDistance())
            : config.targeting().maxDistance();
      // The target is computed and copied right now; later camera movement cannot move it.
      RayTargeting.Target target = RayTargeting.trace(player, range);
      if (target == null) {
         Messages.error(player, Messages.NO_TARGET);
         return;
      }

      boolean activated;
      try {
         activated = this.effects.get(kind).activate(player, target, config);
      } catch (RuntimeException ex) {
         this.plugin.getLogger().log(Level.SEVERE,
               "Rod " + kind.typeId(inspection.reusable()) + " failed to activate for " + player.getName(), ex);
         Messages.error(player, Messages.ACTIVATION_FAILED);
         return;
      }
      if (activated && !inspection.reusable()) {
         this.consumeOne(player, hand, inspection.nonce());
      }
   }

   /**
    * Removes exactly one rod from the casting hand, and only if that hand still holds the same
    * signed rod (matched by its unique nonce).
    */
   private void consumeOne(Player player, EquipmentSlot hand, String nonce) {
      PlayerInventory inventory = player.getInventory();
      ItemStack current = inventory.getItem(hand);
      RodRegistry.Inspection now = this.registry.inspect(current);
      if (now.status() != RodRegistry.Status.GENUINE || !Objects.equals(now.nonce(), nonce)) {
         return;
      }
      ItemStack shown = current.clone();
      inventory.setItem(hand, removeOne(current));
      if (Fx.particlesEnabled()) {
         Location at = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5));
         player.getWorld().spawnParticle(Particle.ITEM, at, 5, 0.2, 0.4, 0.2, 0.0, shown);
      }
      Fx.sound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1F, 1F);
   }

   /** The stack after exactly one item is used: amount - 1, or null when it was the last one. */
   public static ItemStack removeOne(ItemStack stack) {
      if (stack == null || stack.getAmount() <= 1) {
         return null;
      }
      ItemStack rest = stack.clone();
      rest.setAmount(stack.getAmount() - 1);
      return rest;
   }

   private EquipmentSlot castingHand(Player player, EquipmentSlot reported) {
      if (reported == EquipmentSlot.HAND || reported == EquipmentSlot.OFF_HAND) {
         return reported;
      }
      PlayerInventory inventory = player.getInventory();
      if (inventory.getItemInMainHand().getType() == Material.FISHING_ROD) {
         return EquipmentSlot.HAND;
      }
      if (inventory.getItemInOffHand().getType() == Material.FISHING_ROD) {
         return EquipmentSlot.OFF_HAND;
      }
      return null;
   }
}
