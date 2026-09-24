package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionCategory;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionStatus;
import net.srv.legendaryadditions.admin.suggestion.gui.AdminSuggestionGui;
import net.srv.legendaryadditions.admin.suggestion.gui.SuggestionGui;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.entity.Player;

/** /suggestions opens the public GUI; /suggestions admin opens the admin backend (permission checked). */
public final class SuggestionsCommand implements BasicCommand {
   private final SuggestionGui gui;
   private final AdminSuggestionGui adminGui;
   private final SuggestionAccess access;

   public SuggestionsCommand(SuggestionGui gui, AdminSuggestionGui adminGui, SuggestionAccess access) {
      this.gui = gui;
      this.adminGui = adminGui;
      this.access = access;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      if (!(source.getExecutor() instanceof Player player)) {
         Messages.error(source.getSender(), "Only players can open the suggestions GUI.");
         return;
      }
      if (args.length == 0) {
         this.gui.openList(player, SuggestionCategory.LEGENDARY, 0);
      } else if (args.length == 1 && args[0].equalsIgnoreCase("admin")) {
         if (!SuggestionAccess.isAdmin(player)) {
            Messages.error(player, Messages.NO_PERMISSION);
            return;
         }
         this.adminGui.openList(player, SuggestionStatus.PENDING, 0);
      } else {
         Messages.error(player, "Usage: /suggestions" + (SuggestionAccess.isAdmin(player) ? " [admin]" : ""));
      }
   }

   @Override
   public Collection<String> suggest(CommandSourceStack source, String[] args) {
      if (args.length <= 1 && source.getExecutor() instanceof Player player && SuggestionAccess.isAdmin(player)) {
         String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
         if ("admin".startsWith(prefix)) {
            return List.of("admin");
         }
      }
      return List.of();
   }

   @Override
   public boolean canUse(org.bukkit.command.CommandSender sender) {
      return !(sender instanceof Player player) || this.access.canUse(player);
   }
}
