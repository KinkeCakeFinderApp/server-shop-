package net.srv.legendaryadditions.forge.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegendaryRepositoryTest {
   @TempDir
   Path dir;

   private static LegendaryDef def(String id, String name, long created, long updated) {
      return new LegendaryDef(id, name, "minecraft:netherite_pickaxe", List.of("&7First line", "", "&6Third"),
            Map.of("minecraft:efficiency", 10, "minecraft:fortune", 5), Map.of("VEIN_MINE", 2, "TELEKINESIS", 1),
            true, true, null, "uuid-1", created, updated);
   }

   @Test
   void savesLoadsUpdatesAndDeletes() throws Exception {
      String url = "jdbc:sqlite:" + this.dir.resolve("legendaries.db");
      try (LegendaryRepository repo = new LegendaryRepository(url)) {
         repo.save(def("miner", "&6Miner", 1, 1));
         repo.save(def("axe", "&cAxe", 2, 2));
         List<LegendaryDef> all = repo.all();
         assertEquals(2, all.size());
         assertEquals("axe", all.get(0).id());
         LegendaryDef miner = all.get(1);
         assertEquals(List.of("&7First line", "", "&6Third"), miner.lore());
         assertEquals(10, miner.enchants().get("minecraft:efficiency"));
         assertEquals(2, miner.abilities().get("VEIN_MINE"));
         assertTrue(miner.unbreakable());
         assertNull(miner.modelData());

         repo.save(def("miner", "&6Renamed", 99, 5));
         LegendaryDef renamed = repo.all().get(1);
         assertEquals("&6Renamed", renamed.name());
         assertEquals(1, renamed.createdAt(), "creation time is kept on update");
         assertEquals(5, renamed.updatedAt());

         assertTrue(repo.delete("axe"));
         assertFalse(repo.delete("axe"));
         assertEquals(1, repo.all().size());
      }
      // Survives reopening the file.
      try (LegendaryRepository repo = new LegendaryRepository(url)) {
         assertEquals(1, repo.all().size());
      }
   }

   @Test
   void encodingRoundTripsAndSkipsDamage() {
      assertEquals(Map.of("a", 1, "b", 20), LegendaryDef.decode(LegendaryDef.encode(Map.of("b", 20, "a", 1))));
      assertEquals(Map.of("ok", 3), LegendaryDef.decode("ok=3,broken,=4,bad=x"));
      assertTrue(LegendaryDef.decode("").isEmpty());
      assertTrue(LegendaryDef.validId("storm_blade2"));
      assertFalse(LegendaryDef.validId("Storm Blade"));
      assertFalse(LegendaryDef.validId("x"));
   }
}
