package net.srv.eloranks.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Config text: &amp; colour codes, &amp;#RRGGBB hex colours and {placeholders}. */
public final class Text {
   private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
         .character('&').hexColors().build();

   private Text() {
   }

   public static String fill(String template, Map<String, String> placeholders) {
      if (template == null) {
         return "";
      }
      String out = template;
      for (Map.Entry<String, String> entry : placeholders.entrySet()) {
         out = out.replace("{" + entry.getKey() + "}", entry.getValue());
      }
      return out;
   }

   /** Chat text. */
   public static Component chat(String template, Map<String, String> placeholders) {
      return LEGACY.deserialize(fill(template, placeholders));
   }

   /** Item names and lore: not italic unless the text says so. */
   public static Component item(String template, Map<String, String> placeholders) {
      return Component.text().decoration(TextDecoration.ITALIC, false)
            .append(LEGACY.deserialize(fill(template, placeholders))).build();
   }

   /**
    * Lore lines. A line that is exactly a multi-line placeholder (e.g. {reward_description}) is
    * replaced by every line of that value.
    */
   public static List<Component> lore(List<String> templates, Map<String, String> placeholders,
                                      Map<String, List<String>> multiLine) {
      List<Component> out = new ArrayList<>();
      for (String line : templates) {
         String key = line.trim();
         if (key.startsWith("{") && key.endsWith("}") && multiLine.containsKey(key.substring(1, key.length() - 1))) {
            for (String sub : multiLine.get(key.substring(1, key.length() - 1))) {
               out.add(item(sub, placeholders));
            }
            continue;
         }
         out.add(item(line, placeholders));
      }
      return out;
   }
}
