package net.srv.legendaryadditions.admin.suggestion.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.SuggestionService;
import net.srv.legendaryadditions.admin.suggestion.data.Page;
import net.srv.legendaryadditions.admin.suggestion.data.Results;
import net.srv.legendaryadditions.admin.suggestion.data.SortOrder;
import net.srv.legendaryadditions.admin.suggestion.data.Suggestion;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionCategory;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionStatus;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The administrator backend. Security model:
 * <ul>
 *    <li>Opening any admin screen checks {@code admindimension.suggestions.admin} first.</li>
 *    <li>Admin screens are flagged adminOnly, so {@link MenuListener} re-checks the permission on every click.</li>
 *    <li>Every action checks the permission again immediately before it is queued, so a permission
 *        removed while the GUI is open takes effect on the next click.</li>
 *    <li>Every database change is conditional on the status the admin saw, so stale screens, double
 *        clicks and two admins acting at once cannot overwrite each other.</li>
 * </ul>
 */
public final class AdminSuggestionGui {
   private final Plugin plugin;
   private final SuggestionService service;
   private final SuggestionAccess access;
   private final SuggestionFormat format;
   private final ClickGuard guard;
   private final SuggestionGui publicGui;
   private net.srv.legendaryadditions.forge.gui.LegendaryCreatorGui creator;

   public AdminSuggestionGui(Plugin plugin, SuggestionService service, SuggestionAccess access, SuggestionFormat format,
                             ClickGuard guard, SuggestionGui publicGui) {
      this.plugin = plugin;
      this.service = service;
      this.access = access;
      this.format = format;
      this.guard = guard;
      this.publicGui = publicGui;
   }

   /** Adds the Legendary Creator button (slot 52) to the suggestion list screens. */
   public void setCreator(net.srv.legendaryadditions.forge.gui.LegendaryCreatorGui creator) {
      this.creator = creator;
      creator.setBack(p -> this.openList(p, SuggestionStatus.PENDING, 0));
   }

   private boolean requireAdmin(Player player) {
      if (SuggestionAccess.isAdmin(player)) {
         return true;
      }
      player.closeInventory();
      Messages.error(player, Messages.NO_PERMISSION);
      return false;
   }

   // ------------------------------------------------------------------ status lists

   public void openList(Player player, SuggestionStatus status, int page) {
      if (!this.requireAdmin(player)) {
         return;
      }
      UUID id = player.getUniqueId();
      SortOrder sort = status == SuggestionStatus.PENDING ? SortOrder.OLDEST : this.publicGui.sort(id);
      this.publicGui.load(player,
            this.service.submit(repo -> repo.page(status, null, sort, page, SuggestionGui.PAGE_SIZE, id)),
            result -> {
               if (this.requireAdmin(player)) {
                  this.renderList(player, status, sort, result);
               }
            });
   }

