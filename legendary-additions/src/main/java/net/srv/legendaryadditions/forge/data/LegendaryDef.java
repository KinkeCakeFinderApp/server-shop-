package net.srv.legendaryadditions.forge.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One admin-made legendary item as stored in legendaries.db. Plain strings only (material key,
 * enchantment keys, ability ids) so the storage layer never needs a running server.
 *
 * @param enchants  enchantment key (e.g. "minecraft:sharpness") to level
 * @param abilities ability id (e.g. "VEIN_MINE") to level
 */
public record LegendaryDef(
      String id,
      String name,
      String material,
      List<String> lore,
      Map<String, Integer> enchants,
      Map<String, Integer> abilities,
      boolean unbreakable,
      boolean glow,
      Integer modelData,
      String createdBy,
      long createdAt,
      long updatedAt) {

   public static final Pattern ID = Pattern.compile("[a-z0-9_]{2,32}");

   public LegendaryDef {
      lore = List.copyOf(lore);
      enchants = Map.copyOf(enchants);
      abilities = Map.copyOf(abilities);
   }

   public static boolean validId(String id) {
      return id != null && ID.matcher(id).matches();
   }

   /** "a=1,b=2" in insertion order; keys never contain '=' or ','. */
   public static String encode(Map<String, Integer> map) {
      StringBuilder out = new StringBuilder();
      map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
         if (!out.isEmpty()) {
            out.append(',');
         }
         out.append(e.getKey()).append('=').append(e.getValue());
      });
      return out.toString();
   }

   public static Map<String, Integer> decode(String text) {
      Map<String, Integer> map = new LinkedHashMap<>();
      if (text == null || text.isBlank()) {
         return map;
      }
      for (String part : text.split(",")) {
         int eq = part.indexOf('=');
         if (eq <= 0) {
            continue;
         }
         try {
            map.put(part.substring(0, eq).trim(), Integer.parseInt(part.substring(eq + 1).trim()));
         } catch (NumberFormatException ignored) {
            // Skip a damaged entry rather than losing the whole item.
         }
      }
      return map;
   }
}
