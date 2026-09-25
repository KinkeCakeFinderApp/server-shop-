package net.srv.eloranks.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses config durations (30s, 10m, 48h, 2d, 1d12h, plain seconds) and formats them for players. */
public final class Durations {
   private static final Pattern PART = Pattern.compile("(\\d+)\\s*(ms|s|m|h|d|w)");

   private Durations() {
   }

   /**
    * @return the duration in milliseconds
    * @throws IllegalArgumentException if {@code text} is not a duration
    */
   public static long parse(String text) {
      if (text == null || text.isBlank()) {
         throw new IllegalArgumentException("empty duration");
      }
      String value = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
      if (value.matches("\\d+")) {
         return Math.multiplyExact(Long.parseLong(value), 1000L);
      }
      Matcher matcher = PART.matcher(value);
      long total = 0;
      int end = 0;
      while (matcher.find()) {
         if (matcher.start() != end) {
            throw new IllegalArgumentException("not a duration: " + text);
         }
         long amount = Long.parseLong(matcher.group(1));
         long unit = switch (matcher.group(2)) {
            case "ms" -> 1L;
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            default -> 604_800_000L;
         };
         total = Math.addExact(total, Math.multiplyExact(amount, unit));
         end = matcher.end();
      }
      if (end == 0 || end != value.length()) {
         throw new IllegalArgumentException("not a duration: " + text);
      }
      return total;
   }

   /** 1d 4h 12m, 3h 5m, 12m 30s, 45s. Rounds up to the next second so "0s" is never shown early. */
   public static String format(long millis) {
      long seconds = Math.max(0, (millis + 999) / 1000);
      long days = seconds / 86_400;
      long hours = seconds % 86_400 / 3_600;
      long minutes = seconds % 3_600 / 60;
      long secs = seconds % 60;
      StringBuilder out = new StringBuilder();
      if (days > 0) {
         out.append(days).append("d ").append(hours).append("h ").append(minutes).append('m');
      } else if (hours > 0) {
         out.append(hours).append("h ").append(minutes).append('m');
      } else if (minutes > 0) {
         out.append(minutes).append("m ").append(secs).append('s');
      } else {
         out.append(secs).append('s');
      }
      return out.toString();
   }
}
