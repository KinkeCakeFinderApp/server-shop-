package net.srv.legendaryadditions.forge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import net.srv.legendaryadditions.forge.data.LegendaryRepository;

/**
 * Admin-made legendaries. Every definition is cached in memory so ability checks on region
 * threads never touch the database; writes go to legendaries.db on one database thread and
 * update the cache once they succeed.
 */
public final class LegendaryService implements AutoCloseable {
   private final ExecutorService executor;
   private final LegendaryRepository repository;
   private final Logger logger;
   private final Map<String, LegendaryDef> cache = new ConcurrentHashMap<>();
   private final List<Consumer<LegendaryDef>> saveListeners = new CopyOnWriteArrayList<>();

   private LegendaryService(ExecutorService executor, LegendaryRepository repository, Logger logger) {
      this.executor = executor;
      this.repository = repository;
      this.logger = logger;
   }

   public static LegendaryService open(Path databaseFile, Logger logger) throws Exception {
      Class.forName("org.sqlite.JDBC");
      ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "LegendaryAdditions-LegendariesDB");
         thread.setDaemon(true);
         return thread;
      });
      String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
      try {
         LegendaryRepository repository = executor.submit(() -> new LegendaryRepository(url)).get(30, TimeUnit.SECONDS);
         LegendaryService service = new LegendaryService(executor, repository, logger);
         for (LegendaryDef def : executor.submit(repository::all).get(30, TimeUnit.SECONDS)) {
            service.cache.put(def.id(), def);
         }
         logger.info("Loaded " + service.cache.size() + " custom legendaries from legendaries.db.");
         return service;
      } catch (Exception ex) {
         executor.shutdownNow();
         throw ex;
      }
   }

   public LegendaryDef get(String id) {
      return id == null ? null : this.cache.get(id);
   }

   public List<LegendaryDef> all() {
      List<LegendaryDef> list = new ArrayList<>(this.cache.values());
      list.sort(Comparator.comparing(LegendaryDef::id));
      return list;
   }

   /** Runs after every successful save (on the database thread). */
   public void onSaved(Consumer<LegendaryDef> listener) {
      this.saveListeners.add(listener);
   }

   public CompletableFuture<LegendaryDef> save(LegendaryDef def) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            LegendaryDef existing = this.cache.get(def.id());
            LegendaryDef stored = existing == null ? def : new LegendaryDef(def.id(), def.name(), def.material(), def.lore(),
                  def.enchants(), def.abilities(), def.unbreakable(), def.glow(), def.modelData(),
                  existing.createdBy(), existing.createdAt(), def.updatedAt());
            this.repository.save(stored);
            this.cache.put(stored.id(), stored);
            for (Consumer<LegendaryDef> listener : this.saveListeners) {
               try {
                  listener.accept(stored);
               } catch (RuntimeException ex) {
                  this.logger.log(Level.WARNING, "A legendary save listener failed", ex);
               }
            }
            return stored;
         } catch (Exception ex) {
            throw new java.util.concurrent.CompletionException(ex);
         }
      }, this.executor);
   }

   public CompletableFuture<Boolean> delete(String id) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            boolean removed = this.repository.delete(id);
            this.cache.remove(id);
            return removed;
         } catch (Exception ex) {
            throw new java.util.concurrent.CompletionException(ex);
         }
      }, this.executor);
   }

   @Override
   public void close() {
      try {
         this.executor.submit(() -> {
            this.repository.close();
            return null;
         }).get(10, TimeUnit.SECONDS);
      } catch (Exception ex) {
         this.logger.log(Level.WARNING, "Could not close legendaries.db cleanly", ex);
      }
      this.executor.shutdown();
   }
}
