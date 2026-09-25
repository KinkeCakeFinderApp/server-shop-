package net.srv.eloranks.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.srv.eloranks.core.AntiFarmSettings;
import net.srv.eloranks.elo.EloSettings;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.rank.TierLadder;
import net.srv.eloranks.reward.RewardDef;
import net.srv.eloranks.reward.RewardItemDef;
import net.srv.eloranks.util.Durations;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Everything from config.yml, parsed and checked once. Immutable, so a reload just swaps the
 * instance. Every invalid value is reported (path, what is wrong, how to fix it) and replaced by
 * a safe fallback instead of being ignored silently.
 */
public final class Settings {
   public enum FullInventory { REFUSE, PENDING, DROP }

   public enum DisplayedTier { CURRENT, HIGHEST }

   public enum LuckPermsMode { PREFIX, GROUP, NONE }

   public record Pvp(Set<String> worlds, long creditLastAttackerMillis, long combatLogMillis) {
      public boolean countsIn(String world) {
         return this.worlds.isEmpty() || this.worlds.contains(world.toLowerCase(Locale.ROOT));
      }
   }

   public record LuckPerms(LuckPermsMode mode, int priority, Map<String, String> prefixes, Map<String, String> groups) {
      /** @param tierId a tier id, or "unranked" */
      public String prefix(String tierId) {
         return this.prefixes.getOrDefault(tierId, "");
      }

      public String group(String tierId) {
         return this.groups.getOrDefault(tierId, "");
      }
   }

   public record Title(boolean enabled, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
   }

   public record SoundFx(boolean enabled, Key sound, float volume, float pitch) {
   }

   public record ParticleFx(boolean enabled, Particle particle, int count, double spread, double speed) {
   }

   public record RankUp(boolean message, List<String> messageLines, boolean broadcast, String broadcastText,
                        Title title, SoundFx sound, ParticleFx particles) {
   }

   public record Button(int slot, Material material, String name, List<String> lore, boolean glow) {
   }

   public record ProgressBar(int length, String filled, String empty) {
   }

   public record Gui(DateTimeFormatter dateFormat, boolean filler, Material fillerMaterial, ProgressBar bar,
                     String mainTitle, int mainRows, Button profile, List<String> profileMaxLore, Button kits,
                     Button leaderboard, Button close,
                     String kitsTitle, int kitsRows, Button kitsBack, Button pending,
                     Button locked, Button available, Button cooldown,
                     String boardTitle, int boardRows, List<Integer> entrySlots, boolean playerHeads, Button entry,
                     Button previous, Button boardBack, Button next, Button empty) {
   }

   public record Permissions(String admin, String set, String reset, String reload, String claimReset, String forceTier) {
   }

   private final EloSettings elo;
   private final Pvp pvp;
   private final AntiFarmSettings antiFarm;
   private final long claimCooldownMillis;
   private final FullInventory fullInventory;
   private final TierLadder ladder;
   private final Map<String, RewardDef> rewards;
   private final String unrankedName;
   private final String unrankedColor;
   private final DisplayedTier displayedTier;
   private final LuckPerms luckPerms;
   private final RankUp rankUp;
   private final long leaderboardRefreshMillis;
   private final int leaderboardMaxEntries;
   private final Gui gui;
   private final Permissions permissions;
   private final String databaseFile;
   private final FileConfiguration raw;
   private final List<String> problems;

