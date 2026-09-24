package net.srv.legendaryadditions.custom;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class RiftcasterListener implements Listener {
   private static final double SOUND_RADIUS = 16.0;
   private static final double LANDED_CHECK_DISTANCE = 1.5;
   private static final Set<State> REEL_STATES = EnumSet.of(State.REEL_IN, State.CAUGHT_FISH, State.CAUGHT_ENTITY, State.FAILED_ATTEMPT);
   private final Set<UUID> inGroundHooks = new HashSet<>();

   @EventHandler
   public void onPlayerFish(PlayerFishEvent event) {
      Player player = event.getPlayer();
      EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
      ItemStack item = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
      if (Riftcaster.isRiftcaster(item)) {
         if (event.getState() == State.IN_GROUND) {
            if (event.getHook() != null) {
               this.inGroundHooks.add(event.getHook().getUniqueId());
            }
         } else if (REEL_STATES.contains(event.getState())) {
            if (event.getHook() != null) {
               event.setCancelled(true);
               FishHook hook = event.getHook();
               UUID hookId = hook.getUniqueId();
               Location hookLocation = hook.getLocation();
               boolean landed = this.inGroundHooks.remove(hookId) || this.isLandedNear(hook) || hook.getHookedEntity() != null;
               hook.remove();
               if (landed) {
                  Location destination = hookLocation.clone();
                  destination.setYaw(player.getLocation().getYaw());
                  destination.setPitch(player.getLocation().getPitch());
                  player.teleport(destination);

                  for (Player nearby : destination.getWorld().getPlayers()) {
                     if (nearby.getLocation().distance(destination) <= 16.0) {
                        nearby.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isLandedNear(FishHook hook) {
      World world = hook.getWorld();
      Location origin = hook.getLocation();
      RayTraceResult result = world.rayTraceBlocks(origin, new Vector(0, -1, 0), 1.5, FluidCollisionMode.ALWAYS, true);
      return result != null;
   }
}
