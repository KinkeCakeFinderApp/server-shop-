package net.srv.legendaryadditions.admin.dimension;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
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
 * The persistent, End-like Admin dimension ({@code adminplugin:admin}).
 *
 * <p>Folia cannot create worlds while the server runs, so the dimension is declared inside the
 * plugin jar and registered during plugin bootstrap (see {@code LegendaryAdditionsBootstrap}).
 * The server then generates and saves it like any vanilla dimension; nothing extra has to be
 * installed. It has its own dimension type (End sky, lighting and music, no dragon fight), so the
 * vanilla End is never touched.</p>
 *
 * <p>The return point is stored in the player's persistent data, so {@code /admin return} works
 * after restarts.</p>
 */
public final class AdminDimension {
   private static final NamespacedKey SPAWN_READY = NamespacedKey.fromString("adminplugin:central_spawn_ready");

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

   /** Must be called on the player's own thread (player commands already are). */
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
      String origin = serialize(player.getLocation());

      Location centre = this.centralSpawn(world);
      world.getChunkAtAsync(centre).whenComplete((chunk, error) -> {
         if (error != null) {
            this.plugin.getLogger().log(Level.WARNING, "Could not load the Admin dimension spawn chunk", error);
            player.getScheduler().run(this.plugin, t -> Messages.error(player, Messages.DIMENSION_UNAVAILABLE), null);
            return;
         }
         Bukkit.getRegionScheduler().execute(this.plugin, centre, () -> {
            Location destination = this.prepareArrival(world, centre);
            player.getScheduler().run(this.plugin, task -> {
               destination.setYaw(player.getLocation().getYaw());
               destination.setPitch(player.getLocation().getPitch());
               player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                  if (success) {
                     // Saved only after a successful arrival, on the player's thread.
                     player.getScheduler().run(this.plugin, t -> player.getPersistentDataContainer()
                           .set(RodKeys.RETURN_LOCATION, PersistentDataType.STRING, origin), null);
                     Messages.success(player, "Welcome to the Admin dimension. Use /admin return to go back.");
                  } else {
                     Messages.error(player, "The teleport to the Admin dimension was cancelled.");
                  }
               });
            }, null);
         });
      });
   }

   /** Must be called on the player's own thread. */
   public void leave(Player player) {
      String stored = player.getPersistentDataContainer().get(RodKeys.RETURN_LOCATION, PersistentDataType.STRING);
      if (stored == null) {
         Messages.error(player, "You have no saved return location. Use /admin first.");
         return;
      }
      Location destination = deserialize(stored);
      if (destination == null) {
         Messages.error(player, "Your saved return world no longer exists, so you cannot be returned there.");
         return;
      }
      player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
         if (success) {
            player.getScheduler().run(this.plugin,
                  task -> player.getPersistentDataContainer().remove(RodKeys.RETURN_LOCATION), null);
            Messages.success(player, "Returned to where you were before entering the Admin dimension.");
         } else {
            Messages.error(player, "The return teleport was cancelled.");
         }
      });
   }

   private Location centralSpawn(World world) {
      Byte ready = world.getPersistentDataContainer().get(SPAWN_READY, PersistentDataType.BYTE);
      if (ready != null && ready == 1) {
         return world.getSpawnLocation();
      }
      return new Location(world, 0.5, world.getMinHeight() + 1, 0.5);
   }

   /**
    * Runs on the region owning {@code centre}. The first time, finds the top of the central island
    * (building a small platform only if nothing safe exists) and saves it as the world spawn, so the
    * spawn is stable across restarts.
    */
   private Location prepareArrival(World world, Location centre) {
      Byte ready = world.getPersistentDataContainer().get(SPAWN_READY, PersistentDataType.BYTE);
      if (ready != null && ready == 1) {
         Location saved = centre.clone();
         if (SafeLocations.isSafe(saved.getBlock(), false)) {
            return saved.getBlock().getLocation().add(0.5, 0, 0.5);
         }
      }
      int x = centre.getBlockX();
      int z = centre.getBlockZ();
      Block surface = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      Location safe = surface.getY() > world.getMinHeight()
            ? SafeLocations.find(surface.getRelative(0, 1, 0), 3, 3, false)
            : null;
      if (safe == null) {
         int y = Math.clamp(surface.getY(), world.getMinHeight() + 64, world.getMaxHeight() - 4);
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               world.getBlockAt(x + dx, y, z + dz).setType(Material.END_STONE_BRICKS);
               world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR);
               world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR);
            }
         }
         safe = new Location(world, x + 0.5, y + 1, z + 0.5);
      }
      Location spawn = safe.clone();
      // World-level data (spawn point, world PDC) belongs to Folia's global region.
      Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
         world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
         world.getPersistentDataContainer().set(SPAWN_READY, PersistentDataType.BYTE, (byte) 1);
      });
      return safe;
   }

   static String serialize(Location location) {
      return String.join(";", location.getWorld().getKey().toString(),
            Double.toString(location.getX()), Double.toString(location.getY()), Double.toString(location.getZ()),
            Float.toString(location.getYaw()), Float.toString(location.getPitch()));
   }

   static Location deserialize(String value) {
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
