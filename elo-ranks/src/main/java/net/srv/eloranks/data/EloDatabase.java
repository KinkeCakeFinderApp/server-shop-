package net.srv.eloranks.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * SQLite storage. Plain JDBC with no server API so it can be unit tested. Not thread-safe by
 * itself: {@code DataService} runs every call on one dedicated database thread, which also makes
 * every read-check-write sequence (claims, kills) atomic.
 *
 * <p>Players are keyed by UUID; the name is only kept for display and for admin commands, and is
 * updated on every join, so name changes are harmless.</p>
 */
public final class EloDatabase implements AutoCloseable {
   private static final int SCHEMA_VERSION = 1;

   /** Result of {@link #tryClaim}. */
   public enum ClaimStatus { OK, LOCKED, COOLDOWN, UNKNOWN_PLAYER }

   public record ClaimResult(ClaimStatus status, long lastClaim, long pendingId) {
   }

   /** A claimed reward that has not reached the player yet. */
   public record Pending(long id, String tierId, String rewardId, long createdAt) {
   }

   public record LeaderboardRow(UUID uuid, String name, int elo, Set<String> unlocked) {
   }

   @FunctionalInterface
   public interface SqlWork<T> {
      T run() throws SQLException;
   }

   private final Connection connection;

   public EloDatabase(String jdbcUrl) throws SQLException {
      this.connection = DriverManager.getConnection(jdbcUrl);
      try (Statement st = this.connection.createStatement()) {
         st.execute("PRAGMA foreign_keys = ON");
         st.execute("PRAGMA journal_mode = WAL");
         st.execute("PRAGMA synchronous = FULL");
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
                  CREATE TABLE IF NOT EXISTS players (
                     uuid       TEXT    PRIMARY KEY,
                     name       TEXT    NOT NULL,
                     name_lower TEXT    NOT NULL,
                     elo        INTEGER NOT NULL,
                     kills      INTEGER NOT NULL DEFAULT 0,
                     deaths     INTEGER NOT NULL DEFAULT 0,
                     elo_gained INTEGER NOT NULL DEFAULT 0,
                     elo_lost   INTEGER NOT NULL DEFAULT 0,
                     created_at INTEGER NOT NULL,
                     updated_at INTEGER NOT NULL
                  )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name_lower)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_players_elo ON players(elo DESC)");
            st.execute("""
                  CREATE TABLE IF NOT EXISTS unlocks (
                     uuid        TEXT    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                     tier_id     TEXT    NOT NULL,
                     unlocked_at INTEGER NOT NULL,
                     PRIMARY KEY (uuid, tier_id)
                  )""");
            st.execute("""
                  CREATE TABLE IF NOT EXISTS claims (
                     uuid       TEXT    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                     tier_id    TEXT    NOT NULL,
                     last_claim INTEGER NOT NULL,
                     PRIMARY KEY (uuid, tier_id)
                  )""");
            st.execute("""
                  CREATE TABLE IF NOT EXISTS pending (
                     id         INTEGER PRIMARY KEY AUTOINCREMENT,
                     uuid       TEXT    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                     tier_id    TEXT    NOT NULL,
                     reward_id  TEXT    NOT NULL,
                     created_at INTEGER NOT NULL
                  )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_pending_uuid ON pending(uuid)");
            st.execute("""
                  CREATE TABLE IF NOT EXISTS kills (
                     id      INTEGER PRIMARY KEY AUTOINCREMENT,
                     killer  TEXT    NOT NULL,
                     victim  TEXT    NOT NULL,
                     at      INTEGER NOT NULL,
                     gain    INTEGER NOT NULL,
                     loss    INTEGER NOT NULL,
                     counted INTEGER NOT NULL,
                     reason  TEXT
                  )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_kills_pair ON kills(killer, victim, at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_kills_killer ON kills(killer, at)");
            st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
         }
         return null;
      });
   }

   /** Runs {@code work} in one transaction; rolls back on any exception. Joins an open transaction. */
   public <T> T inTransaction(SqlWork<T> work) throws SQLException {
      boolean auto = this.connection.getAutoCommit();
      if (!auto) {
         return work.run();
      }
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

   // ------------------------------------------------------------------ players

   /** Loads the player, creating them with {@code startingElo} on their first join, and stores their current name. */
   public PlayerRecord loadOrCreate(UUID uuid, String name, int startingElo, long now) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("""
            INSERT INTO players (uuid, name, name_lower, elo, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, name_lower = excluded.name_lower""")) {
         ps.setString(1, uuid.toString());
         ps.setString(2, name);
         ps.setString(3, name.toLowerCase(Locale.ROOT));
         ps.setInt(4, startingElo);
         ps.setLong(5, now);
         ps.setLong(6, now);
         ps.executeUpdate();
      }
      return this.load(uuid).orElseThrow();
   }

   public Optional<PlayerRecord> load(UUID uuid) throws SQLException {
      String id = uuid.toString();
      PlayerRecord base;
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
         ps.setString(1, id);
         try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
               return Optional.empty();
            }
            base = new PlayerRecord(uuid, rs.getString("name"), rs.getInt("elo"), rs.getInt("kills"), rs.getInt("deaths"),
                  rs.getLong("elo_gained"), rs.getLong("elo_lost"), Set.of(), Map.of(), 0);
         }
      }
      Set<String> unlocked = new HashSet<>();
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT tier_id FROM unlocks WHERE uuid = ?")) {
         ps.setString(1, id);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               unlocked.add(rs.getString(1));
            }
         }
      }
      Map<String, Long> claims = new HashMap<>();
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT tier_id, last_claim FROM claims WHERE uuid = ?")) {
         ps.setString(1, id);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               claims.put(rs.getString(1), rs.getLong(2));
            }
         }
      }
      int pending;
      try (PreparedStatement ps = this.connection.prepareStatement("SELECT COUNT(*) FROM pending WHERE uuid = ?")) {
         ps.setString(1, id);
         try (ResultSet rs = ps.executeQuery()) {
            pending = rs.next() ? rs.getInt(1) : 0;
         }
      }
      return Optional.of(new PlayerRecord(uuid, base.name(), base.elo(), base.kills(), base.deaths(), base.eloGained(),
            base.eloLost(), unlocked, claims, pending));
   }

   /** Case-insensitive; the most recently seen player wins if two players ever had the same name. */
   public Optional<UUID> findByName(String name) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT uuid FROM players WHERE name_lower = ? ORDER BY updated_at DESC LIMIT 1")) {
         ps.setString(1, name.toLowerCase(Locale.ROOT));
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
         }
      }
   }

   /** Sets ELO and adds to the statistics. */
   public void updateElo(UUID uuid, int elo, long gained, long lost, int kills, int deaths, long now) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("""
            UPDATE players SET elo = ?, elo_gained = elo_gained + ?, elo_lost = elo_lost + ?,
                               kills = kills + ?, deaths = deaths + ?, updated_at = ?
            WHERE uuid = ?""")) {
         ps.setInt(1, elo);
         ps.setLong(2, gained);
         ps.setLong(3, lost);
         ps.setInt(4, kills);
         ps.setInt(5, deaths);
         ps.setLong(6, now);
         ps.setString(7, uuid.toString());
         ps.executeUpdate();
      }
   }

   /** Marks tiers as reached. @return the tiers that were not unlocked before */
   public List<String> unlock(UUID uuid, Collection<String> tierIds, long now) throws SQLException {
      List<String> added = new ArrayList<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
            "INSERT OR IGNORE INTO unlocks (uuid, tier_id, unlocked_at) VALUES (?, ?, ?)")) {
         for (String tier : tierIds) {
            ps.setString(1, uuid.toString());
            ps.setString(2, tier);
            ps.setLong(3, now);
            if (ps.executeUpdate() > 0) {
               added.add(tier);
            }
         }
      }
      return added;
   }

   // ------------------------------------------------------------------ kills and anti-farming

   public void insertKill(UUID killer, UUID victim, long at, int gain, int loss, boolean counted, String reason) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(
            "INSERT INTO kills (killer, victim, at, gain, loss, counted, reason) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
         ps.setString(1, killer.toString());
         ps.setString(2, victim.toString());
         ps.setLong(3, at);
         ps.setInt(4, gain);
         ps.setInt(5, loss);
         ps.setInt(6, counted ? 1 : 0);
         ps.setString(7, reason);
         ps.executeUpdate();
      }
   }

   /** Time of the last kill of {@code victim} by {@code killer} that changed ELO. */
   public OptionalLong lastCountedKill(UUID killer, UUID victim) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT MAX(at) FROM kills WHERE killer = ? AND victim = ? AND counted = 1")) {
         ps.setString(1, killer.toString());
         ps.setString(2, victim.toString());
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               long at = rs.getLong(1);
               return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(at);
            }
            return OptionalLong.empty();
         }
      }
   }

   public int countedKills(UUID killer, UUID victim, long since) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT COUNT(*) FROM kills WHERE killer = ? AND victim = ? AND counted = 1 AND at >= ?")) {
         ps.setString(1, killer.toString());
         ps.setString(2, victim.toString());
         ps.setLong(3, since);
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
         }
      }
   }

   public long gainSince(UUID killer, long since) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT COALESCE(SUM(gain), 0) FROM kills WHERE killer = ? AND counted = 1 AND at >= ?")) {
         ps.setString(1, killer.toString());
         ps.setLong(2, since);
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
         }
      }
   }

   /** Deletes kill history older than {@code before}; it is only needed for the anti-farming windows. */
   public int pruneKills(long before) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM kills WHERE at < ?")) {
         ps.setLong(1, before);
         return ps.executeUpdate();
      }
   }

   // ------------------------------------------------------------------ claims

   /**
    * Atomically checks that the tier is unlocked and off cooldown, stores the claim time and
    * queues the reward for delivery. A second call inside the cooldown always fails, so a reward
    * can never be claimed twice by double clicks or two servers threads racing.
    */
   public ClaimResult tryClaim(UUID uuid, String tierId, String rewardId, long now, long cooldownMillis) throws SQLException {
      return this.inTransaction(() -> {
         String id = uuid.toString();
         try (PreparedStatement ps = this.connection.prepareStatement("SELECT 1 FROM players WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
               if (!rs.next()) {
                  return new ClaimResult(ClaimStatus.UNKNOWN_PLAYER, 0, 0);
               }
            }
         }
         try (PreparedStatement ps = this.connection.prepareStatement("SELECT 1 FROM unlocks WHERE uuid = ? AND tier_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, tierId);
            try (ResultSet rs = ps.executeQuery()) {
               if (!rs.next()) {
                  return new ClaimResult(ClaimStatus.LOCKED, 0, 0);
               }
            }
         }
         try (PreparedStatement ps = this.connection.prepareStatement("SELECT last_claim FROM claims WHERE uuid = ? AND tier_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, tierId);
            try (ResultSet rs = ps.executeQuery()) {
               if (rs.next()) {
                  long last = rs.getLong(1);
                  if (now - last < cooldownMillis) {
                     return new ClaimResult(ClaimStatus.COOLDOWN, last, 0);
                  }
               }
            }
         }
         try (PreparedStatement ps = this.connection.prepareStatement("""
               INSERT INTO claims (uuid, tier_id, last_claim) VALUES (?, ?, ?)
               ON CONFLICT(uuid, tier_id) DO UPDATE SET last_claim = excluded.last_claim""")) {
            ps.setString(1, id);
            ps.setString(2, tierId);
            ps.setLong(3, now);
            ps.executeUpdate();
         }
         try (PreparedStatement ps = this.connection.prepareStatement(
               "INSERT INTO pending (uuid, tier_id, reward_id, created_at) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, id);
            ps.setString(2, tierId);
            ps.setString(3, rewardId);
            ps.setLong(4, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
               keys.next();
               return new ClaimResult(ClaimStatus.OK, now, keys.getLong(1));
            }
         }
      });
   }

   public List<Pending> pending(UUID uuid) throws SQLException {
      List<Pending> out = new ArrayList<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT id, tier_id, reward_id, created_at FROM pending WHERE uuid = ? ORDER BY id")) {
         ps.setString(1, uuid.toString());
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               out.add(new Pending(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
            }
         }
      }
      return out;
   }

   /** @return true if the row existed (so the reward was delivered exactly once) */
   public boolean deletePending(long id) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM pending WHERE id = ?")) {
         ps.setLong(1, id);
         return ps.executeUpdate() > 0;
      }
   }

   // ------------------------------------------------------------------ admin resets

   public int resetClaims(UUID uuid) throws SQLException {
      return this.deleteFor("claims", uuid);
   }

   public int resetUnlocks(UUID uuid) throws SQLException {
      return this.deleteFor("unlocks", uuid);
   }

   /** Deletes the player and (through the foreign keys) their unlocks, claims and pending rewards. */
   public boolean wipe(UUID uuid) throws SQLException {
      return this.inTransaction(() -> {
         try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM kills WHERE killer = ? OR victim = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
         }
         return this.deleteFor("players", uuid) > 0;
      });
   }

   private int deleteFor(String table, UUID uuid) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
         ps.setString(1, uuid.toString());
         return ps.executeUpdate();
      }
   }

   // ------------------------------------------------------------------ leaderboard

   /** Highest ELO first; ties by name. */
   public List<LeaderboardRow> top(int limit) throws SQLException {
      List<LeaderboardRow> rows = new ArrayList<>();
      Map<UUID, Set<String>> unlocks = new HashMap<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
            "SELECT uuid, name, elo FROM players ORDER BY elo DESC, name_lower ASC LIMIT ?")) {
         ps.setInt(1, limit);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               UUID uuid = UUID.fromString(rs.getString(1));
               Set<String> set = new HashSet<>();
               unlocks.put(uuid, set);
               rows.add(new LeaderboardRow(uuid, rs.getString(2), rs.getInt(3), set));
            }
         }
      }
      if (!rows.isEmpty()) {
         try (Statement st = this.connection.createStatement();
              ResultSet rs = st.executeQuery("SELECT uuid, tier_id FROM unlocks")) {
            while (rs.next()) {
               Set<String> set = unlocks.get(UUID.fromString(rs.getString(1)));
               if (set != null) {
                  set.add(rs.getString(2));
               }
            }
         }
      }
      return rows.stream().map(r -> new LeaderboardRow(r.uuid(), r.name(), r.elo(), Set.copyOf(r.unlocked()))).toList();
   }

   @Override
   public void close() throws SQLException {
      this.connection.close();
   }
}
