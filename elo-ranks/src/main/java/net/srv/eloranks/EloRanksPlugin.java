package net.srv.eloranks;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import net.srv.eloranks.command.EloCommand;
import net.srv.eloranks.command.GuiCommand;
import net.srv.eloranks.config.Messages;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.data.EloDatabase;
import net.srv.eloranks.gui.ClickGuard;
import net.srv.eloranks.gui.Guis;
import net.srv.eloranks.gui.MenuListener;
import net.srv.eloranks.listener.CombatListener;
import net.srv.eloranks.listener.PlayerListener;
import net.srv.eloranks.luckperms.LuckPermsDisplay;
import net.srv.eloranks.luckperms.RankDisplay;
import net.srv.eloranks.service.ClaimService;
import net.srv.eloranks.service.DataService;
import net.srv.eloranks.service.LeaderboardCache;
import net.srv.eloranks.service.RankService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** EloRanks: PvP ELO, Tier 6 -> Tier 1, claimable rank kits, a leaderboard and LuckPerms rank display. */
public final class EloRanksPlugin extends JavaPlugin {
   private volatile Settings settings;
   private volatile Messages messages;
   private volatile RankDisplay display = RankDisplay.NONE;
   private DataService data;
   private RankService ranks;
   private ClaimService claims;
   private LeaderboardCache leaderboard;
   private Guis guis;
   private boolean luckPermsPresent;

   @Override
   public void onEnable() {
      this.saveDefaultConfig();
      Settings loaded;
      try {
         loaded = this.loadSettings();
      } catch (Exception ex) {
         this.getLogger().log(Level.SEVERE, "config.yml could not be read (" + ex.getMessage() + "). Fix the YAML "
               + "(or delete the file to get the default one) and restart. EloRanks is disabled.");
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }
      this.apply(loaded);

      try {
         File file = new File(this.getDataFolder(), loaded.databaseFile());
         this.data = new DataService(new EloDatabase("jdbc:sqlite:" + file.getAbsolutePath()), this.getLogger());
      } catch (Exception ex) {
         this.getLogger().log(Level.SEVERE, "Could not open the database " + loaded.databaseFile()
               + ". Rankings cannot be saved, so EloRanks is disabled. Check the file permissions and disk space.", ex);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }
      long history = Math.max(loaded.antiFarm().historyMillis(), 7L * 24 * 3_600_000);
      this.data.submit(logic -> logic.db().pruneKills(System.currentTimeMillis() - history));

      ClickGuard guard = new ClickGuard();
      this.ranks = new RankService(this);
      this.claims = new ClaimService(this, guard);
      this.leaderboard = new LeaderboardCache(this);
      this.guis = new Guis(this);
      this.hookLuckPerms();

      var pm = this.getServer().getPluginManager();
      pm.registerEvents(new MenuListener(this, guard), this);
      pm.registerEvents(new PlayerListener(this), this);
      pm.registerEvents(new CombatListener(this), this);

      this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
         var commands = event.registrar();
         commands.register("rank", "Open the ranking menu.", List.of("ranks"),
               new GuiCommand(this, (plugin, player) -> plugin.guis().openMain(player)));
         commands.register("kits", "Open the rank kits.", List.of("rankkits"),
               new GuiCommand(this, (plugin, player) -> plugin.guis().openKits(player)));
         commands.register("leaderboard", "Open the ELO leaderboard.", List.of("elotop"),
               new GuiCommand(this, (plugin, player) -> plugin.guis().openLeaderboard(player, 0)));
         commands.register("elo", "Your ELO, and admin ELO commands.", new EloCommand(this));
      });

      this.leaderboard.start();
      // /reload or a plugin manager: players already online.
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.ranks.join(player);
      }
      this.getLogger().info("EloRanks enabled: " + loaded.ladder().ascending().size() + " tiers, claim cooldown "
            + net.srv.eloranks.util.Durations.format(loaded.claimCooldownMillis()) + ".");
   }

   @Override
   public void onDisable() {
      if (this.leaderboard != null) {
         this.leaderboard.stop();
      }
      if (this.data != null) {
         this.data.shutdown();
      }
   }

   /** Reads config.yml with the bundled config as defaults. @throws Exception on invalid YAML */
   private Settings loadSettings() throws Exception {
      YamlConfiguration config = new YamlConfiguration();
      try {
         config.load(new File(this.getDataFolder(), "config.yml"));
      } catch (InvalidConfigurationException ex) {
         throw new Exception("invalid YAML: " + ex.getMessage(), ex);
      }
      var resource = this.getResource("config.yml");
      if (resource != null) {
         try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
         }
      }
      return Settings.load(config);
   }

   private void apply(Settings loaded) {
      for (String problem : loaded.problems()) {
         this.getLogger().warning("config.yml " + problem);
      }
      this.settings = loaded;
      this.messages = new Messages(loaded);
   }

   /** /elo reload. @return false if config.yml has invalid YAML (the old config stays) */
   public boolean reload() {
      Settings loaded;
      try {
         loaded = this.loadSettings();
      } catch (Exception ex) {
         this.getLogger().log(Level.SEVERE, "config.yml could not be reloaded: " + ex.getMessage());
         return false;
      }
      String oldDb = this.settings.databaseFile();
      this.apply(loaded);
      if (!oldDb.equals(loaded.databaseFile())) {
         this.getLogger().warning("database.file changed; restart the server to use the new database file.");
      }
      this.hookLuckPerms();
      this.leaderboard.start();
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.ranks.join(player);
      }
      return true;
   }

   private void hookLuckPerms() {
      if (this.settings.luckPerms().mode() == Settings.LuckPermsMode.NONE) {
         this.display = RankDisplay.NONE;
         return;
      }
      if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
         if (!this.luckPermsPresent) {
            this.getLogger().warning("LuckPerms is not installed: ranks work, but the tier cannot be shown next to "
                  + "player names. Install LuckPerms, or set luckperms.mode: none to hide this warning.");
         }
         this.display = RankDisplay.NONE;
         return;
      }
      try {
         LuckPermsDisplay lp = new LuckPermsDisplay(this::settings, this.getLogger());
         lp.checkGroups();
         this.display = lp;
         this.luckPermsPresent = true;
         this.getLogger().info("LuckPerms found: tiers are shown with a LuckPerms " + this.settings.luckPerms().mode()
               .name().toLowerCase(java.util.Locale.ROOT) + ".");
      } catch (LinkageError | RuntimeException ex) {
         this.getLogger().log(Level.WARNING, "LuckPerms is installed but its API could not be used; the tier is not "
               + "shown next to player names.", ex);
         this.display = RankDisplay.NONE;
      }
   }

   public Settings settings() {
      return this.settings;
   }

   public Messages messages() {
      return this.messages;
   }

   public RankDisplay display() {
      return this.display;
   }

   public DataService data() {
      return this.data;
   }

   public RankService ranks() {
      return this.ranks;
   }

   public ClaimService claims() {
      return this.claims;
   }

   public LeaderboardCache leaderboard() {
      return this.leaderboard;
   }

   public Guis guis() {
      return this.guis;
   }
}
