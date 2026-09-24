package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.srv.legendaryadditions.admin.suggestion.ChatInputManager;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.SuggestionService;
import net.srv.legendaryadditions.admin.suggestion.data.Page;
import net.srv.legendaryadditions.admin.suggestion.data.Results;
import net.srv.legendaryadditions.admin.suggestion.data.SortOrder;
import net.srv.legendaryadditions.admin.suggestion.data.Suggestion;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionCategory;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionStatus;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionText;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Public suggestion screens: the two category tabs, suggestion details, voting and submitting.
 *
 * <p>Threading: every method is called on the player's region thread. Database work goes to
 * {@link SuggestionService}; results come back through the player's EntityScheduler, which simply
 * drops the callback if the player has logged off.</p>
 */
public final class SuggestionGui {
   static final int PAGE_SIZE = 45;
   private static final int ROWS = 6;

   private final Plugin plugin;
   private final SuggestionService service;
   private final SuggestionAccess access;
   private final SuggestionFormat format;
   private final ClickGuard guard;
   private final ChatInputManager chat;
   private final Map<UUID, SortOrder> sortOrders = new ConcurrentHashMap<>();

   public SuggestionGui(Plugin plugin, SuggestionService service, SuggestionAccess access, SuggestionFormat format,
                        ClickGuard guard, ChatInputManager chat) {
      this.plugin = plugin;
      this.service = service;
      this.access = access;
      this.format = format;
      this.guard = guard;
      this.chat = chat;
   }

   public SortOrder sort(UUID player) {
      return this.sortOrders.computeIfAbsent(player, id -> SortOrder.fromConfig(this.access.config().sorting()));
   }

   // ------------------------------------------------------------------ main list with tabs

   public void openList(Player player, SuggestionCategory category, int page) {
      if (!this.checkUse(player)) {
         return;
      }
      UUID id = player.getUniqueId();
      SortOrder sort = this.sort(id);
      this.load(player, this.service.submit(repo -> repo.page(SuggestionStatus.APPROVED, category, sort, page, PAGE_SIZE, id)),
            result -> this.renderList(player, category, sort, result));
   }

