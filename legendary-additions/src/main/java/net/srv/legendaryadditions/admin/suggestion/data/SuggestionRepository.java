package net.srv.legendaryadditions.admin.suggestion.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SQLite storage for suggestions and votes. Plain JDBC with no server API, so it can be unit
 * tested. Not thread-safe by itself: {@code SuggestionService} runs every call on one dedicated
 * database thread.
 *
 * <p>Votes are real rows keyed by (suggestion_id, player_uuid) with a primary key, so a player can
 * never hold two votes on one suggestion no matter how often they reconnect or reopen the GUI, and
 * every displayed count is a COUNT over those rows.</p>
 */
public final class SuggestionRepository implements AutoCloseable {
   private static final int SCHEMA_VERSION = 1;
   private static final String SELECT = """
         SELECT s.*,
                (SELECT COUNT(*) FROM votes v WHERE v.suggestion_id = s.id) AS votes,
                EXISTS(SELECT 1 FROM votes v WHERE v.suggestion_id = s.id AND v.player_uuid = ?) AS voted
         FROM suggestions s
         """;

   private final Connection connection;

   public SuggestionRepository(String jdbcUrl) throws SQLException {
      this.connection = DriverManager.getConnection(jdbcUrl);
      try (Statement st = this.connection.createStatement()) {
         st.execute("PRAGMA foreign_keys = ON");
         st.execute("PRAGMA journal_mode = WAL");
         st.execute("PRAGMA synchronous = NORMAL");
         st.execute("PRAGMA busy_timeout = 5000");
      }
      this.migrate();
   }

