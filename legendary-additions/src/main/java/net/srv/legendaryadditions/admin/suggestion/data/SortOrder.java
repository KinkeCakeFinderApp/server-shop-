package net.srv.legendaryadditions.admin.suggestion.data;

import java.util.Locale;

public enum SortOrder {
   HIGHEST_VOTES("Highest votes", "votes DESC, s.created_at DESC, s.id DESC"),
   NEWEST("Newest", "s.created_at DESC, s.id DESC"),
   OLDEST("Oldest", "s.created_at ASC, s.id ASC");

   private final String label;
   private final String sql;

   SortOrder(String label, String sql) {
      this.label = label;
      this.sql = sql;
   }

   public String label() {
      return this.label;
   }

   /** Fixed ORDER BY fragment - never built from user input. */
   String sql() {
      return this.sql;
   }

   public SortOrder next() {
      return values()[(this.ordinal() + 1) % values().length];
   }

   public static SortOrder fromConfig(String value) {
      if (value == null) {
         return HIGHEST_VOTES;
      }
      return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
         case "newest" -> NEWEST;
         case "oldest" -> OLDEST;
         default -> HIGHEST_VOTES;
      };
   }
}
