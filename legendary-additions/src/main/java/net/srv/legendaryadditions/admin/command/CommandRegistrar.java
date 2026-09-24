package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.Commands;
import net.srv.legendaryadditions.admin.rod.RodKind;
import net.srv.legendaryadditions.admin.rod.RodRegistry;
import org.bukkit.plugin.Plugin;

/**
 * Registers the exact command set: /admin, /stab, /stabshot, /nuke, /nukeshot, /lawnuke,
 * /lawnukeshot, /withernuke, /withernukeshot, /wolfrod (and "/wolfrod shot"), /arrowrod,
 * /arrowrodshot, /teleportshot and /suggestions. There is deliberately no /teleport.
 */
public final class CommandRegistrar {
   private CommandRegistrar() {
   }

   public static void registerRods(Commands commands, Plugin plugin, RodRegistry registry) {
      for (RodKind kind : RodKind.values()) {
         if (kind.singleCommand() != null) {
            RodGiveCommand.Mode mode = kind.reusableCommand() == null
                  ? RodGiveCommand.Mode.SUPPLY_WITH_SHOT_SUBCOMMAND
                  : RodGiveCommand.Mode.SINGLE_USE_SUPPLY;
            register(commands, new RodGiveCommand(plugin, registry, kind, mode, kind.singleCommand()));
         }
         if (kind.reusableCommand() != null) {
            register(commands, new RodGiveCommand(plugin, registry, kind, RodGiveCommand.Mode.REUSABLE, kind.reusableCommand()));
         }
      }
   }

   private static void register(Commands commands, RodGiveCommand command) {
      commands.register(command.label(), command.description(), command);
   }
}