   private void renderList(Player player, SuggestionCategory category, SortOrder sort, Page<Suggestion> page) {
      Component title = Items.text(category.title() + " (" + (page.page() + 1) + "/" + page.pageCount() + ")", NamedTextColor.DARK_PURPLE);
      Menu menu = new Menu(player.getUniqueId(), ROWS, title, false);
      int slot = 0;
      for (Suggestion s : page.items()) {
         long suggestionId = s.id();
         menu.set(slot++, this.listIcon(s), (p, click) -> {
            if (click.isRightClick()) {
               this.vote(p, suggestionId, () -> this.openList(p, category, page.page()));
            } else {
               this.openDetail(p, suggestionId, category, page.page());
            }
         });
      }
      if (page.items().isEmpty()) {
         menu.set(22, Items.icon(Material.BARRIER, "No approved suggestions here yet", NamedTextColor.GRAY,
               "Be the first - click Submit Suggestion."));
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, this.tab(Material.NETHER_STAR, SuggestionCategory.LEGENDARY, category),
            (p, c) -> this.openList(p, SuggestionCategory.LEGENDARY, 0));
      menu.set(46, this.tab(Material.COMPASS, SuggestionCategory.SERVER, category),
            (p, c) -> this.openList(p, SuggestionCategory.SERVER, 0));
      menu.set(47, Items.icon(Material.HOPPER, "Sort: " + sort.label(), NamedTextColor.AQUA, "Click to change the order."),
            (p, c) -> {
               this.sortOrders.put(p.getUniqueId(), sort.next());
               this.openList(p, category, 0);
            });
      if (page.hasPrevious()) {
         menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW),
               (p, c) -> this.openList(p, category, page.page() - 1));
      }
      menu.set(49, Items.icon(Material.WRITABLE_BOOK, "Submit Suggestion", NamedTextColor.GREEN,
            "Type your idea in chat.", "An admin reviews it before it is shown here."), (p, c) -> this.startSubmission(p));
      if (page.hasNext()) {
         menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW),
               (p, c) -> this.openList(p, category, page.page() + 1));
      }
      menu.set(52, Items.icon(Material.OAK_DOOR, "Back", NamedTextColor.WHITE,
            page.page() > 0 ? "Back to the first page." : "Leave the suggestion menu."), (p, c) -> {
         if (page.page() > 0) {
            this.openList(p, category, 0);
         } else {
            p.closeInventory();
         }
      });
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   private org.bukkit.inventory.ItemStack tab(Material material, SuggestionCategory tab, SuggestionCategory active) {
      boolean selected = tab == active;
      List<Component> lore = List.of(Items.text(selected ? "Currently viewing" : "Click to view", NamedTextColor.GRAY));
      return Items.icon(material, Items.text(tab.title(), selected ? NamedTextColor.GOLD : NamedTextColor.YELLOW), lore, selected);
   }

   private org.bukkit.inventory.ItemStack listIcon(Suggestion s) {
      List<Component> lore = new ArrayList<>();
      lore.add(Items.text("Category: " + SuggestionFormat.categoryName(s.effectiveCategory()), NamedTextColor.GRAY));
      lore.add(Items.text("Votes: " + s.votes(), NamedTextColor.GOLD));
      lore.add(Items.text("By: " + this.format.author(s, false), NamedTextColor.GRAY));
      lore.add(Items.text("Submitted: " + this.format.date(s.createdAt()), NamedTextColor.GRAY));
      lore.add(Component.empty());
      lore.add(s.votedByViewer()
            ? Items.text("✔ You voted for this", NamedTextColor.GREEN)
            : Items.text("✘ You have not voted", NamedTextColor.RED));
      lore.add(Items.text("Left-click: read the full suggestion", NamedTextColor.DARK_GRAY));
      lore.add(Items.text(s.votedByViewer() ? "Right-click: (already voted)" : "Right-click: vote", NamedTextColor.DARK_GRAY));
      return Items.icon(s.votedByViewer() ? Material.ENCHANTED_BOOK : Material.PAPER, SuggestionFormat.title(s), lore, false);
   }

   // ------------------------------------------------------------------ details

   public void openDetail(Player player, long suggestionId, SuggestionCategory backCategory, int backPage) {
      if (!this.checkUse(player)) {
         return;
      }
      UUID id = player.getUniqueId();
      this.load(player, this.service.submit(repo -> repo.find(suggestionId, id)), found -> {
         if (found.isEmpty() || found.get().status() != SuggestionStatus.APPROVED) {
            Messages.error(player, "That suggestion is no longer available.");
            this.openList(player, backCategory, backPage);
            return;
         }
         this.renderDetail(player, found.get(), backCategory, backPage);
      });
   }

   private void renderDetail(Player player, Suggestion s, SuggestionCategory backCategory, int backPage) {
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Suggestion #" + s.id(), NamedTextColor.DARK_PURPLE), false);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(4, Items.icon(Material.WRITTEN_BOOK, Items.text("Suggestion #" + s.id(), NamedTextColor.GOLD),
            SuggestionFormat.fullText(s.text()), false), (p, c) -> this.sendFullText(p, s));
      menu.set(11, Items.icon(Material.NAME_TAG, "Details", NamedTextColor.AQUA,
            "Category: " + SuggestionFormat.categoryName(s.effectiveCategory()),
            "Votes: " + s.votes(),
            "By: " + this.format.author(s, false),
            "Submitted: " + this.format.date(s.createdAt())));

      boolean removal = this.access.config().allowVoteRemoval();
      long suggestionId = s.id();
      Runnable refresh = () -> this.openDetail(player, suggestionId, backCategory, backPage);
      if (!s.votedByViewer()) {
         menu.set(13, Items.icon(Material.LIME_DYE, "Vote for this suggestion", NamedTextColor.GREEN,
               "Current votes: " + s.votes(), "You get one vote per suggestion."), (p, c) -> this.vote(p, suggestionId, refresh));
      } else if (removal) {
         menu.set(13, Items.icon(Material.RED_DYE, "✔ Voted - click to remove your vote", NamedTextColor.GREEN,
               "Current votes: " + s.votes()), (p, c) -> this.removeVote(p, suggestionId, refresh));
      } else {
         menu.set(13, Items.icon(Material.GRAY_DYE, "✔ You already voted", NamedTextColor.GREEN,
               "Current votes: " + s.votes()));
      }
      menu.set(15, Items.icon(Material.OAK_SIGN, "Read in chat", NamedTextColor.WHITE, "Prints the full text in chat."),
            (p, c) -> this.sendFullText(p, s));
      menu.set(18, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openList(p, backCategory, backPage));
      menu.set(26, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   private void sendFullText(Player player, Suggestion s) {
      player.sendMessage(Items.text("Suggestion #" + s.id() + " (" + s.votes() + " votes):", NamedTextColor.GOLD));
      player.sendMessage(Component.text(s.text(), NamedTextColor.WHITE));
   }

   // ------------------------------------------------------------------ voting

   private void vote(Player player, long suggestionId, Runnable refresh) {
      if (!this.checkUse(player) || !this.guard.begin(player.getUniqueId())) {
         return;
      }
      UUID id = player.getUniqueId();
      this.finish(player, this.service.submit(repo -> repo.vote(suggestionId, id, System.currentTimeMillis())), result -> {
         switch (result) {
            case VOTED -> Messages.success(player, "Your vote was counted.");
            case ALREADY_VOTED -> Messages.info(player, "You have already voted for this suggestion.");
            case NOT_FOUND, NOT_OPEN_FOR_VOTING -> Messages.error(player, "That suggestion is no longer open for voting.");
            default -> Messages.error(player, Messages.ACTION_FAILED);
         }
         refresh.run();
      });
   }

   private void removeVote(Player player, long suggestionId, Runnable refresh) {
      if (!this.checkUse(player)) {
         return;
      }
      if (!this.access.config().allowVoteRemoval()) {
         Messages.error(player, "Removing votes is disabled on this server.");
         return;
      }
      if (!this.guard.begin(player.getUniqueId())) {
         return;
      }
      UUID id = player.getUniqueId();
      this.finish(player, this.service.submit(repo -> repo.removeVote(suggestionId, id)), result -> {
         switch (result) {
            case REMOVED -> Messages.success(player, "Your vote was removed.");
            case NOT_VOTED -> Messages.info(player, "You had not voted for this suggestion.");
            default -> Messages.error(player, "That suggestion is no longer open for voting.");
         }
         refresh.run();
      });
   }

   // ------------------------------------------------------------------ submitting

   private void startSubmission(Player player) {
      if (!this.checkUse(player)) {
         return;
      }
      int max = this.access.config().maxLength();
      player.closeInventory();
      Messages.info(player, "Type your suggestion in chat (" + this.access.config().minLength() + "-" + max
            + " characters). Type 'cancel' to stop.");
      this.chat.begin(player, this.access.config().inputTimeoutSeconds(), this::onSuggestionTyped);
   }

   private void onSuggestionTyped(Player player, String raw) {
      if (raw.trim().equalsIgnoreCase("cancel")) {
         this.chat.end(player.getUniqueId());
         Messages.info(player, "Suggestion cancelled.");
         return;
      }
      SuggestionText.Checked checked = SuggestionText.check(raw, this.access.config().minLength(), this.access.config().maxLength());
      if (!checked.ok()) {
         Messages.error(player, checked.error() + " Try again, or type 'cancel'.");
         return;
      }
      this.chat.end(player.getUniqueId());
      this.openCategoryPicker(player, checked.text());
   }

   private void openCategoryPicker(Player player, String text) {
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Choose a category", NamedTextColor.DARK_PURPLE), false);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      List<Component> preview = new ArrayList<>(SuggestionFormat.fullText(text));
      menu.set(4, Items.icon(Material.PAPER, Items.text("Your suggestion", NamedTextColor.GOLD), preview, false));
      boolean[] submitted = {false};
      menu.set(11, Items.icon(Material.NETHER_STAR, "Legendary Suggestion", NamedTextColor.GOLD,
            "Ideas for legendary items and gear."), (p, c) -> this.submit(p, text, SuggestionCategory.LEGENDARY, submitted));
      menu.set(15, Items.icon(Material.COMPASS, "Server Suggestion", NamedTextColor.AQUA,
            "Ideas for the server itself."), (p, c) -> this.submit(p, text, SuggestionCategory.SERVER, submitted));
      menu.set(22, Items.icon(Material.BARRIER, "Cancel", NamedTextColor.RED), (p, c) -> {
         submitted[0] = true;
         p.closeInventory();
         Messages.info(p, "Suggestion cancelled.");
      });
      player.openInventory(menu.getInventory());
   }

   private void submit(Player player, String text, SuggestionCategory category, boolean[] submitted) {
      if (submitted[0] || !this.checkUse(player) || !this.guard.begin(player.getUniqueId())) {
         return;
      }
      submitted[0] = true;
      player.closeInventory();
      UUID id = player.getUniqueId();
      String name = this.access.config().storePlayerNames() ? player.getName() : null;
      int maxPending = this.access.config().maxPendingPerPlayer();
      this.finish(player, this.service.submit(repo -> repo.create(id, name, text, category, System.currentTimeMillis(), maxPending)),
            created -> {
               if (created.result() == Results.CreateResult.TOO_MANY_PENDING) {
                  Messages.error(player, "You already have " + maxPending + " suggestions waiting for review. Please wait for an admin.");
                  return;
               }
               Messages.success(player, "Suggestion #" + created.id() + " submitted! It is PENDING until an admin reviews it.");
               this.notifyAdmins(player.getName(), created.id());
            });
   }

   private void notifyAdmins(String author, long id) {
      for (Player online : Bukkit.getOnlinePlayers()) {
         online.getScheduler().run(this.plugin, task -> {
            if (SuggestionAccess.isAdmin(online)) {
               Messages.info(online, author + " submitted suggestion #" + id + ". Review it with /suggestionadmin.");
            }
         }, null);
      }
   }

   // ------------------------------------------------------------------ plumbing

   private boolean checkUse(Player player) {
      if (!this.access.enabled() && !SuggestionAccess.isAdmin(player)) {
         Messages.error(player, Messages.SUGGESTIONS_DISABLED);
         return false;
      }
      if (!this.access.canUse(player)) {
         Messages.error(player, Messages.NO_PERMISSION);
         return false;
      }
      return true;
   }

   /** Loads data off-thread, then runs {@code then} on the player's thread. */
   <T> void load(Player player, CompletableFuture<T> future, Consumer<T> then) {
      future.whenComplete((value, error) -> player.getScheduler().run(this.plugin, task -> {
         if (error != null) {
            this.plugin.getLogger().log(Level.SEVERE, "Suggestion database error", error);
            Messages.error(player, Messages.ACTION_FAILED);
            return;
         }
         then.accept(value);
      }, null));
   }

   /** Like {@link #load} for guarded operations: always releases the player's busy flag. */
   <T> void finish(Player player, CompletableFuture<T> future, Consumer<T> then) {
      UUID id = player.getUniqueId();
      future.whenComplete((value, error) -> {
         this.guard.end(id);
         player.getScheduler().run(this.plugin, task -> {
            if (error != null) {
               this.plugin.getLogger().log(Level.SEVERE, "Suggestion database error", error);
               Messages.error(player, Messages.ACTION_FAILED);
               return;
            }
            then.accept(value);
         }, null);
      });
   }
}
