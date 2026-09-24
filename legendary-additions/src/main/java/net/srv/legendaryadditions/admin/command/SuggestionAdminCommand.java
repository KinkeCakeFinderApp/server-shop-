package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionStatus;
import net.srv.legendaryadditions.admin.suggestion.gui.AdminSuggestionGui;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /suggestionadmin opens the admin suggestion backend. The GUI re-checks the permission on every click. */
public final class SuggestionAdminCommand implements BasicCommand {
   private final AdminSuggestionGui adminGui;

   public SuggestionAdminCommand(AdminSuggestionGui adminGui) {
      this.adminGui = adminGui;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      if (!(source.getExecutor() instanceof Player player)) {
         Messages.error(source.getSender(), "Only players can open the suggestion admin GUI.");
         return;
      }
      if (!SuggestionAccess.isAdmin(player)) {
         Messages.error(player, Messages.NO_PERMISSION);
         return;
      }
      if (args.length != 0) {
         Messages.error(player, "Usage: /suggestionadmin");
         return;
      }
      this.adminGui.openList(player, SuggestionStatus.PENDING, 0);
   }

   @Override
   public boolean canUse(CommandSender sender) {
      return !(sender instanceof Player player) || SuggestionAccess.isAdmin(player);
   }
}
