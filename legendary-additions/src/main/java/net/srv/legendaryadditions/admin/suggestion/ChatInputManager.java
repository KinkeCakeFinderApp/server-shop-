package net.srv.legendaryadditions.admin.suggestion;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Captures the next chat message of a player who clicked "Submit Suggestion". The chat event is
 * asynchronous, so the text is handed to the player's own EntityScheduler before any game state or
 * GUI is touched. Sessions end on cancel, timeout or disconnect.
 */
public final class ChatInputManager implements Listener {
   private final Plugin plugin;
   private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();
   private final Map<UUID, BiConsumer<Player, String>> handlers = new ConcurrentHashMap<>();

   public ChatInputManager(Plugin plugin) {
      this.plugin = plugin;
   }

   /** Must be called on the player's thread. {@code handler} runs on the player's thread with the raw text. */
   public void begin(Player player, int timeoutSeconds, BiConsumer<Player, String> handler) {
      this.begin(player, timeoutSeconds, "Suggestion input timed out. Open /suggestions to try again.", handler);
   }

   /**
    * One-shot question: the next chat line ends the session and goes to {@code handler}, except
    * "cancel", which just ends it. Must be called on the player's thread.
    */
   public void ask(Player player, int timeoutSeconds, String timeoutMessage, BiConsumer<Player, String> handler) {
      this.begin(player, timeoutSeconds, timeoutMessage, (p, raw) -> {
         this.end(p.getUniqueId());
         if (raw.trim().equalsIgnoreCase("cancel")) {
            Messages.info(p, "Cancelled.");
            return;
         }
         handler.accept(p, raw.trim());
      });
   }

   public void begin(Player player, int timeoutSeconds, String timeoutMessage, BiConsumer<Player, String> handler) {
      UUID id = player.getUniqueId();
      Object token = new Object();
      this.sessions.put(id, token);
      this.handlers.put(id, handler);
      player.getScheduler().runDelayed(this.plugin, task -> {
         if (this.sessions.remove(id, token)) {
            this.handlers.remove(id);
            Messages.info(player, timeoutMessage);
         }
      }, null, timeoutSeconds * 20L);
   }

   public boolean isWaiting(UUID player) {
      return this.sessions.containsKey(player);
   }

   public void end(UUID player) {
      this.sessions.remove(player);
      this.handlers.remove(player);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      UUID id = player.getUniqueId();
      Object token = this.sessions.get(id);
      if (token == null) {
         return;
      }
      // Never broadcast the suggestion text to public chat.
      event.setCancelled(true);
      String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
      player.getScheduler().run(this.plugin, task -> {
         if (this.sessions.get(id) != token) {
            return;
         }
         BiConsumer<Player, String> handler = this.handlers.get(id);
         if (handler != null) {
            handler.accept(player, raw);
         }
      }, null);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.end(event.getPlayer().getUniqueId());
   }
}
