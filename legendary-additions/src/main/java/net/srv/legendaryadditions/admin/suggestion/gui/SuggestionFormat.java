package net.srv.legendaryadditions.admin.suggestion.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.suggestion.data.Suggestion;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionCategory;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionText;

/** Shared text formatting for suggestion screens. */
public final class SuggestionFormat {
   public static final int WRAP = 40;
   /** Lore lines of full text shown in a GUI item before pointing players to chat. */
   public static final int MAX_TEXT_LINES = 24;

   private final Supplier<AdminSettings> settings;

   public SuggestionFormat(Supplier<AdminSettings> settings) {
      this.settings = settings;
   }

   public String date(long epochMillis) {
      DateTimeFormatter formatter;
      try {
         formatter = DateTimeFormatter.ofPattern(this.settings.get().suggestions().dateFormat());
      } catch (IllegalArgumentException ex) {
         formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
      }
      return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis));
   }

   /** Public view never shows an author when names aren't stored; admins always see the UUID too. */
   public String author(Suggestion s, boolean admin) {
      String name = s.authorName() != null ? s.authorName() : "Anonymous";
      return admin ? name + " (" + s.authorUuid() + ")" : name;
   }

   public static String categoryName(SuggestionCategory category) {
      return category == null ? "None" : (category == SuggestionCategory.LEGENDARY ? "Legendary" : "Server");
   }

   public static Component title(Suggestion s) {
      return Items.text("#" + s.id() + "  " + SuggestionText.shorten(s.text(), 32), NamedTextColor.WHITE);
   }

   public static List<Component> fullText(String text) {
      List<Component> lines = new ArrayList<>();
      List<String> wrapped = SuggestionText.wrap(text, WRAP);
      for (int i = 0; i < wrapped.size() && i < MAX_TEXT_LINES; i++) {
         lines.add(Items.text(wrapped.get(i), NamedTextColor.WHITE));
      }
      if (wrapped.size() > MAX_TEXT_LINES) {
         lines.add(Items.text("... (click to read the rest in chat)", NamedTextColor.DARK_GRAY));
      }
      return lines;
   }
}
