package net.srv.eloranks.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.srv.eloranks.core.RankingLogic;
import net.srv.eloranks.data.EloDatabase;
import net.srv.eloranks.data.PlayerRecord;

/**
 * Runs every database call on one dedicated thread, so no region/player thread ever blocks on
 * SQLite and every check-then-write (claims, kills, admin changes) is serialized. Online
 * players' data is cached so GUIs open without touching the database.
 */
public final class DataService {
   @FunctionalInterface
   public interface Call<T> {
      T run(RankingLogic logic) throws Exception;
   }

   private final ExecutorService executor;
   private final EloDatabase db;
   private final RankingLogic logic;
   private final Logger logger;
   private final Map<UUID, PlayerRecord> cache = new ConcurrentHashMap<>();

   public DataService(EloDatabase db, Logger logger) {
      this.db = db;
      this.logic = new RankingLogic(db);
      this.logger = logger;
      this.executor = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "EloRanks-Database");
         thread.setDaemon(true);
         return thread;
      });
   }

   public <T> CompletableFuture<T> submit(Call<T> call) {
      try {
         return CompletableFuture.supplyAsync(() -> {
            try {
               return call.run(this.logic);
            } catch (Exception ex) {
               this.logger.log(Level.SEVERE, "Database operation failed", ex);
               throw new java.util.concurrent.CompletionException(ex);
            }
         }, this.executor);
      } catch (RejectedExecutionException rejected) {
         return CompletableFuture.failedFuture(rejected);
      }
   }

   public PlayerRecord cached(UUID uuid) {
      return this.cache.get(uuid);
   }

   /** Only keeps data of online players (and of players an event is about right now). */
   public void cache(PlayerRecord record) {
      if (record != null) {
         this.cache.put(record.uuid(), record);
      }
   }

   public void forget(UUID uuid) {
      this.cache.remove(uuid);
   }

   /** Finishes queued writes, then closes the database. */
   public void shutdown() {
      this.executor.shutdown();
      try {
         if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
            this.logger.warning("Database writes did not finish within 10 seconds.");
         }
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
      try {
         this.db.close();
      } catch (Exception ex) {
         this.logger.log(Level.WARNING, "Could not close the database", ex);
      }
   }
}