   private Settings(FileConfiguration config) {
      this.raw = config;
      Problems p = new Problems();
      this.problems = p.list;

      this.elo = new EloSettings(
            p.intAtLeast(config, "elo.starting-elo", 0),
            p.intAtLeast(config, "elo.minimum-elo", Integer.MIN_VALUE),
            p.intAtLeast(config, "elo.maximum-elo", 0),
            config.getBoolean("elo.allow-below-starting-elo"),
            p.intAtLeast(config, "elo.k-factor", 1),
            p.doubleAtLeast(config, "elo.loss-multiplier", 0.0),
            p.intAtLeast(config, "elo.minimum-gain", 0),
            p.intAtLeast(config, "elo.maximum-gain", 0));
      if (this.elo.minimumElo() < 0) {
         p.add("elo.minimum-elo", "is negative; ELO never goes below 0, so 0 is used", "set it to 0 or more");
      }
      if (this.elo.maximumElo() > 0 && this.elo.maximumElo() < this.elo.startingElo()) {
         p.add("elo.maximum-elo", "is lower than starting-elo", "raise maximum-elo or set it to 0 (no maximum)");
      }

      Set<String> worlds = new HashSet<>();
      config.getStringList("pvp.worlds").forEach(w -> worlds.add(w.toLowerCase(Locale.ROOT)));
      this.pvp = new Pvp(Set.copyOf(worlds), p.duration(config, "pvp.credit-last-attacker"), p.duration(config, "pvp.combat-log"));

      this.antiFarm = new AntiFarmSettings(
            p.duration(config, "anti-farming.same-victim-cooldown"),
            p.intAtLeast(config, "anti-farming.same-victim-limit", 0),
            p.duration(config, "anti-farming.same-victim-window"),
            p.intAtLeast(config, "anti-farming.gain-limit", 0),
            p.duration(config, "anti-farming.gain-limit-window"),
            config.getBoolean("anti-farming.block-same-ip"));
      if (this.antiFarm.sameVictimLimit() > 0 && this.antiFarm.sameVictimWindowMillis() <= 0) {
         p.add("anti-farming.same-victim-window", "must be longer than 0 when same-victim-limit is set", "e.g. 24h");
      }
      if (this.antiFarm.gainLimit() > 0 && this.antiFarm.gainWindowMillis() <= 0) {
         p.add("anti-farming.gain-limit-window", "must be longer than 0 when gain-limit is set", "e.g. 24h");
      }

      this.claimCooldownMillis = p.duration(config, "claim-cooldown");
      this.fullInventory = p.choice(config, "full-inventory", FullInventory.class, FullInventory.REFUSE);
      this.displayedTier = p.choice(config, "displayed-tier", DisplayedTier.class, DisplayedTier.CURRENT);

      this.rewards = this.loadRewards(config, p);
      this.ladder = this.loadTiers(config, p);
      this.unrankedName = config.getString("unranked.name", "Unranked");
      this.unrankedColor = config.getString("unranked.color", "&8");

      Map<String, String> prefixes = new HashMap<>();
      Map<String, String> groups = new HashMap<>();
      for (String id : this.tierIdsAndUnranked()) {
         prefixes.put(id, config.getString("luckperms.prefixes." + id, ""));
         groups.put(id, config.getString("luckperms.groups." + id, ""));
      }
      this.luckPerms = new LuckPerms(p.choice(config, "luckperms.mode", LuckPermsMode.class, LuckPermsMode.PREFIX),
            config.getInt("luckperms.priority", 150), Map.copyOf(prefixes), Map.copyOf(groups));
      if (this.luckPerms.mode() == LuckPermsMode.GROUP) {
         Set<String> seen = new HashSet<>();
         for (Tier tier : this.ladder.ascending()) {
            String group = this.luckPerms.group(tier.id());
            if (group.isBlank()) {
               p.add("luckperms.groups." + tier.id(), "is empty", "set the LuckPerms group for " + tier.name());
            } else if (!seen.add(group.toLowerCase(Locale.ROOT))) {
               p.add("luckperms.groups." + tier.id(), "uses group '" + group + "' twice", "give every tier its own group");
            }
         }
      }

      this.rankUp = new RankUp(
            config.getBoolean("rank-up.message.enabled"), config.getStringList("rank-up.message.text"),
            config.getBoolean("rank-up.broadcast.enabled"), config.getString("rank-up.broadcast.text", ""),
            new Title(config.getBoolean("rank-up.title.enabled"), config.getString("rank-up.title.title", ""),
                  config.getString("rank-up.title.subtitle", ""), config.getInt("rank-up.title.fade-in", 10),
                  config.getInt("rank-up.title.stay", 60), config.getInt("rank-up.title.fade-out", 20)),
            this.loadSound(config, p), this.loadParticles(config, p));

      this.leaderboardRefreshMillis = Math.max(5_000, p.duration(config, "leaderboard.refresh-interval"));
      this.leaderboardMaxEntries = Math.max(1, p.intAtLeast(config, "leaderboard.max-entries", 1));
      this.gui = this.loadGui(config, p);
      this.permissions = new Permissions(config.getString("permissions.admin", "elo.admin"),
            config.getString("permissions.set", "elo.admin.set"), config.getString("permissions.reset", "elo.admin.reset"),
            config.getString("permissions.reload", "elo.admin.reload"),
            config.getString("permissions.claim-reset", "elo.admin.claimreset"),
            config.getString("permissions.force-tier", "elo.admin.forcetier"));
      String file = config.getString("database.file", "eloranks.db");
      if (file == null || file.isBlank() || file.contains("..") || file.contains("/") || file.contains("\\")) {
         p.add("database.file", "must be a plain file name", "e.g. eloranks.db");
         file = "eloranks.db";
      }
      this.databaseFile = file;
   }

