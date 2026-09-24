package net.srv.legendaryadditions.admin.effect;

import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.rod.RayTargeting;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/** Admin Teleport Rod (/teleportshot). */
public final class TeleportEffect {
   private static final int VERTICAL_SEARCH = 3;

   private TeleportEffect() {
   }

   /** @return false if the destination was rejected as unsafe (the rod is then not consumed). */
   public static boolean teleport(Plugin plugin, Player player, RayTargeting.Target target, AdminSettings.Teleport settings) {
      Block start = target.entity() != null
            ? target.entity().getLocation().getBlock()
            : SafeLocations.standingBlock(target.block(), target.face());
      Location destination = SafeLocations.find(start, settings.safeSearchRadius(), VERTICAL_SEARCH);
      if (destination == null) {
         Messages.error(player, Messages.UNSAFE_TELEPORT);
         return false;
      }
      Location from = player.getLocation();
      destination.setYaw(from.getYaw());
      destination.setPitch(from.getPitch());

      from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.4, null, true);
      from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1F, 1F);
      player.setFallDistance(0F);
      player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
         if (!success) {
            return;
         }
         Bukkit.getRegionScheduler().execute(plugin, destination, () -> {
            destination.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destination.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1, null, true);
            destination.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1F, 1.2F);
         });
      });
      return true;
   }
}
