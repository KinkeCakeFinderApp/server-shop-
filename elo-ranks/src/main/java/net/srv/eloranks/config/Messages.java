package net.srv.eloranks.config;

import java.util.List;
import net.srv.eloranks.util.Placeholders;
import net.srv.eloranks.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** Sends the configurable messages under messages:. Every message may be one line or a list. */
public final class Messages {
   private final FileConfiguration config;

   public Messages(Settings settings) {
      this.config = settings.raw();
   }

   public String raw(String key) {
      return this.config.getString("messages." + key, "");
   }

   public List<String> lines(String key) {
      String path = "messages." + key;
      if (this.config.isList(path)) {
         return this.config.getStringList(path);
      }
      String line = this.config.getString(path, "");
      return line.isEmpty() ? List.of() : List.of(line);
   }

   public void send(CommandSender to, String key, Placeholders placeholders) {
      this.sendLines(to, this.lines(key), placeholders);
   }

   public void send(CommandSender to, String key) {
      this.send(to, key, new Placeholders());
   }

   public void sendLines(CommandSender to, List<String> lines, Placeholders placeholders) {
      Placeholders all = new Placeholders().with("prefix", this.raw("prefix")).with(placeholders);
      for (String line : lines) {
         to.sendMessage(Text.chat(line, all.map()));
      }
   }
}