   /** Parses {@code config} (whose defaults must be the bundled config.yml). */
   public static Settings load(FileConfiguration config) {
      return new Settings(config);
   }

   // ------------------------------------------------------------------ loading

   private Map<String, RewardDef> loadRewards(FileConfiguration config, Problems p) {
      Map<String, RewardDef> out = new LinkedHashMap<>();
      ConfigurationSection section = config.getConfigurationSection("rewards");
      if (section == null) {
         p.add("rewards", "is missing", "restore the rewards section from the default config.yml");
         return out;
      }
      for (String id : section.getKeys(false)) {
         String path = "rewards." + id;
         ConfigurationSection r = section.getConfigurationSection(id);
         if (r == null) {
            p.add(path, "is not a section", "see the default config.yml");
            continue;
         }
         String containerText = r.getString("container", "none").toLowerCase(Locale.ROOT);
         RewardDef.Container container;
         if (containerText.equals("barrel")) {
            container = RewardDef.Container.BARREL;
         } else if (containerText.equals("none")) {
            container = RewardDef.Container.NONE;
         } else {
            p.add(path + ".container", "'" + containerText + "' is not barrel or none (shulker boxes are not supported)",
                  "use barrel or none");
            container = RewardDef.Container.BARREL;
         }
         List<RewardItemDef> items = new ArrayList<>();
         List<Map<?, ?>> list = r.getMapList("items");
         for (int i = 0; i < list.size(); i++) {
            RewardItemDef item = this.loadItem(list.get(i), path + ".items[" + i + "]", p);
            if (item != null) {
               items.add(item);
            }
         }
         if (items.isEmpty()) {
            p.add(path + ".items", "has no valid items; the reward cannot be claimed", "add at least one item");
            continue;
         }
         Material icon = p.material(r.getString("icon", ""), path + ".icon", items.getFirst().material());
         out.put(id, new RewardDef(id, r.getString("name", id), icon, r.getStringList("description"), container,
               r.getString("container-name", ""), List.copyOf(items)));
      }
      return out;
   }

