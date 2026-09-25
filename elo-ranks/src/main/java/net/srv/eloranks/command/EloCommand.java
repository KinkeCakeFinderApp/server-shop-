package net.srv.eloranks.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.srv.eloranks.EloRanksPlugin;
import net.srv.eloranks.config.Messages;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.rank.RankFormat;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /elo - your ELO information. Admin sub-commands: set, add, remove, reset, resetclaims,
 * resetprogress, wipe, forcetier, reload.
 */
public final class EloCommand implements BasicCommand {
   private static final List<String> SUBCOMMANDS = List.of("set", "add", "remove", "reset", "resetclaims",
         "resetprogress", "wipe", "forcetier", "reload");

   private final EloRanksPlugin plugin;

   public EloCommand(EloRanksPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public boolean canUse(CommandSender sender) {
      return true;
   }

   private Messages messages() {
      return this.plugin.messages();
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      CommandSender sender = source.getSender();
      if (args.length == 0 || !SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
         if (args.length > 0 && this.isAnyAdmin(sender)) {
            this.messages().send(sender, "admin-usage");
            return;
         }
         this.info(source);
         return;
      }
      String sub = args[0].toLowerCase(Locale.ROOT);
      Settings.Permissions perms = this.plugin.settings().permissions();
      String node = switch (sub) {
         case "set", "add", "remove" -> perms.set();
         case "reset", "resetprogress", "wipe" -> perms.reset();
         case "resetclaims" -> perms.claimReset();
         case "forcetier" -> perms.forceTier();
         default -> perms.reload();
      };
      if (!sender.hasPermission(node) && !sender.hasPermission(perms.admin())) {
         this.messages().send(sender, "no-permission");
         return;
      }
      if (sub.equals("reload")) {
         this.messages().send(sender, this.plugin.reload() ? "reload-complete" : "reload-failed");
         return;
      }
      int needed = switch (sub) {
         case "set", "add", "remove", "forcetier" -> 3;
         default -> 2;
      };
      if (args.length != needed) {
         this.messages().send(sender, "admin-usage");
         return;
      }
      String name = args[1];
      Long amount = null;
      Tier tier = null;
      if (sub.equals("forcetier")) {
         Optional<Tier> parsed = this.plugin.settings().ladder().parse(args[2]);
         if (parsed.isEmpty()) {
            String tiers = String.join(", ", this.plugin.settings().ladder().ascending().stream()
                  .map(t -> String.valueOf(t.number())).toList());
            this.messages().send(sender, "invalid-tier", new Placeholders().with("value", args[2]).with("tiers", tiers));
            return;
         }
         tier = parsed.get();
      } else if (needed == 3) {
         try {
            amount = Long.parseLong(args[2]);
            if (amount < 0 || amount > Integer.MAX_VALUE) {
               throw new NumberFormatException();
            }
         } catch (NumberFormatException ex) {
            this.messages().send(sender, "invalid-number", Placeholders.of("value", args[2]));
            return;
         }
      }
      long value = amount == null ? 0 : amount;
      Tier forced = tier;
      this.resolve(name).thenAccept(found -> {
         if (found.isEmpty()) {
            this.messages().send(sender, "invalid-player", Placeholders.of("player", name));
            return;
         }
         UUID id = found.get();
         switch (sub) {
            case "set" -> this.reportElo(sender, this.plugin.ranks().setElo(id, value), u -> u.after());
            case "add" -> this.reportElo(sender, this.plugin.ranks().addElo(id, value), u -> u.after());
            case "remove" -> this.reportElo(sender, this.plugin.ranks().addElo(id, -value), u -> u.after());
            case "reset" -> this.reportElo(sender, this.plugin.ranks().setElo(id, this.plugin.settings().elo().startingElo()),
                  u -> u.after());
            case "forcetier" -> this.reportElo(sender, this.plugin.ranks().setElo(id, forced.elo()), u -> u.after());
            case "resetclaims" -> this.report(sender, this.plugin.ranks().resetClaims(id), "admin-claims-reset", name);
            case "resetprogress" -> this.report(sender, this.plugin.ranks().resetProgress(id), "admin-progress-reset", name);
            case "wipe" -> this.report(sender, this.plugin.ranks().wipe(id), "admin-wiped", name);
            default -> { }
         }
      }).exceptionally(ex -> {
         this.messages().send(sender, "database-error");
         return null;
      });
   }

   private <T> void reportElo(CommandSender sender, CompletableFuture<T> future, Function<T, PlayerRecord> record) {
      future.whenComplete((result, error) -> {
         if (error != null) {
            this.messages().send(sender, "database-error");
            return;
         }
         PlayerRecord after = record.apply(result);
         Settings settings = this.plugin.settings();
         Optional<Tier> tier = RankFormat.displayed(settings, after);
         this.messages().send(sender, "admin-elo-set", new Placeholders().with("player", after.name()).with("elo", after.elo())
               .with("tier_name", RankFormat.name(settings, tier)).with("tier_color", RankFormat.color(settings, tier)));
         this.plugin.getLogger().info(sender.getName() + " changed the ELO of " + after.name() + " to " + after.elo() + ".");
      });
   }

   private void report(CommandSender sender, CompletableFuture<?> future, String message, String name) {
      future.whenComplete((result, error) -> {
         if (error != null) {
            this.messages().send(sender, "database-error");
            return;
         }
         this.messages().send(sender, message, Placeholders.of("player", name));
         this.plugin.getLogger().info(sender.getName() + " ran " + message + " for " + name + ".");
      });
   }

   /** A saved player by name (they must have joined once), preferring the exact online player. */
   private CompletableFuture<Optional<UUID>> resolve(String name) {
      Player online = Bukkit.getPlayerExact(name);
      if (online != null) {
         UUID id = online.getUniqueId();
         return this.plugin.data().submit(logic -> logic.db().load(id).map(PlayerRecord::uuid));
      }
      return this.plugin.data().submit(logic -> logic.db().findByName(name));
   }

   private void info(CommandSourceStack source) {
      if (!(source.getExecutor() instanceof Player player)) {
         this.messages().send(source.getSender(), this.isAnyAdmin(source.getSender()) ? "admin-usage" : "players-only");
         return;
      }
      PlayerRecord record = this.plugin.data().cached(player.getUniqueId());
      if (record == null) {
         this.messages().send(player, "data-loading");
         return;
      }
      Settings settings = this.plugin.settings();
      boolean max = settings.ladder().nextAfter(record.elo()).isEmpty();
      this.messages().send(player, max ? "info-max-tier" : "info", RankFormat.of(settings, record, System.currentTimeMillis()));
   }

   private boolean isAnyAdmin(CommandSender sender) {
      Settings.Permissions p = this.plugin.settings().permissions();
      for (String node : List.of(p.admin(), p.set(), p.reset(), p.reload(), p.claimReset(), p.forceTier())) {
         if (sender.hasPermission(node)) {
            return true;
         }
      }
      return false;
   }

   @Override
   public Collection<String> suggest(CommandSourceStack source, String[] args) {
      if (!this.isAnyAdmin(source.getSender())) {
         return List.of();
      }
      if (args.length <= 1) {
         String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
         return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
      }
      String sub = args[0].toLowerCase(Locale.ROOT);
      if (args.length == 2 && !sub.equals("reload")) {
         String prefix = args[1].toLowerCase(Locale.ROOT);
         List<String> names = new ArrayList<>();
         for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
               names.add(p.getName());
            }
         }
         return names;
      }
      if (args.length == 3 && sub.equals("forcetier")) {
         return this.plugin.settings().ladder().ascending().stream().map(t -> String.valueOf(t.number())).toList();
      }
      return List.of();
   }
}
