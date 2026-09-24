package net.srv.legendaryadditions.admin.suggestion.data;

import java.util.UUID;

/**
 * One suggestion as read from storage. {@code votes} is always computed from the vote records, and
 * {@code votedByViewer} says whether the player the query was made for has a vote record.
 */
public record Suggestion(
      long id,
      UUID authorUuid,
      String authorName,
      String text,
      long createdAt,
      SuggestionStatus status,
      SuggestionCategory requestedCategory,
      SuggestionCategory category,
      UUID reviewedByUuid,
      String reviewedByName,
      Long reviewedAt,
      UUID approvedByUuid,
      String approvedByName,
      Long approvedAt,
      int votes,
      boolean votedByViewer) {

   /** The category shown to players: the admin-assigned one once approved, otherwise the requested one. */
   public SuggestionCategory effectiveCategory() {
      return this.category != null ? this.category : this.requestedCategory;
   }
}
