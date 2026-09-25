package net.srv.legendaryadditions;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.logging.Level;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.SelfTest;
import net.srv.legendaryadditions.admin.command.AdminCommand;
import net.srv.legendaryadditions.admin.command.CommandRegistrar;
import net.srv.legendaryadditions.admin.command.SuggestionAdminCommand;
import net.srv.legendaryadditions.admin.command.SuggestionsCommand;
import net.srv.legendaryadditions.admin.dimension.AdminDimension;
import net.srv.legendaryadditions.admin.effect.ExplosionGuard;
import net.srv.legendaryadditions.admin.effect.Fx;
import net.srv.legendaryadditions.admin.effect.RodEffects;
import net.srv.legendaryadditions.admin.rod.RodAuthenticator;
import net.srv.legendaryadditions.admin.rod.RodListener;
import net.srv.legendaryadditions.admin.rod.RodRegistry;
import net.srv.legendaryadditions.admin.suggestion.ChatInputManager;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.SuggestionService;
import net.srv.legendaryadditions.admin.suggestion.gui.AdminSuggestionGui;
import net.srv.legendaryadditions.admin.suggestion.gui.ClickGuard;
import net.srv.legendaryadditions.admin.suggestion.gui.MenuListener;
import net.srv.legendaryadditions.admin.suggestion.gui.SuggestionFormat;
import net.srv.legendaryadditions.admin.suggestion.gui.SuggestionGui;
import net.srv.legendaryadditions.custom.DrillListener;
import net.srv.legendaryadditions.custom.LegendaryAdditionsCommand;
import net.srv.legendaryadditions.custom.PaxelListener;
import net.srv.legendaryadditions.custom.RiftcasterListener;
import net.srv.legendaryadditions.forge.AbilityListener;
import net.srv.legendaryadditions.forge.LegendaryItems;
import net.srv.legendaryadditions.forge.LegendaryService;
import net.srv.legendaryadditions.forge.gui.LegendaryCreatorGui;
import net.srv.legendaryadditions.shopadmin.FoliaShopStore;
import net.srv.legendaryadditions.shopadmin.ShopAdminCommand;
import net.srv.legendaryadditions.shopadmin.ShopBuyCommand;
import net.srv.legendaryadditions.shopadmin.ShopAdminGui;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendaryAdditionsMod extends JavaPlugin {
   public static JavaPlugin plugin;
   public static Server server;

   private volatile AdminSettings settings;
   private SuggestionService suggestions;
   private LegendaryService legendaries;
   private FoliaShopStore shopStore;

   @Override
   public void onEnable() {
      plugin = this;
      server = this.getServer();

      this.saveDefaultConfig();
      this.migrateConfig();
      this.settings = AdminSettings.load(this.getConfig());
      Fx.configure(this.settings.rodParticles(), this.settings.rodSounds());
      this.checkTntLimit();

      RodAuthenticator authenticator;
      try {
         authenticator = RodAuthenticator.loadOrCreate(this.getDataPath().resolve("rod-secret.key"));
      } catch (Exception ex) {
         this.getLogger().log(Level.SEVERE, "Could not load rod-secret.key - disabling plugin", ex);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }
      try {
         this.suggestions = SuggestionService.open(this.getDataPath().resolve("suggestions.db"), this.getLogger());
      } catch (Exception ex) {
         this.getLogger().log(Level.SEVERE, "Could not open suggestions.db - disabling plugin", ex);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }

      try {
         this.legendaries = LegendaryService.open(this.getDataPath().resolve("legendaries.db"), this.getLogger());
      } catch (Exception ex) {
         this.getLogger().log(Level.SEVERE, "Could not open legendaries.db - disabling plugin", ex);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }
      LegendaryItems.init(this, this.legendaries);
      this.shopStore = new FoliaShopStore(this);
      FoliaShopStore store = this.shopStore;
      this.legendaries.onSaved(def -> store.syncLegendary(def).whenComplete((n, error) -> {
         if (error != null) {
            this.getLogger().log(Level.WARNING, "Could not update /shop entries for legendary " + def.id(), error);
         } else if (n > 0) {
            this.getLogger().info("Updated " + n + " /shop entr" + (n == 1 ? "y" : "ies") + " for legendary '" + def.id() + "'.");
         }
      }));

      RodRegistry registry = new RodRegistry(authenticator);
      RodEffects effects = new RodEffects(this);
      AdminDimension dimension = new AdminDimension(this, () -> this.settings);

      SuggestionAccess access = new SuggestionAccess(() -> this.settings);
      SuggestionFormat format = new SuggestionFormat(() -> this.settings);
      ClickGuard guard = new ClickGuard();
      ChatInputManager chat = new ChatInputManager(this);
      SuggestionGui suggestionGui = new SuggestionGui(this, this.suggestions, access, format, guard, chat);
      AdminSuggestionGui adminGui = new AdminSuggestionGui(this, this.suggestions, access, format, guard, suggestionGui);
      LegendaryCreatorGui creatorGui = new LegendaryCreatorGui(this, this.legendaries, chat);
      adminGui.setCreator(creatorGui);
      ShopAdminCommand shopAdmin = new ShopAdminCommand(new ShopAdminGui(this, this.shopStore, this.legendaries, chat));

      PluginManager pm = this.getServer().getPluginManager();
      pm.registerEvents(new RiftcasterListener(), this);
      pm.registerEvents(new DrillListener(), this);
      pm.registerEvents(new PaxelListener(this), this);
      pm.registerEvents(new RodListener(this, registry, effects, () -> this.settings), this);
      pm.registerEvents(new ExplosionGuard(), this);
      pm.registerEvents(net.srv.legendaryadditions.admin.effect.TntSpawner.listener(), this);
      pm.registerEvents(new MenuListener(this, guard), this);
      pm.registerEvents(chat, this);
      pm.registerEvents(new AbilityListener(this), this);
      pm.registerEvents(shopAdmin, this);

      this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
         var commands = event.registrar();
         commands.register("legendary_additions", "Give legendary items to players.", new LegendaryAdditionsCommand());
         commands.register("admin", "Enter the Admin dimension, or /admin return to go back.", new AdminCommand(dimension));
         commands.register("suggestions", "Browse, vote on and submit suggestions.", new SuggestionsCommand(suggestionGui, access));
         commands.register("suggestionadmin", "Review and manage suggestions (admins only).", new SuggestionAdminCommand(adminGui));
         commands.register("shopadmin", "Edit /shop: add legendaries and items, change prices (same as /shop admin).", shopAdmin);
         commands.register("la_shopbuy", "Used by FoliaShop for item prices (console only).", new ShopBuyCommand(this, this.shopStore));
         CommandRegistrar.registerRods(commands, this, registry);
      });

      if (SelfTest.enabled()) {
         new SelfTest(this).run(registry, dimension, this.suggestions);
      }

      if (dimension.world() == null) {
         this.getLogger().severe("Admin dimension '" + this.settings.adminWorldKey()
               + "' is not loaded. /admin will report it as unavailable. Check the startup log for datapack errors.");
      } else {
         this.getLogger().info("Admin dimension loaded as world '" + dimension.world().getName() + "'.");
      }
   }

   @Override
   public void onDisable() {
      // Folia/Paper cancel this plugin's scheduled tasks automatically; rod effects are temporary by design.
      if (this.suggestions != null) {
         this.suggestions.close();
      }
      if (this.legendaries != null) {
         this.legendaries.close();
      }
      if (this.shopStore != null) {
         this.shopStore.close();
      }
   }

   /**
    * Brings older config.yml files up to date. Version 2 gave Orbital Strike and Nuke a silent crater
    * and no warning delay (3.0.0 configs made them look like duds). Version 3 switches the Nuke to
    * the Unstable SMP TNT rings.
    */
   /**
    * spigot.yml's max-tnt-per-tick (default 100) freezes every TNT over the limit for that tick, so a
    * 1169-TNT nuke would hang in the air and go off in slow waves instead of all at once.
    */
   private void checkTntLimit() {
      int limit = net.srv.legendaryadditions.admin.effect.TntSpawner.loadLimit();
      int nuke = this.settings.nukeRings().ringCounts().stream().mapToInt(Integer::intValue).sum() + 1;
      if (limit > 0 && limit < nuke) {
         this.getLogger().info("spigot.yml max-tnt-per-tick is " + limit + " (the Nuke uses " + nuke + " TNT), so strikes"
               + " bigger than that use falling TNT blocks, which fall the same way and are not limited. Set"
               + " max-tnt-per-tick to " + Math.max(2000, nuke) + " or higher to use primed TNT for them.");
      }
   }

   private void migrateConfig() {
      var config = this.getConfig();
      int version = config.getInt("config-version", 1);
      if (version >= 4) {
         return;
      }
      if (version < 2) {
         config.set("orbital-strike.warning-time-ticks", 0);
         config.set("orbital-strike.destroy-blocks", true);
         config.set("orbital-strike.crater-radius", 4.0);
         config.set("orbital-strike.block-damage-power", null);
         config.set("nuke.warning-time-ticks", 0);
         config.set("nuke.destroy-blocks", true);
         config.set("nuke.crater-radius", 9.0);
         config.set("nuke.block-damage-power", null);
      }
      // 4: both strikes now summon real TNT like Unstable SMP / Orbital Strike Cannon.
      config.set("orbital-strike.style", "tnt");
      config.set("orbital-strike.tnt-spacing", 2);
      config.set("orbital-strike.tnt-per-layer", 1);
      config.set("orbital-strike.tnt-power", 4.0);
      config.set("orbital-strike.fuse-ticks", 20);
      config.set("orbital-strike.blocks-per-tick", 16);
      config.set("nuke.style", "rings");
      config.set("nuke.ring-radii", AdminSettings.DEFAULT_RING_RADII);
      config.set("nuke.ring-tnt-counts", AdminSettings.DEFAULT_RING_COUNTS);
      config.set("nuke.center-tnt", true);
      config.set("nuke.spawn-height", 70.0);
      config.set("nuke.fuse-ticks", 80);
      config.set("nuke.misalign", 0.0);
      config.set("nuke.tnt-power", 4.0);
      config.set("config-version", 4);
      this.saveConfig();
      this.getLogger().info("Updated config.yml: the Stab now drops a TNT column to bedrock (orbital-strike.style: tnt)"
            + " and the Nuke drops the Unstable SMP TNT rings (nuke.style: rings).");
   }
}
