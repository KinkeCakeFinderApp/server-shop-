package net.srv.legendaryadditions.admin.suggestion.data;

public enum SuggestionCategory {
   LEGENDARY("Legendary Suggestions"),
   SERVER("Server Suggestions");

   private final String title;

   SuggestionCategory(String title) {
      this.title = title;
   }

   public String title() {
      return this.title;
   }

   public SuggestionCategory other() {
      return this == LEGENDARY ? SERVER : LEGENDARY;
   }
}
