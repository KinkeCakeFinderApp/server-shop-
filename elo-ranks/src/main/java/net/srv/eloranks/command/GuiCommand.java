package net.srv.eloranks.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.function.BiConsumer;
import net.srv.eloranks.EloRanksPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /rank, /kits and /leaderboard: open a GUI. No permission needed. */
public final class GuiCommand implements BasicCommand {
   private final EloRanksPlugin plugin;
   private final BiConsumer<EloRanksPlugin, Player> open;

   public GuiCommand(EloRanksPlugin plugin, BiConsumer<EloRanksPlugin, Player> open) {
      this.plugin = plugin;
      this.open = open;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      if (!(source.getExecutor() instanceof Player player)) {
         this.plugin.messages().send(source.getSender(), "players-only");
         return;
      }
      player.getScheduler().run(this.plugin, task -> this.open.accept(this.plugin, player), null);
   }

   @Override
   public boolean canUse(CommandSender sender) {
      return true;
   }
}
