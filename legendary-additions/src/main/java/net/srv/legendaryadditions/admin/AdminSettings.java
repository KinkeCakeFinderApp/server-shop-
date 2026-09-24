package net.srv.legendaryadditions.admin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml. */
public record AdminSettings(
      Targeting targeting,
      Strike orbital,
      Strike nuke,
      Teleport teleport,
      LawNuke lawNuke,
      WitherNuke witherNuke,
      WolfRod wolfRod,
      ArrowRod arrowRod,
      String adminWorldKey,
      Suggestions suggestions) {

   public record Targeting(double maxDistance, Set<String> disabledWorlds) {}

   public record Strike(double radius, double damage, double knockback, int warningTicks, boolean destroyBlocks,
                        boolean createFire, boolean damageOwner, float blockDamagePower) {}

   public record Teleport(double maxDistance, int safeSearchRadius, boolean allowLava) {}

   public record LawNuke(double radius, int explosions, float explosionPower, int warningTicks, int detonationTicks,
                         boolean destroyBlocks, boolean createFire, boolean damageOwner) {}

   public record WitherNuke(int skullCount, double spawnHeight, double maxHorizontalSpeed, boolean charged,
                            boolean destroyBlocks, boolean damageOwner, int skullsPerTick) {}

   public record WolfRod(int count, boolean wolfArmor, int strengthAmplifier, int speedAmplifier, int buffTicks,
                         int fireResistanceTicks, int lifetimeSeconds) {}

   public record Suggestions(boolean enabled, boolean allowAllPlayers, boolean storePlayerNames, int minLength,
                             int maxLength, boolean allowVoteRemoval, String sorting, int inputTimeoutSeconds,
                             int maxPendingPerPlayer, boolean allowDelete, String dateFormat) {}

   public record ArrowRod(int gridSize, int layers, double spawnHeight, double speed, double damage,
                           double secondLayerDamage, boolean damageOwner) {}

   public static AdminSettings load(FileConfiguration config) {
      List<String> disabled = config.getStringList("rod-targeting.disabled-worlds");
      Targeting targeting = new Targeting(
            positive(config.getDouble("rod-targeting.max-distance", 256.0), 256.0),
            disabled.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet()));
      Strike orbital = strike(config.getConfigurationSection("orbital-strike"), 8.0, 50.0, 2.5, 20, false, false, 5.0);
      Strike nuke = strike(config.getConfigurationSection("nuke"), 20.0, 150.0, 5.0, 60, true, true, 10.0);

      Teleport teleport = new Teleport(
            positive(config.getDouble("teleport-rod.max-distance", 100.0), 100.0),
            clamp(config.getInt("teleport-rod.safe-search-radius", 3), 0, 6),
            config.getBoolean("teleport-rod.allow-lava-destinations", false));

      LawNuke lawNuke = new LawNuke(
            positive(config.getDouble("law-nuke.radius", 45.0), 45.0),
            clamp(config.getInt("law-nuke.explosions", 140), 1, 1000),
            (float) clamp(config.getDouble("law-nuke.explosion-power", 4.0), 0.5, 20.0),
            clamp(config.getInt("law-nuke.warning-time-ticks", 90), 1, 1200),
            clamp(config.getInt("law-nuke.detonation-ticks", 30), 1, 400),
            config.getBoolean("law-nuke.destroy-blocks", true),
            config.getBoolean("law-nuke.create-fire", false),
            config.getBoolean("law-nuke.damage-owner", false));

      WitherNuke witherNuke = new WitherNuke(
            clamp(config.getInt("wither-nuke.skull-count", 160), 1, 2000),
            positive(config.getDouble("wither-nuke.spawn-height", 70.0), 70.0),
            positive(config.getDouble("wither-nuke.max-horizontal-speed", 2.3), 2.3),
            config.getBoolean("wither-nuke.charged-skulls", true),
            config.getBoolean("wither-nuke.destroy-blocks", true),
            config.getBoolean("wither-nuke.damage-owner", false),
            clamp(config.getInt("wither-nuke.skulls-per-tick", 20), 1, 200));

      WolfRod wolfRod = new WolfRod(
            clamp(config.getInt("wolf-rod.count", 53), 1, 200),
            config.getBoolean("wolf-rod.wolf-armor", true),
            clamp(config.getInt("wolf-rod.strength-amplifier", 1), 0, 10),
            clamp(config.getInt("wolf-rod.speed-amplifier", 1), 0, 10),
            clamp(config.getInt("wolf-rod.buff-duration-ticks", 1800), 1, 1_000_000),
            clamp(config.getInt("wolf-rod.fire-resistance-ticks", 9600), 1, 1_000_000),
            Math.max(0, config.getInt("wolf-rod.lifetime-seconds", 0)));

      ArrowRod arrowRod = new ArrowRod(
            clamp(config.getInt("arrow-rod.grid-size", 5), 1, 15),
            clamp(config.getInt("arrow-rod.layers", 2), 1, 5),
            positive(config.getDouble("arrow-rod.spawn-height", 86.0), 86.0),
            positive(config.getDouble("arrow-rod.speed", 10.0), 10.0),
            Math.max(0.0, config.getDouble("arrow-rod.damage", 70.0)),
            Math.max(0.0, config.getDouble("arrow-rod.second-layer-damage", 80.0)),
            config.getBoolean("arrow-rod.damage-owner", false));

      String worldKey = config.getString("admin-dimension.world", "adminplugin:admin");

      int maxLength = clamp(config.getInt("suggestions.max-length", 500), 10, 4000);
      String sorting = config.getString("suggestions.sorting", "highest-votes");
      Suggestions suggestions = new Suggestions(
            config.getBoolean("suggestions.enabled", true),
            config.getBoolean("suggestions.allow-all-players", true),
            config.getBoolean("suggestions.store-player-names", true),
            clamp(config.getInt("suggestions.min-length", 5), 1, maxLength),
            maxLength,
            config.getBoolean("suggestions.allow-vote-removal", false),
            sorting == null ? "highest-votes" : sorting,
            clamp(config.getInt("suggestions.input-timeout-seconds", 120), 10, 3600),
            Math.max(0, config.getInt("suggestions.max-pending-per-player", 5)),
            config.getBoolean("suggestions.allow-delete", true),
            config.getString("suggestions.date-format", "yyyy-MM-dd HH:mm"));
      return new AdminSettings(targeting, orbital, nuke, teleport, lawNuke, witherNuke, wolfRod, arrowRod, worldKey, suggestions);
   }

   private static Strike strike(ConfigurationSection s, double radius, double damage, double knockback, int warning,
                                boolean destroy, boolean fire, double power) {
      if (s == null) {
         return new Strike(radius, damage, knockback, warning, destroy, fire, false, (float) power);
      }
      return new Strike(
            positive(s.getDouble("radius", radius), radius),
            Math.max(0.0, s.getDouble("damage", damage)),
            Math.max(0.0, s.getDouble("knockback", knockback)),
            clamp(s.getInt("warning-time-ticks", warning), 1, 1200),
            s.getBoolean("destroy-blocks", destroy),
            s.getBoolean("create-fire", fire),
            s.getBoolean("damage-owner", false),
            (float) clamp(s.getDouble("block-damage-power", power), 0.5, 20.0));
   }

   private static double positive(double value, double fallback) {
      return value > 0.0 && Double.isFinite(value) ? value : fallback;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}
