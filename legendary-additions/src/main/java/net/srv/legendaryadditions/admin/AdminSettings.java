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
      StabColumn stab,
      Strike nuke,
      NukeRings nukeRings,
      Teleport teleport,
      LawNuke lawNuke,
      WitherNuke witherNuke,
      WolfRod wolfRod,
      ArrowRod arrowRod,
      String adminWorldKey,
      Suggestions suggestions,
      boolean rodSounds,
      boolean rodParticles) {

   public record Targeting(double maxDistance, Set<String> disabledWorlds) {}

   public record Strike(double radius, double damage, double knockback, int warningTicks, boolean destroyBlocks,
                        boolean createFire, boolean damageOwner, double craterRadius) {}

   /** style "rings" = Unstable SMP TNT rings; "crater" = the old silent crater strike. */
   public record NukeRings(boolean rings, List<Double> ringRadii, List<Integer> ringCounts, double spawnHeight,
                           int fuseTicks, double misalign, boolean centerTnt, float power, boolean destroyBlocks,
                           boolean damageOwner) {}

   /** The Unstable SMP datapack nuke: 1 centre TNT plus 9 rings, 1169 TNT in total. */
   public static final List<Double> DEFAULT_RING_RADII = List.of(9.8, 19.7, 28.9, 37.9, 46.7, 55.5, 64.1, 72.6, 81.1);
   public static final List<Integer> DEFAULT_RING_COUNTS = List.of(48, 96, 118, 132, 142, 150, 154, 162, 166);

   /**
    * style "tnt" = Unstable SMP / Orbital Strike Cannon stab: a column of primed TNT from the build
    * limit down to bedrock; "crater" = the old silent crater strike.
    */
   public record StabColumn(boolean tnt, int spacing, int tntPerLayer, float power, int fuseTicks, int blocksPerTick,
                            boolean destroyBlocks, boolean damageOwner) {}

   public record Teleport(double maxDistance, int safeSearchRadius, boolean allowLava) {}

   /** TNT summoned in the sky above the target, spread evenly over {@code radius} as it falls. */
   public record LawNuke(double radius, int explosions, float explosionPower, int warningTicks, double spawnHeight,
                         int fuseTicks, boolean destroyBlocks, boolean createFire, boolean damageOwner) {}

   public record WitherNuke(int skullCount, double spawnHeight, double maxHorizontalSpeed, boolean charged,
                            boolean destroyBlocks, boolean damageOwner, int skullsPerTick) {}

   public record WolfRod(int count, boolean wolfArmor, int strengthAmplifier, int speedAmplifier, int buffTicks,
                         int fireResistanceTicks, int lifetimeSeconds) {}

   public record Suggestions(boolean enabled, boolean allowAllPlayers, boolean storePlayerNames, int minLength,
                             int maxLength, boolean allowVoteRemoval, String sorting, int inputTimeoutSeconds,
                             int maxPendingPerPlayer, boolean allowDelete, String dateFormat) {}

   /** A sphere of arrows around the target, all flying at its centre, in {@code waves} waves. */
   public record ArrowRod(double sphereRadius, int arrowsPerWave, int waves, int waveIntervalTicks, double speed,
                          double damage, double laterWaveDamage, boolean damageOwner) {}

   public static AdminSettings load(FileConfiguration config) {
      List<String> disabled = config.getStringList("rod-targeting.disabled-worlds");
      Targeting targeting = new Targeting(
            positive(config.getDouble("rod-targeting.max-distance", 256.0), 256.0),
            disabled.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet()));
      Strike orbital = strike(config.getConfigurationSection("orbital-strike"), 8.0, 50.0, 2.5, 0, true, false, 4.0);
      Strike nuke = strike(config.getConfigurationSection("nuke"), 20.0, 150.0, 5.0, 0, true, false, 9.0);

      NukeRings nukeRings = nukeRings(config);
      StabColumn stab = new StabColumn(
            !"crater".equalsIgnoreCase(config.getString("orbital-strike.style", "tnt")),
            clamp(config.getInt("orbital-strike.tnt-spacing", 2), 1, 16),
            clamp(config.getInt("orbital-strike.tnt-per-layer", 1), 1, 16),
            (float) clamp(config.getDouble("orbital-strike.tnt-power", 4.0), 0.5, 20.0),
            clamp(config.getInt("orbital-strike.fuse-ticks", 20), 0, 400),
            clamp(config.getInt("orbital-strike.blocks-per-tick", 16), 0, 4096),
            config.getBoolean("orbital-strike.destroy-blocks", true),
            config.getBoolean("orbital-strike.damage-owner", false));

      Teleport teleport = new Teleport(
            positive(config.getDouble("teleport-rod.max-distance", 100.0), 100.0),
            clamp(config.getInt("teleport-rod.safe-search-radius", 3), 0, 6),
            config.getBoolean("teleport-rod.allow-lava-destinations", false));

      LawNuke lawNuke = new LawNuke(
            positive(config.getDouble("law-nuke.radius", 45.0), 45.0),
            clamp(config.getInt("law-nuke.explosions", 140), 1, 1000),
            (float) clamp(config.getDouble("law-nuke.explosion-power", 4.0), 0.5, 20.0),
            clamp(config.getInt("law-nuke.warning-time-ticks", 0), 0, 1200),
            positive(config.getDouble("law-nuke.spawn-height", 70.0), 70.0),
            clamp(config.getInt("law-nuke.fuse-ticks", 80), 1, 400),
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
            clamp(config.getDouble("arrow-rod.sphere-radius", 12.0), 2.0, 48.0),
            clamp(config.getInt("arrow-rod.arrows-per-wave", 400), 1, 2000),
            clamp(config.getInt("arrow-rod.waves", 3), 1, 20),
            clamp(config.getInt("arrow-rod.wave-interval-ticks", 10), 1, 200),
            clamp(config.getDouble("arrow-rod.speed", 3.0), 0.1, 10.0),
            Math.max(0.0, config.getDouble("arrow-rod.damage", 70.0)),
            Math.max(0.0, config.getDouble("arrow-rod.later-wave-damage", 80.0)),
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
      return new AdminSettings(targeting, orbital, stab, nuke, nukeRings, teleport, lawNuke, witherNuke, wolfRod, arrowRod, worldKey, suggestions,
            config.getBoolean("rod-effects.sounds", false),
            config.getBoolean("rod-effects.particles", false));
   }

   static NukeRings nukeRings(FileConfiguration config) {
      List<Double> radii = config.getDoubleList("nuke.ring-radii").stream()
            .filter(r -> r > 0.0 && r <= 200.0).toList();
      List<Integer> counts = config.getIntegerList("nuke.ring-tnt-counts").stream()
            .map(c -> clamp(c, 1, 1000)).toList();
      if (radii.isEmpty() || radii.size() != counts.size()) {
         radii = DEFAULT_RING_RADII;
         counts = DEFAULT_RING_COUNTS;
      }
      return new NukeRings(
            !"crater".equalsIgnoreCase(config.getString("nuke.style", "rings")),
            radii,
            counts,
            clamp(config.getDouble("nuke.spawn-height", 70.0), 1.0, 300.0),
            clamp(config.getInt("nuke.fuse-ticks", 80), 1, 400),
            clamp(config.getDouble("nuke.misalign", 0.0), 0.0, 10.0),
            config.getBoolean("nuke.center-tnt", true),
            (float) clamp(config.getDouble("nuke.tnt-power", 4.0), 0.5, 20.0),
            config.getBoolean("nuke.destroy-blocks", true),
            config.getBoolean("nuke.damage-owner", false));
   }

   private static Strike strike(ConfigurationSection s, double radius, double damage, double knockback, int warning,
                                boolean destroy, boolean fire, double power) {
      if (s == null) {
         return new Strike(radius, damage, knockback, warning, destroy, fire, false, power);
      }
      return new Strike(
            positive(s.getDouble("radius", radius), radius),
            Math.max(0.0, s.getDouble("damage", damage)),
            Math.max(0.0, s.getDouble("knockback", knockback)),
            clamp(s.getInt("warning-time-ticks", warning), 0, 1200),
            s.getBoolean("destroy-blocks", destroy),
            s.getBoolean("create-fire", fire),
            s.getBoolean("damage-owner", false),
            Math.max(1.0, Math.min(24.0, s.getDouble("crater-radius", power))));
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