   private void renderList(Player player, SuggestionStatus status, SortOrder sort, Page<Suggestion> page) {
      Menu menu = new Menu(player.getUniqueId(), 6,
            Items.text("Admin: " + status.name() + " (" + (page.page() + 1) + "/" + page.pageCount() + ")", NamedTextColor.DARK_RED), true);
      int slot = 0;
      for (Suggestion s : page.items()) {
         long suggestionId = s.id();
         List<Component> lore = new ArrayList<>();
         lore.add(Items.text("Status: " + s.status(), NamedTextColor.GRAY));
         lore.add(Items.text("Requested: " + SuggestionFormat.categoryName(s.requestedCategory()), NamedTextColor.GRAY));
         lore.add(Items.text("Category: " + SuggestionFormat.categoryName(s.category()), NamedTextColor.GRAY));
         lore.add(Items.text("Votes: " + s.votes(), NamedTextColor.GOLD));
         lore.add(Items.text("By: " + this.format.author(s, true), NamedTextColor.GRAY));
         lore.add(Items.text("Submitted: " + this.format.date(s.createdAt()), NamedTextColor.GRAY));
         lore.add(Items.text("Click to review", NamedTextColor.DARK_GRAY));
         menu.set(slot++, Items.icon(Material.PAPER, SuggestionFormat.title(s), lore, false),
               (p, c) -> this.openReview(p, suggestionId, status, page.page()));
      }
      if (page.items().isEmpty()) {
         menu.set(22, Items.icon(Material.BARRIER, "Nothing " + status.name().toLowerCase() + " right now", NamedTextColor.GRAY));
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, this.tab(Material.CLOCK, "Pending Suggestions", SuggestionStatus.PENDING, status), (p, c) -> this.openList(p, SuggestionStatus.PENDING, 0));
      menu.set(46, this.tab(Material.LIME_CONCRETE, "Approved Suggestions", SuggestionStatus.APPROVED, status), (p, c) -> this.openList(p, SuggestionStatus.APPROVED, 0));
      menu.set(47, this.tab(Material.RED_CONCRETE, "Rejected Suggestions", SuggestionStatus.REJECTED, status), (p, c) -> this.openList(p, SuggestionStatus.REJECTED, 0));
      menu.set(48, this.tab(Material.CHEST, "Archived Suggestions", SuggestionStatus.ARCHIVED, status), (p, c) -> this.openList(p, SuggestionStatus.ARCHIVED, 0));
      if (page.hasPrevious()) {
         menu.set(49, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openList(p, status, page.page() - 1));
      }
      if (page.hasNext()) {
         menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openList(p, status, page.page() + 1));
      }
      menu.set(51, Items.icon(Material.HOPPER, "Order: " + sort.label(), NamedTextColor.AQUA,
            status == SuggestionStatus.PENDING ? "Pending is always oldest first." : "Uses your public sort order."));
      if (this.creator != null) {
         menu.set(52, Items.icon(Material.NETHER_STAR, "Legendary Creator", NamedTextColor.LIGHT_PURPLE,
               "Make new legendary items with", "abilities and enchantments."), (p, c) -> this.creator.openList(p, 0));
      }
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   private ItemStack tab(Material material, String name, SuggestionStatus tab, SuggestionStatus active) {
      boolean selected = tab == active;
      return Items.icon(material, Items.text(name, selected ? NamedTextColor.GOLD : NamedTextColor.YELLOW),
            List.of(Items.text(selected ? "Currently viewing" : "Click to view", NamedTextColor.GRAY)), selected);
   }

   // ------------------------------------------------------------------ review

   public void openReview(Player player, long suggestionId, SuggestionStatus backStatus, int backPage) {
      if (!this.requireAdmin(player)) {
         return;
      }
      UUID id = player.getUniqueId();
      this.publicGui.load(player, this.service.submit(repo -> repo.find(suggestionId, id)), found -> {
         if (!this.requireAdmin(player)) {
            return;
         }
         if (found.isEmpty()) {
            Messages.error(player, "Suggestion #" + suggestionId + " no longer exists.");
            this.openList(player, backStatus, backPage);
            return;
         }
         this.renderReview(player, found.get(), backStatus, backPage);
      });
   }

