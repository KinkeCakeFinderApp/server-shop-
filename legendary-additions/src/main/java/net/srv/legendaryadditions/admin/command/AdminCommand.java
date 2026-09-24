package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.srv.legendaryadditions.admin.dimension.AdminDimension;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.entity.Player;

/** /admin enters the Admin dimension; /admin return goes back. */
public final class AdminCommand implements BasicCommand {
   public static final String PERMISSION = "admindimension.admin";

   private final AdminDimension dimension;

   public AdminCommand(AdminDimension dimension) {
      this.dimension = dimension;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      if (!source.getSender().hasPermission(PERMISSION)) {
         Messages.error(source.getSender(), Messages.NO_PERMISSION);
         return;
      }
      if (!(source.getExecutor() instanceof Player player)) {
         Messages.error(source.getSender(), "Only players can use /admin.");
         return;
      }
      if (args.length == 0) {
         this.dimension.enter(player);
      } else if (args.length == 1 && args[0].equalsIgnoreCase("return")) {
         this.dimension.leave(player);
      } else {
         Messages.error(player, "Usage: /admin [return]");
      }
   }

   @Override
   public Collection<String> suggest(CommandSourceStack source, String[] args) {
      if (args.length == 0) {
         return List.of("return");
      }
      if (args.length == 1 && "return".startsWith(args[0].toLowerCase(Locale.ROOT))) {
         return List.of("return");
      }
      return List.of();
   }

   @Override
   public String permission() {
      return PERMISSION;
   }
}
