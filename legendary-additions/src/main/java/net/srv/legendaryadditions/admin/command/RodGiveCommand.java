package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.srv.legendaryadditions.admin.rod.RodKind;
import net.srv.legendaryadditions.admin.rod.RodRegistry;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * /{rod} [player] gives a shulker of 27 single-use rods; /{rod}shot [player] gives one reusable rod.
 * Items are always created and handed over on the receiving player's own thread.
 */
public final class RodGiveCommand implements BasicCommand {
   private final Plugin plugin;
   private final RodRegistry registry;
   private final RodKind kind;
   private final boolean reusable;

   public RodGiveCommand(Plugin plugin, RodRegistry registry, RodKind kind, boolean reusable) {
      this.plugin = plugin;
      this.registry = registry;
      this.kind = kind;
      this.reusable = reusable;
   }

   public String label() {
      return this.reusable ? this.kind.command() + "shot" : this.kind.command();
   }

   public String description() {
      return this.reusable
            ? "Gives a reusable " + this.kind.displayName() + "."
            : "Gives a shulker of 27 single-use " + this.kind.displayName() + "s.";
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      CommandSender sender = source.getSender();
      if (!sender.hasPermission(this.kind.permission())) {
         Messages.error(sender, Messages.NO_PERMISSION);
         return;
      }
      if (args.length > 1) {
         Messages.error(sender, "Usage: /" + this.label() + " [player]");
         return;
      }
      Player target;
      if (args.length == 1) {
         target = Bukkit.getPlayerExact(args[0]);
         if (target == null) {
            Messages.error(sender, "Player '" + args[0] + "' is not online.");
            return;
         }
      } else if (source.getExecutor() instanceof Player self) {
         target = self;
      } else {
         Messages.error(sender, "Usage: /" + this.label() + " <player>");
         return;
      }

      String itemName = this.reusable ? "a reusable " + this.kind.displayName() : "a shulker of " + this.kind.displayName() + "s";
      target.getScheduler().run(this.plugin, task -> {
         ItemStack item = this.reusable ? this.registry.createRod(this.kind, true) : this.registry.createSingleUseShulker(this.kind);
         Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
         leftover.values().forEach(stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));
         Messages.success(target, "You received " + itemName + ".");
         if (!sender.equals(target)) {
            Messages.success(sender, "Gave " + target.getName() + " " + itemName + ".");
         }
      }, () -> Messages.error(sender, target.getName() + " left before the item could be given."));
   }

   @Override
   public Collection<String> suggest(CommandSourceStack source, String[] args) {
      if (args.length > 1) {
         return List.of();
      }
      String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
      return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .toList();
   }

   @Override
   public String permission() {
      return this.kind.permission();
   }
}
