package net.srv.legendaryadditions.admin.rod;

import java.util.function.Supplier;
import java.util.logging.Level;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.effect.ArrowRainEffect;
import net.srv.legendaryadditions.admin.effect.LawNukeEffect;
import net.srv.legendaryadditions.admin.effect.StrikeEffect;
import net.srv.legendaryadditions.admin.effect.TeleportEffect;
import net.srv.legendaryadditions.admin.effect.WitherNukeEffect;
import net.srv.legendaryadditions.admin.effect.WolfPackEffect;
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
 * Cast → ray trace from the eyes → lock target → activate. The cast is cancelled, so no hook is
 * thrown and the fishing hook is never used for targeting. Only genuine signed rods are touched;
 * every other fishing rod keeps vanilla behaviour.
 */
public final class RodListener implements Listener {
   private final Plugin plugin;
   private final RodRegistry registry;
   private final Supplier<AdminSettings> settings;

   public RodListener(Plugin plugin, RodRegistry registry, Supplier<AdminSettings> settings) {
      this.plugin = plugin;
      this.registry = registry;
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
      if (!player.hasPermission("admindimension.use")) {
         Messages.error(player, Messages.NO_PERMISSION);
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
      RayTargeting.Target target = RayTargeting.trace(player, range);
      if (target == null) {
         Messages.error(player, Messages.NO_TARGET);
         return;
      }

      boolean activated;
      try {
         activated = this.activate(kind, player, target, config);
      } catch (RuntimeException ex) {
         this.plugin.getLogger().log(Level.SEVERE, "Rod " + kind.typeId(inspection.reusable()) + " failed for " + player.getName(), ex);
         Messages.error(player, Messages.ACTIVATION_FAILED);
         return;
      }
      if (activated && !inspection.reusable()) {
         this.consumeOne(player, hand, item);
      }
   }

   private boolean activate(RodKind kind, Player player, RayTargeting.Target target, AdminSettings config) {
      Location center = target.center();
      switch (kind) {
         case ORBITAL -> StrikeEffect.launch(this.plugin, center, config.orbital(), StrikeEffect.Style.ORBITAL, player.getUniqueId());
         case NUKE -> StrikeEffect.launch(this.plugin, center, config.nuke(), StrikeEffect.Style.NUKE, player.getUniqueId());
         case TELEPORT -> {
            return TeleportEffect.teleport(this.plugin, player, target, config.teleport());
         }
         case LAW_NUKE -> LawNukeEffect.launch(this.plugin, center, config.lawNuke(), player.getUniqueId());
         case WITHER_NUKE -> WitherNukeEffect.launch(this.plugin, center, config.witherNuke(), player.getUniqueId());
         case WOLF -> WolfPackEffect.launch(this.plugin, player, center, config.wolfRod());
         case ARROW -> ArrowRainEffect.launch(center, config.arrowShot(), player.getUniqueId());
      }
      return true;
   }

   /** Removes exactly one rod from the stack in the casting hand, with the datapack's break effect. */
   private void consumeOne(Player player, EquipmentSlot hand, ItemStack used) {
      PlayerInventory inventory = player.getInventory();
      ItemStack current = inventory.getItem(hand);
      if (current.getAmount() > 1) {
         current.setAmount(current.getAmount() - 1);
         inventory.setItem(hand, current);
      } else {
         inventory.setItem(hand, null);
      }
      Location at = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5));
      player.getWorld().spawnParticle(Particle.ITEM, at, 5, 0.2, 0.4, 0.2, 0.0, used.clone());
      player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1F, 1F);
   }

   private EquipmentSlot castingHand(Player player, EquipmentSlot reported) {
      PlayerInventory inventory = player.getInventory();
      if (reported == EquipmentSlot.HAND || reported == EquipmentSlot.OFF_HAND) {
         return reported;
      }
      if (inventory.getItemInMainHand().getType() == Material.FISHING_ROD) {
         return EquipmentSlot.HAND;
      }
      if (inventory.getItemInOffHand().getType() == Material.FISHING_ROD) {
         return EquipmentSlot.OFF_HAND;
      }
      return null;
   }
}
