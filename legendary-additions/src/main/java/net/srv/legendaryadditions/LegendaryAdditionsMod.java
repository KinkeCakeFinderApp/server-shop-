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
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendaryAdditionsMod extends JavaPlugin {
   public static JavaPlugin plugin;
   public static Server server;

   private volatile AdminSettings settings;
   private SuggestionService suggestions;

   @Override
   public void onEnable() {
      plugin = this;
      server = this.getServer();

      this.saveDefaultConfig();
      this.settings = AdminSettings.load(this.getConfig());
      Fx.configure(this.settings.rodParticles(), this.settings.rodSounds());

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

      RodRegistry registry = new RodRegistry(authenticator);
      RodEffects effects = new RodEffects(this);
      AdminDimension dimension = new AdminDimension(this, () -> this.settings);

      SuggestionAccess access = new SuggestionAccess(() -> this.settings);
      SuggestionFormat format = new SuggestionFormat(() -> this.settings);
      ClickGuard guard = new ClickGuard();
      ChatInputManager chat = new ChatInputManager(this);
      SuggestionGui suggestionGui = new SuggestionGui(this, this.suggestions, access, format, guard, chat);
      AdminSuggestionGui adminGui = new AdminSuggestionGui(this, this.suggestions, access, format, guard, suggestionGui);

      PluginManager pm = this.getServer().getPluginManager();
      pm.registerEvents(new RiftcasterListener(), this);
      pm.registerEvents(new DrillListener(), this);
      pm.registerEvents(new PaxelListener(this), this);
      pm.registerEvents(new RodListener(this, registry, effects, () -> this.settings), this);
      pm.registerEvents(new ExplosionGuard(), this);
      pm.registerEvents(new MenuListener(this, guard), this);
      pm.registerEvents(chat, this);

      this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
         var commands = event.registrar();
         commands.register("legendary_additions", "Give legendary items to players.", new LegendaryAdditionsCommand());
         commands.register("admin", "Enter the Admin dimension, or /admin return to go back.", new AdminCommand(dimension));
         commands.register("suggestions", "Browse, vote on and submit suggestions.", new SuggestionsCommand(suggestionGui, access));
         commands.register("suggestionadmin", "Review and manage suggestions (admins only).", new SuggestionAdminCommand(adminGui));
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
   }
}
