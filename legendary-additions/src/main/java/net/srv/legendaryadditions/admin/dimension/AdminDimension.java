package net.srv.legendaryadditions.admin.dimension;

import java.util.Locale;
import java.util.function.Supplier;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.effect.SafeLocations;
import net.srv.legendaryadditions.admin.rod.RodKeys;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The persistent Admin dimension. It is a datapack dimension shipped inside the plugin jar and
 * registered at bootstrap, so the server creates and saves it like any vanilla dimension (Folia
 * cannot create worlds at runtime). The return point is stored in the player's persistent data,
 * so /admin return still works after a restart.
 */
public final class AdminDimension {
   private final Plugin plugin;
   private final Supplier<AdminSettings> settings;

   public AdminDimension(Plugin plugin, Supplier<AdminSettings> settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public World world() {
      NamespacedKey key = NamespacedKey.fromString(this.settings.get().adminWorldKey());
      return key == null ? null : Bukkit.getWorld(key);
   }

   /** Must be called on the player's own thread (commands from players already are). */
   public void enter(Player player) {
      World world = this.world();
      if (world == null) {
         Messages.error(player, Messages.DIMENSION_UNAVAILABLE);
         return;
      }
      if (player.getWorld().equals(world)) {
         Messages.info(player, "You are already in the Admin dimension. Use /admin return to leave.");
         return;
      }
      player.getPersistentDataContainer().set(RodKeys.RETURN_LOCATION, PersistentDataType.STRING, serialize(player.getLocation()));

      Location spawn = world.getSpawnLocation();
      world.getChunkAtAsync(spawn).thenAccept(chunk -> Bukkit.getRegionScheduler().execute(this.plugin, spawn, () -> {
         Location destination = this.safeArrival(world, spawn);
         player.getScheduler().run(this.plugin, task -> {
            destination.setYaw(player.getLocation().getYaw());
            destination.setPitch(player.getLocation().getPitch());
            player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
               if (success) {
                  Messages.success(player, "Welcome to the Admin dimension. Use /admin return to go back.");
               } else {
                  Messages.error(player, "Teleport to the Admin dimension was cancelled.");
               }
            });
         }, null);
      }));
   }

   public void leave(Player player) {
      String stored = player.getPersistentDataContainer().get(RodKeys.RETURN_LOCATION, PersistentDataType.STRING);
      Location destination = stored == null ? null : deserialize(stored);
      if (destination == null) {
         World main = Bukkit.getWorlds().getFirst();
         destination = main.getSpawnLocation().add(0.5, 0, 0.5);
         if (player.getWorld().equals(this.world())) {
            Messages.info(player, "No saved return point - sending you to the main world spawn.");
         } else {
            Messages.error(player, "You have no return point. Use /admin first.");
            return;
         }
      }
      player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
         if (success) {
            player.getScheduler().run(this.plugin,
                  task -> player.getPersistentDataContainer().remove(RodKeys.RETURN_LOCATION), null);
            Messages.success(player, "Returned from the Admin dimension.");
         } else {
            Messages.error(player, "Return teleport was cancelled.");
         }
      });
   }

   /** Runs on the region owning {@code spawn}. Builds a small platform the first time if needed. */
   private Location safeArrival(World world, Location spawn) {
      int x = spawn.getBlockX();
      int z = spawn.getBlockZ();
      Block surface = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      Location safe = SafeLocations.find(surface.getRelative(0, 1, 0), 2, 3);
      if (safe != null) {
         world.setSpawnLocation(safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
         return safe;
      }
      int y = Math.max(surface.getY(), world.getMinHeight() + 1);
      if (y + 3 >= world.getMaxHeight()) {
         y = world.getMaxHeight() - 4;
      }
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            world.getBlockAt(x + dx, y, z + dz).setType(Material.SMOOTH_STONE);
            world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR);
            world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR);
         }
      }
      world.setSpawnLocation(x, y + 1, z);
      return new Location(world, x + 0.5, y + 1, z + 0.5);
   }

   private static String serialize(Location location) {
      return String.join(";", location.getWorld().getKey().toString(),
            Double.toString(location.getX()), Double.toString(location.getY()), Double.toString(location.getZ()),
            Float.toString(location.getYaw()), Float.toString(location.getPitch()));
   }

   private static Location deserialize(String value) {
      String[] parts = value.split(";");
      if (parts.length != 6) {
         return null;
      }
      NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
      World world = key == null ? null : Bukkit.getWorld(key);
      if (world == null) {
         return null;
      }
      try {
         return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
               Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
      } catch (NumberFormatException ex) {
         return null;
      }
   }
}
