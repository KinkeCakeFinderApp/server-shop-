package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
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
 * Gives rods. Three shapes exist:
 * <ul>
 *    <li>{@code /stab [player]} - single-use supply: one shulker holding 27 signed single-use rods.</li>
 *    <li>{@code /stabshot [player]} - one reusable rod.</li>
 *    <li>{@code /wolfrod [player]} and {@code /wolfrod shot [player]} - both variants on one command.</li>
 * </ul>
 * Items are always created and inserted on the receiving player's own thread.
 */
public final class RodGiveCommand implements BasicCommand {
   public enum Mode {
      SINGLE_USE_SUPPLY,
      REUSABLE,
      /** Single-use supply by default, reusable rod when the first argument is "shot". */
      SUPPLY_WITH_SHOT_SUBCOMMAND
   }

   private final Plugin plugin;
   private final RodRegistry registry;
   private final RodKind kind;
   private final Mode mode;
   private final String label;

   public RodGiveCommand(Plugin plugin, RodRegistry registry, RodKind kind, Mode mode, String label) {
      this.plugin = plugin;
      this.registry = registry;
      this.kind = kind;
      this.mode = mode;
      this.label = label;
   }

   public String label() {
      return this.label;
   }

   public String description() {
      return switch (this.mode) {
         case SINGLE_USE_SUPPLY -> "Gives a shulker of 27 single-use " + this.kind.displayName() + "s.";
         case REUSABLE -> "Gives one reusable " + this.kind.displayName() + ".";
         case SUPPLY_WITH_SHOT_SUBCOMMAND -> "Gives a shulker of 27 single-use " + this.kind.displayName()
               + "s, or '" + this.label + " shot' for one reusable rod.";
      };
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      CommandSender sender = source.getSender();
      if (!sender.hasPermission(this.kind.permission())) {
         Messages.error(sender, Messages.NO_PERMISSION);
         return;
      }
      boolean reusable = this.mode == Mode.REUSABLE;
      int playerArg = 0;
      if (this.mode == Mode.SUPPLY_WITH_SHOT_SUBCOMMAND && args.length > 0 && args[0].equalsIgnoreCase("shot")) {
         reusable = true;
         playerArg = 1;
      }
      if (args.length > playerArg + 1) {
         Messages.error(sender, "Usage: " + this.usage());
         return;
      }
      Player target;
      if (args.length == playerArg + 1) {
         target = Bukkit.getPlayerExact(args[playerArg]);
         if (target == null) {
            Messages.error(sender, "Player '" + args[playerArg] + "' is not online.");
            return;
         }
      } else if (source.getExecutor() instanceof Player self) {
         target = self;
      } else {
         Messages.error(sender, "Console must name a player. Usage: " + this.usage());
         return;
      }

      boolean giveReusable = reusable;
      String itemName = giveReusable
            ? "a reusable " + this.kind.displayName()
            : "a shulker of " + RodRegistry.SHULKER_SLOTS + " single-use " + this.kind.displayName() + "s";
      target.getScheduler().run(this.plugin, task -> {
         ItemStack item = giveReusable ? this.registry.createRod(this.kind, true) : this.registry.createSingleUseShulker(this.kind);
         Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
         leftover.values().forEach(stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));
         Messages.success(target, "You received " + itemName + (leftover.isEmpty() ? "." : " (inventory full - dropped at your feet)."));
         if (!sender.equals(target)) {
            Messages.success(sender, "Gave " + target.getName() + " " + itemName + ".");
         }
      }, () -> Messages.error(sender, target.getName() + " left before the item could be given."));
   }

   private String usage() {
      return switch (this.mode) {
         case SUPPLY_WITH_SHOT_SUBCOMMAND -> "/" + this.label + " [shot] [player]";
         default -> "/" + this.label + " [player]";
      };
   }

   @Override
   public Collection<String> suggest(CommandSourceStack source, String[] args) {
      String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
      int index = Math.max(0, args.length - 1);
      List<String> options = new ArrayList<>();
      boolean shotGiven = this.mode == Mode.SUPPLY_WITH_SHOT_SUBCOMMAND && args.length > 1 && args[0].equalsIgnoreCase("shot");
      if (index == 0 && this.mode == Mode.SUPPLY_WITH_SHOT_SUBCOMMAND) {
         options.add("shot");
      }
      if (index == 0 || (index == 1 && shotGiven)) {
         Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
      }
      return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
   }

   @Override
   public String permission() {
      return this.kind.permission();
   }
}