   private void migrate() throws SQLException {
      int version;
      try (Statement st = this.connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
         version = rs.next() ? rs.getInt(1) : 0;
      }
      if (version >= SCHEMA_VERSION) {
         return;
      }
      this.inTransaction(() -> {
         try (Statement st = this.connection.createStatement()) {
            st.execute("""
                  CREATE TABLE IF NOT EXISTS suggestions (
                     id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                     author_uuid        TEXT    NOT NULL,
                     author_name        TEXT,
                     text               TEXT    NOT NULL,
                     created_at         INTEGER NOT NULL,
                     status             TEXT    NOT NULL,
                     requested_category TEXT    NOT NULL,
                     category           TEXT,
                     reviewed_by_uuid   TEXT,
                     reviewed_by_name   TEXT,
                     reviewed_at        INTEGER,
                     approved_by_uuid   TEXT,
                     approved_by_name   TEXT,
                     approved_at        INTEGER,
                     updated_at         INTEGER NOT NULL
                  )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_suggestions_status_category ON suggestions(status, category)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_suggestions_author_status ON suggestions(author_uuid, status)");
            st.execute("""
                  CREATE TABLE IF NOT EXISTS votes (
                     suggestion_id INTEGER NOT NULL REFERENCES suggestions(id) ON DELETE CASCADE,
                     player_uuid   TEXT    NOT NULL,
                     voted_at      INTEGER NOT NULL,
                     PRIMARY KEY (suggestion_id, player_uuid)
                  )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_votes_player ON votes(player_uuid)");
            st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
         }
         return null;
      });
   }

   // ---------------------------------------------------------------- creation

   /** Creates a PENDING suggestion unless the author already has {@code maxPending} pending ones (0 = no limit). */
   public Results.Created create(UUID author, String authorName, String text, SuggestionCategory requested, long now,
                                 int maxPending) throws SQLException {
      return this.inTransaction(() -> {
         if (maxPending > 0 && this.countPendingBy(author) >= maxPending) {
            return new Results.Created(Results.CreateResult.TOO_MANY_PENDING, -1);
         }
         try (PreparedStatement ps = this.connection.prepareStatement("""
               INSERT INTO suggestions (author_uuid, author_name, text, created_at, status, requested_category, updated_at)
               VALUES (?, ?, ?, ?, 'PENDING', ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, author.toString());
            ps.setString(2, authorName);
            ps.setString(3, text);
            ps.setLong(4, now);
            ps.setString(5, requested.name());
            ps.setLong(6, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
               keys.next();
               return new Results.Created(Results.CreateResult.CREATED, keys.getLong(1));
            }
         }
      });
   }

   public int countPendingBy(UUID author) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT COUNT(*) FROM suggestions WHERE author_uuid = ? AND status = 'PENDING'")) {
         ps.setString(1, author.toString());
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
         }
      }
   }

   // ---------------------------------------------------------------- reading

   public Optional<Suggestion> find(long id, UUID viewer) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(SELECT + " WHERE s.id = ?")) {
         ps.setString(1, viewer.toString());
         ps.setLong(2, id);
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(read(rs)) : Optional.empty();
         }
      }
   }

   /**
    * One page of suggestions with the given status, optionally filtered by final category. Only
    * {@code pageSize} rows are loaded.
    */
   public Page<Suggestion> page(SuggestionStatus status, SuggestionCategory category, SortOrder sort, int page,
                                int pageSize, UUID viewer) throws SQLException {
      String where = category == null ? " WHERE s.status = ?" : " WHERE s.status = ? AND s.category = ?";
      int total;
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT COUNT(*) FROM suggestions s" + where)) {
         ps.setString(1, status.name());
         if (category != null) {
            ps.setString(2, category.name());
         }
         try (ResultSet rs = ps.executeQuery()) {
            total = rs.next() ? rs.getInt(1) : 0;
         }
      }
      int maxPage = Math.max(0, (total - 1) / pageSize);
      int safePage = Math.clamp(page, 0, maxPage);
      List<Suggestion> items = new ArrayList<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
            SELECT + where + " ORDER BY " + sort.sql() + " LIMIT ? OFFSET ?")) {
         int i = 1;
         ps.setString(i++, viewer.toString());
         ps.setString(i++, status.name());
         if (category != null) {
            ps.setString(i++, category.name());
         }
         ps.setInt(i++, pageSize);
         ps.setInt(i, safePage * pageSize);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               items.add(read(rs));
            }
         }
      }
      return new Page<>(items, safePage, pageSize, total);
   }

   public Page<Results.Voter> voters(long suggestionId, int page, int pageSize) throws SQLException {
      int total;
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT COUNT(*) FROM votes WHERE suggestion_id = ?")) {
         ps.setLong(1, suggestionId);
         try (ResultSet rs = ps.executeQuery()) {
            total = rs.next() ? rs.getInt(1) : 0;
         }
      }
      int safePage = Math.clamp(page, 0, Math.max(0, (total - 1) / pageSize));
      List<Results.Voter> voters = new ArrayList<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT player_uuid, voted_at FROM votes WHERE suggestion_id = ? ORDER BY voted_at ASC LIMIT ? OFFSET ?")) {
         ps.setLong(1, suggestionId);
         ps.setInt(2, pageSize);
         ps.setInt(3, safePage * pageSize);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               voters.add(new Results.Voter(UUID.fromString(rs.getString(1)), rs.getLong(2)));
            }
         }
      }
      return new Page<>(voters, safePage, pageSize, total);
   }

   // ---------------------------------------------------------------- voting

   /** Adds a vote. Only APPROVED suggestions accept votes; the primary key blocks duplicates. */
   public Results.VoteResult vote(long id, UUID player, long now) throws SQLException {
      return this.inTransaction(() -> {
         Optional<SuggestionStatus> status = this.statusOf(id);
         if (status.isEmpty()) {
            return Results.VoteResult.NOT_FOUND;
         }
         if (status.get() != SuggestionStatus.APPROVED) {
            return Results.VoteResult.NOT_OPEN_FOR_VOTING;
         }
         try (PreparedStatement ps = this.connection.prepareStatement(
               "INSERT OR IGNORE INTO votes (suggestion_id, player_uuid, voted_at) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, player.toString());
            ps.setLong(3, now);
            return ps.executeUpdate() == 1 ? Results.VoteResult.VOTED : Results.VoteResult.ALREADY_VOTED;
         }
      });
   }

   public Results.VoteResult removeVote(long id, UUID player) throws SQLException {
      return this.inTransaction(() -> {
         Optional<SuggestionStatus> status = this.statusOf(id);
         if (status.isEmpty()) {
            return Results.VoteResult.NOT_FOUND;
         }
         if (status.get() != SuggestionStatus.APPROVED) {
            return Results.VoteResult.NOT_OPEN_FOR_VOTING;
         }
         try (PreparedStatement ps = this.connection.prepareStatement(
               "DELETE FROM votes WHERE suggestion_id = ? AND player_uuid = ?")) {
            ps.setLong(1, id);
            ps.setString(2, player.toString());
            return ps.executeUpdate() == 1 ? Results.VoteResult.REMOVED : Results.VoteResult.NOT_VOTED;
         }
      });
   }

   // ---------------------------------------------------------------- admin changes
   // Every change is conditional on the status the admin was looking at, so two admins acting on the
   // same suggestion (or a stale GUI) cannot overwrite each other.

   public Results.ChangeResult approve(long id, SuggestionStatus expected, SuggestionCategory category, UUID admin,
                                       String adminName, long now) throws SQLException {
      if (expected == SuggestionStatus.APPROVED) {
         return Results.ChangeResult.NOT_ALLOWED;
      }
      return this.guardedUpdate(id, expected, """
            UPDATE suggestions SET status = 'APPROVED', category = ?, approved_by_uuid = ?, approved_by_name = ?,
                   approved_at = ?, reviewed_by_uuid = ?, reviewed_by_name = ?, reviewed_at = ?, updated_at = ?
            WHERE id = ? AND status = ?""", ps -> {
         ps.setString(1, category.name());
         ps.setString(2, admin.toString());
         ps.setString(3, adminName);
         ps.setLong(4, now);
         ps.setString(5, admin.toString());
         ps.setString(6, adminName);
         ps.setLong(7, now);
         ps.setLong(8, now);
         ps.setLong(9, id);
         ps.setString(10, expected.name());
      });
   }

   public Results.ChangeResult reject(long id, SuggestionStatus expected, UUID admin, String adminName, long now)
         throws SQLException {
      if (expected == SuggestionStatus.REJECTED) {
         return Results.ChangeResult.NOT_ALLOWED;
      }
      return this.setReviewedStatus(id, expected, SuggestionStatus.REJECTED, admin, adminName, now);
   }

   public Results.ChangeResult archive(long id, SuggestionStatus expected, UUID admin, String adminName, long now)
         throws SQLException {
      if (expected == SuggestionStatus.ARCHIVED) {
         return Results.ChangeResult.NOT_ALLOWED;
      }
      return this.setReviewedStatus(id, expected, SuggestionStatus.ARCHIVED, admin, adminName, now);
   }

   /** Moves an APPROVED suggestion to another public category. */
   public Results.ChangeResult changeCategory(long id, SuggestionStatus expected, SuggestionCategory category, long now)
         throws SQLException {
      if (expected != SuggestionStatus.APPROVED) {
         return Results.ChangeResult.NOT_ALLOWED;
      }
      return this.guardedUpdate(id, expected,
            "UPDATE suggestions SET category = ?, updated_at = ? WHERE id = ? AND status = ?", ps -> {
               ps.setString(1, category.name());
               ps.setLong(2, now);
               ps.setLong(3, id);
               ps.setString(4, expected.name());
            });
   }

   /** Permanently deletes a suggestion and (via ON DELETE CASCADE) its votes. */
   public Results.ChangeResult delete(long id, SuggestionStatus expected) throws SQLException {
      return this.guardedUpdate(id, expected, "DELETE FROM suggestions WHERE id = ? AND status = ?", ps -> {
         ps.setLong(1, id);
         ps.setString(2, expected.name());
      });
   }

   private Results.ChangeResult setReviewedStatus(long id, SuggestionStatus expected, SuggestionStatus target, UUID admin,
                                                  String adminName, long now) throws SQLException {
      return this.guardedUpdate(id, expected, """
            UPDATE suggestions SET status = ?, reviewed_by_uuid = ?, reviewed_by_name = ?, reviewed_at = ?, updated_at = ?
            WHERE id = ? AND status = ?""", ps -> {
         ps.setString(1, target.name());
         ps.setString(2, admin.toString());
         ps.setString(3, adminName);
         ps.setLong(4, now);
         ps.setLong(5, now);
         ps.setLong(6, id);
         ps.setString(7, expected.name());
      });
   }

   private Results.ChangeResult guardedUpdate(long id, SuggestionStatus expected, String sql, Binder binder)
         throws SQLException {
      return this.inTransaction(() -> {
         try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            binder.bind(ps);
            if (ps.executeUpdate() == 1) {
               return Results.ChangeResult.OK;
            }
         }
         return this.statusOf(id).isEmpty() ? Results.ChangeResult.NOT_FOUND : Results.ChangeResult.CHANGED_BY_SOMEONE_ELSE;
      });
   }

   private Optional<SuggestionStatus> statusOf(long id) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT status FROM suggestions WHERE id = ?")) {
         ps.setLong(1, id);
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(SuggestionStatus.valueOf(rs.getString(1))) : Optional.empty();
         }
      }
   }

   // ---------------------------------------------------------------- plumbing

   private static Suggestion read(ResultSet rs) throws SQLException {
      String category = rs.getString("category");
      return new Suggestion(
            rs.getLong("id"),
            UUID.fromString(rs.getString("author_uuid")),
            rs.getString("author_name"),
            rs.getString("text"),
            rs.getLong("created_at"),
            SuggestionStatus.valueOf(rs.getString("status")),
            SuggestionCategory.valueOf(rs.getString("requested_category")),
            category == null ? null : SuggestionCategory.valueOf(category),
            uuidOrNull(rs.getString("reviewed_by_uuid")),
            rs.getString("reviewed_by_name"),
            longOrNull(rs, "reviewed_at"),
            uuidOrNull(rs.getString("approved_by_uuid")),
            rs.getString("approved_by_name"),
            longOrNull(rs, "approved_at"),
            rs.getInt("votes"),
            rs.getBoolean("voted"));
   }

   private static UUID uuidOrNull(String value) {
      return value == null ? null : UUID.fromString(value);
   }

   private static Long longOrNull(ResultSet rs, String column) throws SQLException {
      long value = rs.getLong(column);
      return rs.wasNull() ? null : value;
   }

   private <T> T inTransaction(SqlWork<T> work) throws SQLException {
      boolean auto = this.connection.getAutoCommit();
      this.connection.setAutoCommit(false);
      try {
         T result = work.run();
         this.connection.commit();
         return result;
      } catch (SQLException | RuntimeException ex) {
         this.connection.rollback();
         throw ex;
      } finally {
         this.connection.setAutoCommit(auto);
      }
   }

   @Override
   public void close() throws SQLException {
      this.connection.close();
   }

   @FunctionalInterface
   private interface SqlWork<T> {
      T run() throws SQLException;
   }

   @FunctionalInterface
   private interface Binder {
      void bind(PreparedStatement ps) throws SQLException;
   }

   /** Statuses that are never shown in the public tabs. */
   public static final Set<SuggestionStatus> HIDDEN_FROM_PUBLIC =
         EnumSet.of(SuggestionStatus.PENDING, SuggestionStatus.REJECTED, SuggestionStatus.ARCHIVED);
}
