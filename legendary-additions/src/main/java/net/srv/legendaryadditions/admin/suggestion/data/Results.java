package net.srv.legendaryadditions.admin.suggestion.data;

import java.util.UUID;

public final class Results {
   private Results() {
   }

   public enum VoteResult {
      VOTED, ALREADY_VOTED, REMOVED, NOT_VOTED, NOT_FOUND, NOT_OPEN_FOR_VOTING
   }

   /** Outcome of an admin state change guarded by the status the admin was looking at. */
   public enum ChangeResult {
      OK, NOT_FOUND, CHANGED_BY_SOMEONE_ELSE, NOT_ALLOWED
   }

   public enum CreateResult {
      CREATED, TOO_MANY_PENDING
   }

   public record Created(CreateResult result, long id) {}

   public record Voter(UUID playerUuid, long votedAt) {}
}
