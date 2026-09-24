package net.srv.legendaryadditions.admin.suggestion.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuggestionRepositoryTest {
   private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
   private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
   private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

   @TempDir
   Path dir;
   private String url;

   @BeforeEach
   void setUp() {
      this.url = "jdbc:sqlite:" + this.dir.resolve("suggestions.db");
   }

   private long createPending(SuggestionRepository repo, UUID author, String text) throws Exception {
      Results.Created created = repo.create(author, "name", text, SuggestionCategory.LEGENDARY, 1000, 0);
      assertEquals(Results.CreateResult.CREATED, created.result());
      return created.id();
   }

   @Test
   void newSuggestionsArePendingAndHiddenFromPublicTabs() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         long id = createPending(repo, ALICE, "Add a legendary trident");
         Suggestion s = repo.find(id, ALICE).orElseThrow();
         assertEquals(SuggestionStatus.PENDING, s.status());
         assertEquals(null, s.category());
         for (SuggestionCategory c : SuggestionCategory.values()) {
            assertEquals(0, repo.page(SuggestionStatus.APPROVED, c, SortOrder.HIGHEST_VOTES, 0, 45, ALICE).total());
         }
         assertEquals(1, repo.page(SuggestionStatus.PENDING, null, SortOrder.NEWEST, 0, 45, ADMIN).total());
      }
   }

   @Test
   void approvedSuggestionAppearsOnlyInItsAssignedCategory() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         long id = createPending(repo, ALICE, "Server-wide event every Friday");
         assertEquals(Results.ChangeResult.OK,
               repo.approve(id, SuggestionStatus.PENDING, SuggestionCategory.SERVER, ADMIN, "Admin", 2000));
         assertEquals(1, repo.page(SuggestionStatus.APPROVED, SuggestionCategory.SERVER, SortOrder.HIGHEST_VOTES, 0, 45, BOB).total());
         assertEquals(0, repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.HIGHEST_VOTES, 0, 45, BOB).total());
         Suggestion s = repo.find(id, BOB).orElseThrow();
         assertEquals(ADMIN, s.approvedByUuid());
         assertEquals("Admin", s.approvedByName());
         assertEquals(2000L, s.approvedAt());

         assertEquals(Results.ChangeResult.OK,
               repo.changeCategory(id, SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, 3000));
         assertEquals(1, repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.HIGHEST_VOTES, 0, 45, BOB).total());
         assertEquals(0, repo.page(SuggestionStatus.APPROVED, SuggestionCategory.SERVER, SortOrder.HIGHEST_VOTES, 0, 45, BOB).total());
      }
   }

   @Test
   void duplicateVotesAreRejectedAndSurviveRestart() throws Exception {
      long id;
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         id = createPending(repo, ALICE, "More shop items");
         assertEquals(Results.VoteResult.NOT_OPEN_FOR_VOTING, repo.vote(id, BOB, 1));
         repo.approve(id, SuggestionStatus.PENDING, SuggestionCategory.SERVER, ADMIN, "Admin", 2);
         assertEquals(Results.VoteResult.VOTED, repo.vote(id, BOB, 3));
         assertEquals(Results.VoteResult.ALREADY_VOTED, repo.vote(id, BOB, 4));
         assertEquals(Results.VoteResult.VOTED, repo.vote(id, ALICE, 5));
      }
      // "Restart": a brand-new connection to the same file.
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         Suggestion forBob = repo.find(id, BOB).orElseThrow();
         assertEquals(2, forBob.votes());
         assertTrue(forBob.votedByViewer());
         assertFalse(repo.find(id, ADMIN).orElseThrow().votedByViewer());
         assertEquals(Results.VoteResult.ALREADY_VOTED, repo.vote(id, BOB, 6));
         assertEquals(2, repo.voters(id, 0, 45).total());
      }
   }

   @Test
   void voteRemovalOnlyRemovesOwnVote() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         long id = createPending(repo, ALICE, "Voting test");
         repo.approve(id, SuggestionStatus.PENDING, SuggestionCategory.LEGENDARY, ADMIN, "Admin", 2);
         repo.vote(id, BOB, 3);
         assertEquals(Results.VoteResult.NOT_VOTED, repo.removeVote(id, ALICE));
         assertEquals(Results.VoteResult.REMOVED, repo.removeVote(id, BOB));
         assertEquals(0, repo.find(id, BOB).orElseThrow().votes());
      }
   }

   @Test
   void rejectedArchivedAndDeletedSuggestionsCannotBeVotedOnOrShown() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         long rejected = createPending(repo, ALICE, "Rejected idea");
         long archived = createPending(repo, ALICE, "Archived idea");
         assertEquals(Results.ChangeResult.OK, repo.reject(rejected, SuggestionStatus.PENDING, ADMIN, "Admin", 2));
         repo.approve(archived, SuggestionStatus.PENDING, SuggestionCategory.SERVER, ADMIN, "Admin", 2);
         repo.vote(archived, BOB, 3);
         assertEquals(Results.ChangeResult.OK, repo.archive(archived, SuggestionStatus.APPROVED, ADMIN, "Admin", 4));

         assertEquals(Results.VoteResult.NOT_OPEN_FOR_VOTING, repo.vote(rejected, BOB, 5));
         assertEquals(Results.VoteResult.NOT_OPEN_FOR_VOTING, repo.vote(archived, ALICE, 5));
         assertEquals(0, repo.page(SuggestionStatus.APPROVED, SuggestionCategory.SERVER, SortOrder.HIGHEST_VOTES, 0, 45, BOB).total());

         assertEquals(Results.ChangeResult.OK, repo.delete(archived, SuggestionStatus.ARCHIVED));
         assertTrue(repo.find(archived, BOB).isEmpty());
         assertEquals(0, repo.voters(archived, 0, 45).total());
         assertEquals(Results.VoteResult.NOT_FOUND, repo.vote(archived, BOB, 6));
         assertEquals(Results.VoteResult.NOT_FOUND, repo.vote(999_999, BOB, 6));
      }
   }

   @Test
   void staleAdminActionsAreRefused() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         long id = createPending(repo, ALICE, "Race condition");
         // Admin A approves while admin B still looks at the PENDING version.
         assertEquals(Results.ChangeResult.OK, repo.approve(id, SuggestionStatus.PENDING, SuggestionCategory.LEGENDARY, ADMIN, "A", 2));
         assertEquals(Results.ChangeResult.CHANGED_BY_SOMEONE_ELSE, repo.reject(id, SuggestionStatus.PENDING, ADMIN, "B", 3));
         assertEquals(Results.ChangeResult.NOT_ALLOWED, repo.approve(id, SuggestionStatus.APPROVED, SuggestionCategory.SERVER, ADMIN, "B", 3));
         assertEquals(Results.ChangeResult.NOT_FOUND, repo.archive(12345, SuggestionStatus.PENDING, ADMIN, "B", 3));
         assertEquals(SuggestionStatus.APPROVED, repo.find(id, ADMIN).orElseThrow().status());
      }
   }

   @Test
   void pendingLimitIsEnforced() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         assertEquals(Results.CreateResult.CREATED, repo.create(ALICE, null, "one", SuggestionCategory.SERVER, 1, 2).result());
         assertEquals(Results.CreateResult.CREATED, repo.create(ALICE, null, "two", SuggestionCategory.SERVER, 1, 2).result());
         assertEquals(Results.CreateResult.TOO_MANY_PENDING, repo.create(ALICE, null, "three", SuggestionCategory.SERVER, 1, 2).result());
         assertEquals(Results.CreateResult.CREATED, repo.create(BOB, null, "bob", SuggestionCategory.SERVER, 1, 2).result());
      }
   }

   @Test
   void pagingAndSortingByVotes() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         long[] ids = new long[50];
         for (int i = 0; i < ids.length; i++) {
            ids[i] = repo.create(ALICE, null, "idea " + i, SuggestionCategory.LEGENDARY, i, 0).id();
            repo.approve(ids[i], SuggestionStatus.PENDING, SuggestionCategory.LEGENDARY, ADMIN, "Admin", i);
         }
         repo.vote(ids[10], BOB, 1);
         repo.vote(ids[10], ALICE, 1);
         repo.vote(ids[20], BOB, 1);

         Page<Suggestion> first = repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.HIGHEST_VOTES, 0, 45, BOB);
         assertEquals(45, first.items().size());
         assertEquals(50, first.total());
         assertTrue(first.hasNext());
         assertEquals(ids[10], first.items().get(0).id());
         assertEquals(2, first.items().get(0).votes());
         assertEquals(ids[20], first.items().get(1).id());

         Page<Suggestion> second = repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.HIGHEST_VOTES, 1, 45, BOB);
         assertEquals(5, second.items().size());
         assertFalse(second.hasNext());
         // Out-of-range pages clamp instead of failing.
         assertEquals(1, repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.NEWEST, 99, 45, BOB).page());

         assertEquals(ids[49], repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.NEWEST, 0, 45, BOB).items().get(0).id());
         assertEquals(ids[0], repo.page(SuggestionStatus.APPROVED, SuggestionCategory.LEGENDARY, SortOrder.OLDEST, 0, 45, BOB).items().get(0).id());
      }
   }

   @Test
   void databaseFileIsCreated() throws Exception {
      try (SuggestionRepository repo = new SuggestionRepository(this.url)) {
         createPending(repo, ALICE, "file check");
      }
      assertTrue(Files.exists(this.dir.resolve("suggestions.db")));
   }
}
