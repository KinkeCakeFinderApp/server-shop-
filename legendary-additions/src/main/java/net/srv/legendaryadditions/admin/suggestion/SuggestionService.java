package net.srv.legendaryadditions.admin.suggestion;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionRepository;

/**
 * Runs every database call on one dedicated thread, off all Folia region threads. SQLite allows
 * one writer at a time, so a single ordered queue is both the simplest and the safest option;
 * callers get a {@link CompletableFuture} and hop back onto the player's own scheduler to update GUIs.
 */
public final class SuggestionService implements AutoCloseable {
   private final ExecutorService executor;
   private final SuggestionRepository repository;
   private final Logger logger;

   private SuggestionService(ExecutorService executor, SuggestionRepository repository, Logger logger) {
      this.executor = executor;
      this.repository = repository;
      this.logger = logger;
   }

   public static SuggestionService open(Path databaseFile, Logger logger) throws Exception {
      Class.forName("org.sqlite.JDBC");
      ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "LegendaryAdditions-SuggestionsDB");
         thread.setDaemon(true);
         return thread;
      });
      String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
      try {
         SuggestionRepository repository = executor.submit(() -> new SuggestionRepository(url)).get(30, TimeUnit.SECONDS);
         return new SuggestionService(executor, repository, logger);
      } catch (Exception ex) {
         executor.shutdownNow();
         throw ex;
      }
   }

   @FunctionalInterface
   public interface Call<T> {
      T run(SuggestionRepository repository) throws SQLException;
   }

   public <T> CompletableFuture<T> submit(Call<T> call) {
      try {
         return CompletableFuture.supplyAsync(() -> {
            try {
               return call.run(this.repository);
            } catch (SQLException ex) {
               throw new CompletionException(ex);
            }
         }, this.executor);
      } catch (RuntimeException rejected) {
         return CompletableFuture.failedFuture(rejected);
      }
   }

   public Logger logger() {
      return this.logger;
   }

   @Override
   public void close() {
      try {
         this.executor.submit(() -> {
            this.repository.close();
            return null;
         }).get(10, TimeUnit.SECONDS);
      } catch (Exception ex) {
         this.logger.log(Level.WARNING, "Could not close the suggestions database cleanly", ex);
      }
      this.executor.shutdown();
      try {
         if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
            this.executor.shutdownNow();
         }
      } catch (InterruptedException ex) {
         this.executor.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }
}
