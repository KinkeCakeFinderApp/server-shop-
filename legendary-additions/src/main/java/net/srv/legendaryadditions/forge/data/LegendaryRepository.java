package net.srv.legendaryadditions.forge.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SQLite storage for admin-made legendaries (legendaries.db). Plain JDBC so it can be unit tested;
 * {@code LegendaryService} runs every call on one database thread.
 */
public final class LegendaryRepository implements AutoCloseable {
   private static final int SCHEMA_VERSION = 1;

   private final Connection connection;

   public LegendaryRepository(String jdbcUrl) throws SQLException {
      this.connection = DriverManager.getConnection(jdbcUrl);
      try (Statement st = this.connection.createStatement()) {
         st.execute("PRAGMA journal_mode = WAL");
         st.execute("PRAGMA synchronous = NORMAL");
         st.execute("PRAGMA busy_timeout = 5000");
         int version;
         try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
         }
         if (version < SCHEMA_VERSION) {
            st.execute("""
                  CREATE TABLE IF NOT EXISTS legendaries (
                     id          TEXT    PRIMARY KEY,
                     name        TEXT    NOT NULL,
                     material    TEXT    NOT NULL,
                     lore        TEXT    NOT NULL,
                     enchants    TEXT    NOT NULL,
                     abilities   TEXT    NOT NULL,
                     unbreakable INTEGER NOT NULL,
                     glow        INTEGER NOT NULL,
                     model_data  INTEGER,
                     created_by  TEXT,
                     created_at  INTEGER NOT NULL,
                     updated_at  INTEGER NOT NULL
                  )""");
            st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
         }
      }
   }

   public List<LegendaryDef> all() throws SQLException {
      List<LegendaryDef> out = new ArrayList<>();
      try (Statement st = this.connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM legendaries ORDER BY id")) {
         while (rs.next()) {
            out.add(read(rs));
         }
      }
      return out;
   }

   /** Inserts or replaces by id. A replaced row keeps its original creator and creation time. */
   public void save(LegendaryDef def) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("""
            INSERT INTO legendaries (id, name, material, lore, enchants, abilities, unbreakable, glow, model_data,
                                     created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
               name = excluded.name, material = excluded.material, lore = excluded.lore,
               enchants = excluded.enchants, abilities = excluded.abilities, unbreakable = excluded.unbreakable,
               glow = excluded.glow, model_data = excluded.model_data, updated_at = excluded.updated_at""")) {
         ps.setString(1, def.id());
         ps.setString(2, def.name());
         ps.setString(3, def.material());
         ps.setString(4, String.join("\n", def.lore()));
         ps.setString(5, LegendaryDef.encode(def.enchants()));
         ps.setString(6, LegendaryDef.encode(def.abilities()));
         ps.setInt(7, def.unbreakable() ? 1 : 0);
         ps.setInt(8, def.glow() ? 1 : 0);
         if (def.modelData() == null) {
            ps.setNull(9, java.sql.Types.INTEGER);
         } else {
            ps.setInt(9, def.modelData());
         }
         ps.setString(10, def.createdBy());
         ps.setLong(11, def.createdAt());
         ps.setLong(12, def.updatedAt());
         ps.executeUpdate();
      }
   }

   public boolean delete(String id) throws SQLException {
      try (PreparedStatement ps = this.connection.prepareStatement("DELETE FROM legendaries WHERE id = ?")) {
         ps.setString(1, id);
         return ps.executeUpdate() > 0;
      }
   }

   private static LegendaryDef read(ResultSet rs) throws SQLException {
      String lore = rs.getString("lore");
      int model = rs.getInt("model_data");
      Integer modelData = rs.wasNull() ? null : model;
      return new LegendaryDef(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("material"),
            lore == null || lore.isEmpty() ? List.of() : Arrays.asList(lore.split("\n", -1)),
            LegendaryDef.decode(rs.getString("enchants")),
            LegendaryDef.decode(rs.getString("abilities")),
            rs.getInt("unbreakable") != 0,
            rs.getInt("glow") != 0,
            modelData,
            rs.getString("created_by"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));
   }

   @Override
   public void close() throws SQLException {
      this.connection.close();
   }
}
