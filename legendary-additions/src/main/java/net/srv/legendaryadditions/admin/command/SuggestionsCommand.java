package net.srv.legendaryadditions.admin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionCategory;
import net.srv.legendaryadditions.admin.suggestion.gui.SuggestionGui;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /suggestions opens the public GUI. The admin backend is only reachable through /suggestionadmin. */
public final class SuggestionsCommand implements BasicCommand {
   private final SuggestionGui gui;
   private final SuggestionAccess access;

   public SuggestionsCommand(SuggestionGui gui, SuggestionAccess access) {
      this.gui = gui;
      this.access = access;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      if (!(source.getExecutor() instanceof Player player)) {
         Messages.error(source.getSender(), "Only players can open the suggestions GUI.");
         return;
      }
      if (args.length != 0) {
         Messages.error(player, "Usage: /suggestions");
         return;
      }
      this.gui.openList(player, SuggestionCategory.LEGENDARY, 0);
   }

   @Override
   public boolean canUse(CommandSender sender) {
      return !(sender instanceof Player player) || this.access.canUse(player);
   }
}