   private RewardItemDef loadItem(Map<?, ?> map, String path, Problems p) {
      Object materialValue = map.get("material");
      Material material = p.material(materialValue == null ? "" : materialValue.toString(), path + ".material", null);
      if (material == null) {
         return null;
      }
      int amount;
      try {
         amount = Integer.parseInt(String.valueOf(map.containsKey("amount") ? map.get("amount") : 1));
      } catch (NumberFormatException ex) {
         p.add(path + ".amount", "'" + map.get("amount") + "' is not a whole number", "e.g. amount: 64");
         return null;
      }
      if (amount <= 0 || amount > 27 * 99 * 64) {
         p.add(path + ".amount", "must be between 1 and " + 27 * 99 * 64, "fix the amount");
         return null;
      }
      String name = map.get("name") == null ? null : map.get("name").toString();
      List<String> lore = new ArrayList<>();
      if (map.get("lore") instanceof List<?> l) {
         l.forEach(o -> lore.add(String.valueOf(o)));
      }
      boolean potion = material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION
            || material == Material.TIPPED_ARROW;
      PotionType potionType = null;
      List<RewardItemDef.Effect> effects = new ArrayList<>();
      Color color = null;
      if (map.get("potion-type") != null) {
         String id = map.get("potion-type").toString();
         potionType = key(id) == null ? null : Registry.POTION.get(key(id));
         if (!potion) {
            p.add(path + ".potion-type", "is only used for potion, splash_potion, lingering_potion and tipped_arrow", "remove it");
         } else if (potionType == null) {
            p.add(path + ".potion-type", "'" + id + "' is not a potion type",
                  "use e.g. strong_swiftness (Speed II), strong_strength (Strength II), fire_resistance");
            return null;
         }
      }
      if (map.get("effects") instanceof List<?> list) {
         for (int i = 0; i < list.size(); i++) {
            String epath = path + ".effects[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> e)) {
               p.add(epath, "is not {type: ..., level: ..., duration: ...}", "e.g. - {type: speed, level: 2, duration: 3m}");
               continue;
            }
            String typeId = String.valueOf(e.get("type"));
            PotionEffectType type = key(typeId) == null ? null : Registry.MOB_EFFECT.get(key(typeId));
            if (type == null) {
               p.add(epath + ".type", "'" + typeId + "' is not an effect", "use e.g. speed, strength, resistance");
               continue;
            }
            int level;
            long duration;
            try {
               level = Integer.parseInt(String.valueOf(e.containsKey("level") ? e.get("level") : 1));
               duration = Durations.parse(String.valueOf(e.containsKey("duration") ? e.get("duration") : "1m"));
            } catch (IllegalArgumentException ex) {
               p.add(epath, "has an invalid level or duration", "level is a number from 1, duration e.g. 3m");
               continue;
            }
            if (level < 1 || level > 256) {
               p.add(epath + ".level", "must be 1 to 256", "level 2 = Speed II");
               continue;
            }
            effects.add(new RewardItemDef.Effect(type, level, (int) Math.min(Integer.MAX_VALUE, duration / 50)));
         }
         if (!potion && !effects.isEmpty()) {
            p.add(path + ".effects", "is only used for potions", "remove it");
            effects.clear();
         }
      }
      if (map.get("color") != null) {
         String hex = map.get("color").toString().replace("#", "");
         try {
            color = Color.fromRGB(Integer.parseInt(hex, 16));
         } catch (IllegalArgumentException ex) {
            p.add(path + ".color", "'" + map.get("color") + "' is not #RRGGBB", "e.g. color: '#33EBFF'");
         }
      }
      if (potion && potionType == null && effects.isEmpty()) {
         p.add(path, "is a potion without potion-type or effects (it would be an uncraftable water bottle)",
               "add potion-type: strong_swiftness or effects");
         return null;
      }
      return new RewardItemDef(material, amount, name, List.copyOf(lore), potionType, List.copyOf(effects), color);
   }

   private TierLadder loadTiers(FileConfiguration config, Problems p) {
      List<Tier> tiers = new ArrayList<>();
      ConfigurationSection section = config.getConfigurationSection("tiers");
      int kitSlots = Math.max(1, Math.min(6, config.getInt("gui.kits.rows", 3))) * 9;
      Set<Integer> numbers = new HashSet<>();
      Set<Integer> elos = new HashSet<>();
      Set<Integer> slots = new HashSet<>();
      if (section == null || section.getKeys(false).isEmpty()) {
         p.add("tiers", "has no tiers", "restore the tiers section from the default config.yml");
         return new TierLadder(tiers);
      }
      for (String id : section.getKeys(false)) {
         String path = "tiers." + id;
         ConfigurationSection t = section.getConfigurationSection(id);
         if (t == null) {
            p.add(path, "is not a section", "see the default config.yml");
            continue;
         }
         if (!id.equals(id.toLowerCase(Locale.ROOT)) || id.equals("unranked")) {
            p.add(path, "tier ids must be lower case and not 'unranked'", "rename it, e.g. tier-6");
            continue;
         }
         if (!t.isInt("elo") || t.getInt("elo") < 0) {
            p.add(path + ".elo", "must be a whole number of 0 or more; " + id + " is disabled", "e.g. elo: 100");
            continue;
         }
         int elo = t.getInt("elo");
         String rewardId = t.getString("reward", "");
         if (!this.rewards.containsKey(rewardId)) {
            p.add(path + ".reward", "'" + rewardId + "' is not a valid reward under rewards:; " + id + " is disabled",
                  "use one of " + this.rewards.keySet());
            continue;
         }
         int number = t.getInt("number", 0);
         if (number <= 0) {
            p.add(path + ".number", "must be 1 or more", "e.g. number: 6");
            continue;
         }
         int slot = t.getInt("slot", -1);
         if (slot < 0 || slot >= kitSlots || !slots.add(slot)) {
            p.add(path + ".slot", "must be a free slot from 0 to " + (kitSlots - 1) + " of the /kits GUI", "change the slot");
            continue;
         }
         if (!numbers.add(number)) {
            p.add(path + ".number", "tier number " + number + " is used twice", "give every tier its own number");
         }
         if (!elos.add(elo)) {
            p.add(path + ".elo", elo + " ELO is used by two tiers", "give every tier a different elo");
         }
         tiers.add(new Tier(id, number, t.getString("name", id), t.getString("color", "&7"), elo, rewardId, slot));
      }
      if (tiers.isEmpty()) {
         p.add("tiers", "no valid tiers; players can not rank up", "fix the errors above");
      }
      return new TierLadder(tiers);
   }

   private SoundFx loadSound(FileConfiguration config, Problems p) {
      boolean enabled = config.getBoolean("rank-up.sound.enabled");
      Key key = null;
      try {
         key = Key.key(config.getString("rank-up.sound.sound", "minecraft:ui.toast.challenge_complete"));
      } catch (RuntimeException ex) {
         p.add("rank-up.sound.sound", "is not a sound id", "e.g. minecraft:ui.toast.challenge_complete");
         enabled = false;
      }
      return new SoundFx(enabled, key, (float) config.getDouble("rank-up.sound.volume", 1.0),
            (float) config.getDouble("rank-up.sound.pitch", 1.0));
   }

   private ParticleFx loadParticles(FileConfiguration config, Problems p) {
      boolean enabled = config.getBoolean("rank-up.particles.enabled");
      String id = config.getString("rank-up.particles.particle", "totem_of_undying");
      Particle particle = key(id) == null ? null : Registry.PARTICLE_TYPE.get(key(id));
      if (particle == null || particle.getDataType() != Void.class) {
         if (enabled) {
            p.add("rank-up.particles.particle", "'" + id + "' is not a particle that needs no extra data",
                  "use e.g. totem_of_undying, happy_villager, firework");
         }
         enabled = false;
      }
      return new ParticleFx(enabled, particle, Math.max(0, config.getInt("rank-up.particles.count", 60)),
            config.getDouble("rank-up.particles.spread", 0.6), config.getDouble("rank-up.particles.speed", 0.4));
   }

   private Gui loadGui(FileConfiguration config, Problems p) {
      DateTimeFormatter format;
      try {
         String zone = config.getString("gui.time-zone", "");
         format = DateTimeFormatter.ofPattern(config.getString("gui.date-format", "yyyy-MM-dd HH:mm"))
               .withZone(zone == null || zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone));
      } catch (RuntimeException ex) {
         p.add("gui.date-format / gui.time-zone", "is invalid (" + ex.getMessage() + ")", "e.g. yyyy-MM-dd HH:mm and Europe/London");
         format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
      }
      int mainRows = p.rows(config, "gui.main.rows");
      int kitsRows = p.rows(config, "gui.kits.rows");
      int boardRows = p.rows(config, "gui.leaderboard.rows");
      List<Integer> entrySlots = p.slots(config.getString("gui.leaderboard.entry-slots", "0-44"),
            "gui.leaderboard.entry-slots", boardRows * 9);
      return new Gui(format, config.getBoolean("gui.filler.enabled"),
            p.material(config.getString("gui.filler.material"), "gui.filler.material", Material.GRAY_STAINED_GLASS_PANE),
            new ProgressBar(Math.max(1, config.getInt("gui.progress-bar.length", 20)),
                  config.getString("gui.progress-bar.filled", "&a|"), config.getString("gui.progress-bar.empty", "&8|")),
            config.getString("gui.main.title", ""), mainRows,
            p.button(config, "gui.main.profile", mainRows), config.getStringList("gui.main.profile.max-tier-lore"),
            p.button(config, "gui.main.kits", mainRows), p.button(config, "gui.main.leaderboard", mainRows),
            p.button(config, "gui.main.close", mainRows),
            config.getString("gui.kits.title", ""), kitsRows, p.button(config, "gui.kits.back", kitsRows),
            p.button(config, "gui.kits.pending", kitsRows),
            p.stateButton(config, "gui.kits.states.locked"), p.stateButton(config, "gui.kits.states.available"),
            p.stateButton(config, "gui.kits.states.cooldown"),
            config.getString("gui.leaderboard.title", ""), boardRows, entrySlots,
            config.getBoolean("gui.leaderboard.entry.player-heads"), p.stateButton(config, "gui.leaderboard.entry"),
            p.button(config, "gui.leaderboard.previous", boardRows), p.button(config, "gui.leaderboard.back", boardRows),
            p.button(config, "gui.leaderboard.next", boardRows), p.button(config, "gui.leaderboard.empty", boardRows));
   }

   private List<String> tierIdsAndUnranked() {
      List<String> ids = new ArrayList<>(this.ladder.ascending().stream().map(Tier::id).toList());
      ids.add("unranked");
      return ids;
   }

   static NamespacedKey key(String id) {
      return id == null ? null : NamespacedKey.fromString(id.trim().toLowerCase(Locale.ROOT));
   }

   /** Collects configuration problems with the path, the problem and the fix. */
   private static final class Problems {
      private final List<String> list = new ArrayList<>();

      void add(String path, String problem, String fix) {
         this.list.add(path + ": " + problem + " -> " + fix);
      }

      int intAtLeast(FileConfiguration c, String path, int min) {
         if (c.isSet(path) && !c.isInt(path)) {
            this.add(path, "'" + c.get(path) + "' is not a whole number", "using the default " + c.getDefaults().getInt(path));
            return c.getDefaults().getInt(path);
         }
         int value = c.getInt(path);
         if (value < min) {
            this.add(path, "must be " + min + " or more", "using " + min);
            return min;
         }
         return value;
      }

      double doubleAtLeast(FileConfiguration c, String path, double min) {
         double value = c.getDouble(path);
         if (value < min) {
            this.add(path, "must be " + min + " or more", "using " + min);
            return min;
         }
         return value;
      }

      long duration(FileConfiguration c, String path) {
         String value = c.getString(path);
         try {
            return Durations.parse(value);
         } catch (IllegalArgumentException | ArithmeticException ex) {
            String fallback = c.getDefaults() == null ? "0s" : c.getDefaults().getString(path, "0s");
            this.add(path, "'" + value + "' is not a duration", "use e.g. 30s, 10m, 48h, 2d (using the default " + fallback + ")");
            return Durations.parse(fallback);
         }
      }

      <E extends Enum<E>> E choice(FileConfiguration c, String path, Class<E> type, E fallback) {
         String value = c.getString(path, fallback.name());
         try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException ex) {
            List<String> options = new ArrayList<>();
            for (E e : type.getEnumConstants()) {
               options.add(e.name().toLowerCase(Locale.ROOT));
            }
            this.add(path, "'" + value + "' is not valid", "use one of " + options + " (using " + fallback.name().toLowerCase(Locale.ROOT) + ")");
            return fallback;
         }
      }

      Material material(String id, String path, Material fallback) {
         Material material = id == null || id.isBlank() ? null : Material.matchMaterial(id.trim());
         if (material == null || !material.isItem() || material.isAir()) {
            if (fallback == null || (id != null && !id.isBlank())) {
               this.add(path, "'" + id + "' is not an item", "use a Minecraft item id like ender_pearl"
                     + (fallback == null ? "" : " (using " + fallback.getKey().getKey() + ")"));
            }
            return fallback;
         }
         return material;
      }

      int rows(FileConfiguration c, String path) {
         int rows = c.getInt(path, 3);
         if (rows < 1 || rows > 6) {
            this.add(path, "must be 1 to 6", "using 3");
            return 3;
         }
         return rows;
      }

      Button button(FileConfiguration c, String path, int rows) {
         int slot = c.getInt(path + ".slot", -1);
         if (slot < 0 || slot >= rows * 9) {
            this.add(path + ".slot", "must be 0 to " + (rows * 9 - 1), "change the slot; the button is hidden until then");
            slot = -1;
         }
         Material fallback = c.getDefaults() == null ? Material.STONE
               : Material.matchMaterial(c.getDefaults().getString(path + ".material", "stone"));
         return new Button(slot, this.material(c.getString(path + ".material"), path + ".material", fallback),
               c.getString(path + ".name", ""), c.getStringList(path + ".lore"), c.getBoolean(path + ".glow"));
      }

      /** A button without a slot; an empty material means "use the reward's icon". */
      Button stateButton(FileConfiguration c, String path) {
         String id = c.getString(path + ".material", "");
         Material material = id == null || id.isBlank() ? null : this.material(id, path + ".material", Material.PAPER);
         return new Button(-1, material, c.getString(path + ".name", ""), c.getStringList(path + ".lore"), c.getBoolean(path + ".glow"));
      }

      List<Integer> slots(String spec, String path, int size) {
         List<Integer> out = new ArrayList<>();
         try {
            for (String part : spec.split(",")) {
               String s = part.trim();
               if (s.contains("-")) {
                  String[] range = s.split("-");
                  for (int i = Integer.parseInt(range[0].trim()); i <= Integer.parseInt(range[1].trim()); i++) {
                     out.add(i);
                  }
               } else if (!s.isEmpty()) {
                  out.add(Integer.parseInt(s));
               }
            }
         } catch (RuntimeException ex) {
            this.add(path, "'" + spec + "' is not a slot list", "e.g. 0-44 or 10,11,12");
            out.clear();
         }
         out.removeIf(i -> i < 0 || i >= size);
         if (out.isEmpty()) {
            this.add(path, "has no slots inside the GUI", "using 0-" + (Math.min(size, 45) - 1));
            for (int i = 0; i < Math.min(size, 45); i++) {
               out.add(i);
            }
         }
         return List.copyOf(new java.util.LinkedHashSet<>(out));
      }
   }

   // ------------------------------------------------------------------ getters

   public EloSettings elo() {
      return this.elo;
   }

   public Pvp pvp() {
      return this.pvp;
   }

   public AntiFarmSettings antiFarm() {
      return this.antiFarm;
   }

   public long claimCooldownMillis() {
      return this.claimCooldownMillis;
   }

   public FullInventory fullInventory() {
      return this.fullInventory;
   }

   public TierLadder ladder() {
      return this.ladder;
   }

   public RewardDef reward(String id) {
      return this.rewards.get(id);
   }

   public String unrankedName() {
      return this.unrankedName;
   }

   public String unrankedColor() {
      return this.unrankedColor;
   }

   public DisplayedTier displayedTier() {
      return this.displayedTier;
   }

   public LuckPerms luckPerms() {
      return this.luckPerms;
   }

   public RankUp rankUp() {
      return this.rankUp;
   }

   public long leaderboardRefreshMillis() {
      return this.leaderboardRefreshMillis;
   }

   public int leaderboardMaxEntries() {
      return this.leaderboardMaxEntries;
   }

   public Gui gui() {
      return this.gui;
   }

   public Permissions permissions() {
      return this.permissions;
   }

   public String databaseFile() {
      return this.databaseFile;
   }

   /** The raw config, for messages and GUI text. */
   public FileConfiguration raw() {
      return this.raw;
   }

   public List<String> problems() {
      return this.problems;
   }
}