   private void renderReview(Player player, Suggestion s, SuggestionStatus backStatus, int backPage) {
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Review #" + s.id() + " - " + s.status(), NamedTextColor.DARK_RED), true);
      for (int i = 0; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      long id = s.id();
      SuggestionStatus seen = s.status();
      Runnable reopen = () -> this.openReview(player, id, backStatus, backPage);

      menu.set(4, Items.icon(Material.WRITTEN_BOOK, Items.text("Full suggestion text", NamedTextColor.GOLD),
            SuggestionFormat.fullText(s.text()), false), (p, c) -> {
         p.sendMessage(Items.text("Suggestion #" + id + ":", NamedTextColor.GOLD));
         p.sendMessage(Component.text(s.text(), NamedTextColor.WHITE));
      });

      List<Component> info = new ArrayList<>();
      info.add(Items.text("ID: " + s.id(), NamedTextColor.GRAY));
      info.add(Items.text("Submitted by: " + this.format.author(s, true), NamedTextColor.GRAY));
      info.add(Items.text("Submitted: " + this.format.date(s.createdAt()), NamedTextColor.GRAY));
      info.add(Items.text("Status: " + s.status(), NamedTextColor.YELLOW));
      info.add(Items.text("Requested category: " + SuggestionFormat.categoryName(s.requestedCategory()), NamedTextColor.GRAY));
      info.add(Items.text("Assigned category: " + SuggestionFormat.categoryName(s.category()), NamedTextColor.GRAY));
      info.add(Items.text("Votes: " + s.votes(), NamedTextColor.GOLD));
      if (s.approvedAt() != null) {
         info.add(Items.text("Approved by: " + s.approvedByName() + " (" + s.approvedByUuid() + ")", NamedTextColor.GREEN));
         info.add(Items.text("Approved at: " + this.format.date(s.approvedAt()), NamedTextColor.GREEN));
      }
      if (s.reviewedAt() != null) {
         info.add(Items.text("Last reviewed by: " + s.reviewedByName() + " at " + this.format.date(s.reviewedAt()), NamedTextColor.GRAY));
      }
      menu.set(13, Items.icon(Material.NAME_TAG, Items.text("Suggestion details", NamedTextColor.AQUA), info, false));

      if (seen != SuggestionStatus.APPROVED) {
         menu.set(28, Items.icon(Material.LIME_WOOL, "Approve as Legendary", NamedTextColor.GREEN,
               "Makes it public in Legendary Suggestions."), (p, c) -> this.approve(p, id, seen, SuggestionCategory.LEGENDARY, reopen));
         menu.set(29, Items.icon(Material.GREEN_WOOL, "Approve as Server", NamedTextColor.GREEN,
               "Makes it public in Server Suggestions."), (p, c) -> this.approve(p, id, seen, SuggestionCategory.SERVER, reopen));
      } else {
         SuggestionCategory target = s.category() == null ? SuggestionCategory.LEGENDARY : s.category().other();
         menu.set(29, Items.icon(Material.ANVIL, "Change Category", NamedTextColor.AQUA,
               "Currently: " + SuggestionFormat.categoryName(s.category()),
               "Click to move it to " + SuggestionFormat.categoryName(target) + "."),
               (p, c) -> this.changeCategory(p, id, seen, target, reopen));
      }
      if (seen == SuggestionStatus.PENDING || seen == SuggestionStatus.APPROVED) {
         menu.set(31, Items.icon(Material.RED_WOOL, "Reject", NamedTextColor.RED, "Hides it from the public tabs."),
               (p, c) -> this.confirm(p, "Reject suggestion #" + id + "?", () -> this.reject(p, id, seen, reopen), reopen));
      }
      if (seen != SuggestionStatus.ARCHIVED) {
         menu.set(33, Items.icon(Material.CHEST, "Archive", NamedTextColor.GOLD, "Moves it to the archive (hidden from players)."),
               (p, c) -> this.confirm(p, "Archive suggestion #" + id + "?", () -> this.archive(p, id, seen, reopen), reopen));
      }
      if (this.access.config().allowDelete()) {
         menu.set(34, Items.icon(Material.LAVA_BUCKET, "Delete permanently", NamedTextColor.DARK_RED,
               "Removes the suggestion and all its votes.", "This cannot be undone."),
               (p, c) -> this.confirm(p, "DELETE suggestion #" + id + " forever?", () -> this.delete(p, id, seen, backStatus, backPage), reopen));
      }
      menu.set(40, Items.icon(Material.BOOK, "View voters (" + s.votes() + ")", NamedTextColor.AQUA, "Who voted and when."),
            (p, c) -> this.openVoters(p, id, 0, reopen));
      menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openList(p, backStatus, backPage));
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   // ------------------------------------------------------------------ actions

   private void approve(Player admin, long id, SuggestionStatus seen, SuggestionCategory category, Runnable reopen) {
      UUID adminId = admin.getUniqueId();
      String adminName = admin.getName();
      this.run(admin, repo -> repo.approve(id, seen, category, adminId, adminName, System.currentTimeMillis()),
            "Approved #" + id + " as " + SuggestionFormat.categoryName(category) + ".",
            id, "was approved and is now public in " + category.title() + ".", reopen);
   }

   private void reject(Player admin, long id, SuggestionStatus seen, Runnable reopen) {
      UUID adminId = admin.getUniqueId();
      String adminName = admin.getName();
      this.run(admin, repo -> repo.reject(id, seen, adminId, adminName, System.currentTimeMillis()),
            "Rejected #" + id + ".", id, "was rejected by an admin.", reopen);
   }

   private void archive(Player admin, long id, SuggestionStatus seen, Runnable reopen) {
      UUID adminId = admin.getUniqueId();
      String adminName = admin.getName();
      this.run(admin, repo -> repo.archive(id, seen, adminId, adminName, System.currentTimeMillis()),
            "Archived #" + id + ".", id, "was archived.", reopen);
   }

   private void changeCategory(Player admin, long id, SuggestionStatus seen, SuggestionCategory category, Runnable reopen) {
      this.run(admin, repo -> repo.changeCategory(id, seen, category, System.currentTimeMillis()),
            "Moved #" + id + " to " + category.title() + ".", id, null, reopen);
   }

   private void delete(Player admin, long id, SuggestionStatus seen, SuggestionStatus backStatus, int backPage) {
      if (!this.access.config().allowDelete()) {
         Messages.error(admin, "Deleting suggestions is disabled in the config.");
         return;
      }
      this.run(admin, repo -> repo.delete(id, seen), "Deleted #" + id + " permanently.", id, null,
            () -> this.openList(admin, backStatus, backPage));
   }

   /**
    * Shared path for every admin change: permission check right before queueing, one operation in
    * flight per admin, guarded update in the database, then feedback and a refreshed screen.
    */
   private void run(Player admin, SuggestionService.Call<Results.ChangeResult> change, String success, long id,
                    String authorNotice, Runnable after) {
      if (!this.requireAdmin(admin) || !this.guard.begin(admin.getUniqueId())) {
         return;
      }
      this.publicGui.finish(admin, this.service.submit(repo -> {
         Results.ChangeResult result = change.run(repo);
         UUID author = result == Results.ChangeResult.OK && authorNotice != null
               ? repo.find(id, admin.getUniqueId()).map(Suggestion::authorUuid).orElse(null)
               : null;
         return new Outcome(result, author);
      }), outcome -> {
         switch (outcome.result()) {
            case OK -> {
               Messages.success(admin, success);
               this.plugin.getLogger().info(admin.getName() + ": " + success);
               if (outcome.author() != null) {
                  this.notifyAuthor(outcome.author(), "Your suggestion #" + id + " " + authorNotice);
               }
            }
            case NOT_FOUND -> Messages.error(admin, "Suggestion #" + id + " no longer exists.");
            case CHANGED_BY_SOMEONE_ELSE -> Messages.error(admin, "Suggestion #" + id + " was changed by someone else - showing the latest version.");
            case NOT_ALLOWED -> Messages.error(admin, "That action does not apply to this suggestion's current status.");
         }
         if (SuggestionAccess.isAdmin(admin)) {
            after.run();
         } else {
            admin.closeInventory();
         }
      });
   }

   private record Outcome(Results.ChangeResult result, UUID author) {}

   private void notifyAuthor(UUID author, String message) {
      Player online = Bukkit.getPlayer(author);
      if (online != null) {
         online.getScheduler().run(this.plugin, task -> Messages.info(online, message), null);
      }
   }

   private void confirm(Player player, String question, Runnable onConfirm, Runnable onCancel) {
      if (!this.requireAdmin(player)) {
         return;
      }
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Are you sure?", NamedTextColor.DARK_RED), true);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(4, Items.icon(Material.PAPER, question, NamedTextColor.YELLOW));
      boolean[] used = {false};
      menu.set(11, Items.icon(Material.LIME_CONCRETE, "Confirm", NamedTextColor.GREEN), (p, c) -> {
         if (!used[0] && this.requireAdmin(p)) {
            used[0] = true;
            onConfirm.run();
         }
      });
      menu.set(15, Items.icon(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED), (p, c) -> onCancel.run());
      player.openInventory(menu.getInventory());
   }

   // ------------------------------------------------------------------ voting information

   private void openVoters(Player player, long id, int page, Runnable back) {
      if (!this.requireAdmin(player)) {
         return;
      }
      this.publicGui.load(player, this.service.submit(repo -> {
         Page<Results.Voter> voters = repo.voters(id, page, SuggestionGui.PAGE_SIZE);
         List<String> names = new ArrayList<>();
         for (Results.Voter voter : voters.items()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(voter.playerUuid());
            names.add(offline.getName() != null ? offline.getName() : voter.playerUuid().toString());
         }
         return new VoterPage(voters, names);
      }), result -> {
         if (!this.requireAdmin(player)) {
            return;
         }
         Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Voters for #" + id + " (" + result.voters().total() + ")", NamedTextColor.DARK_RED), true);
         for (int i = 0; i < result.voters().items().size(); i++) {
            Results.Voter voter = result.voters().items().get(i);
            menu.set(i, Items.icon(Material.PAPER, result.names().get(i), NamedTextColor.WHITE,
                  "UUID: " + voter.playerUuid(), "Voted: " + this.format.date(voter.votedAt())));
         }
         for (int i = 45; i < 54; i++) {
            menu.set(i, Items.filler());
         }
         if (result.voters().hasPrevious()) {
            menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openVoters(p, id, page - 1, back));
         }
         if (result.voters().hasNext()) {
            menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openVoters(p, id, page + 1, back));
         }
         menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> back.run());
         menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
         player.openInventory(menu.getInventory());
      });
   }

   private record VoterPage(Page<Results.Voter> voters, List<String> names) {}
}
