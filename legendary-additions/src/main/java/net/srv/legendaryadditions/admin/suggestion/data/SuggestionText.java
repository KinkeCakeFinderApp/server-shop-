package net.srv.legendaryadditions.admin.suggestion.data;

import java.util.ArrayList;
import java.util.List;

/** Validation and formatting of suggestion text. Pure functions, unit tested. */
public final class SuggestionText {
   private SuggestionText() {
   }

   public record Checked(String text, String error) {
      public boolean ok() {
         return this.error == null;
      }
   }

   /** Strips control characters and legacy colour codes, collapses whitespace and checks length. */
   public static Checked check(String raw, int minLength, int maxLength) {
      if (raw == null) {
         return new Checked(null, "Your suggestion was empty.");
      }
      String cleaned = raw
            .replaceAll("\\s+", " ")
            .replaceAll("\\p{Cntrl}", "")
            .replaceAll("(?i)[§&][0-9a-fk-orx]", "")
            .replaceAll(" {2,}", " ")
            .trim();
      int length = cleaned.codePointCount(0, cleaned.length());
      if (length < minLength) {
         return new Checked(null, "Your suggestion is too short (minimum " + minLength + " characters).");
      }
      if (length > maxLength) {
         return new Checked(null, "Your suggestion is too long (" + length + "/" + maxLength + " characters).");
      }
      return new Checked(cleaned, null);
   }

   /** Word-wraps text into lines of at most {@code width} characters (long words are split). */
   public static List<String> wrap(String text, int width) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();
      for (String word : text.split(" ")) {
         while (word.length() > width) {
            if (!line.isEmpty()) {
               lines.add(line.toString());
               line.setLength(0);
            }
            lines.add(word.substring(0, width));
            word = word.substring(width);
         }
         if (line.length() + word.length() + (line.isEmpty() ? 0 : 1) > width) {
            lines.add(line.toString());
            line.setLength(0);
         }
         if (!line.isEmpty()) {
            line.append(' ');
         }
         line.append(word);
      }
      if (!line.isEmpty()) {
         lines.add(line.toString());
      }
      return lines;
   }

   public static String shorten(String text, int max) {
      return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)).stripTrailing() + "...";
   }
}
