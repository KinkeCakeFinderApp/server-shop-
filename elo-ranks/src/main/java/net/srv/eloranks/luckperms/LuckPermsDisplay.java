package net.srv.eloranks.luckperms;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.rank.Tier;

/**
 * Keeps the player's tier in LuckPerms through the LuckPerms API.
 *
 * <p>Only data this plugin owns is ever changed:</p>
 * <ul>
 *   <li>prefix mode: the one prefix node this plugin added. Its text is remembered in the meta
 *       key {@value #PREFIX_KEY}, so it is found even after the configured prefixes change. Other
 *       prefixes are never touched.</li>
 *   <li>group mode: only membership of the groups listed under luckperms.groups (and the group
 *       this plugin added last, remembered in {@value #GROUP_KEY}).</li>
 *   <li>the meta keys {@value #TIER_KEY}, {@value #PREFIX_KEY} and {@value #GROUP_KEY}.</li>
 * </ul>
 * Permissions, other groups, other prefixes and other meta are left alone.
 */
public final class LuckPermsDisplay implements RankDisplay {
   public static final String TIER_KEY = "eloranks-tier";
   public static final String PREFIX_KEY = "eloranks-prefix";
   public static final String GROUP_KEY = "eloranks-group";

   private final LuckPerms api;
   private final Supplier<Settings> settings;
   private final Logger logger;

   public LuckPermsDisplay(Supplier<Settings> settings, Logger logger) {
      this.api = LuckPermsProvider.get();
      this.settings = settings;
      this.logger = logger;
   }

   /** Warns about groups from the config that LuckPerms does not have. */
   public void checkGroups() {
      Settings.LuckPerms lp = this.settings.get().luckPerms();
      if (lp.mode() != Settings.LuckPermsMode.GROUP) {
         return;
      }
      for (String group : new HashSet<>(lp.groups().values())) {
         if (group.isBlank()) {
            continue;
         }
         this.api.getGroupManager().loadGroup(group).thenAccept(found -> {
            if (found.isEmpty()) {
               this.logger.warning("luckperms.groups: the LuckPerms group '" + group + "' does not exist. Create it with "
                     + "/lp creategroup " + group + " or change luckperms.groups in config.yml.");
            }
         });
      }
   }

   @Override
   public void apply(UUID player, Optional<Tier> tier) {
      Settings settings = this.settings.get();
      Settings.LuckPerms lp = settings.luckPerms();
      String tierId = tier.map(Tier::id).orElse("unranked");
      String tierValue = tier.map(t -> String.valueOf(t.number())).orElse("unranked");
      this.api.getUserManager().modifyUser(player, user -> {
         this.setMeta(user, TIER_KEY, tierValue);
         this.updatePrefix(user, lp, lp.mode() == Settings.LuckPermsMode.PREFIX ? lp.prefix(tierId) : "");
         this.updateGroup(user, lp, lp.mode() == Settings.LuckPermsMode.GROUP ? lp.group(tierId) : "");
      }).exceptionally(ex -> {
         this.logger.log(Level.WARNING, "Could not update the LuckPerms rank of " + player, ex);
         return null;
      });
   }

   private void updatePrefix(User user, Settings.LuckPerms lp, String prefix) {
      String previous = this.meta(user, PREFIX_KEY);
      Set<String> owned = new HashSet<>(lp.prefixes().values());
      if (previous != null) {
         owned.add(previous);
      }
      owned.remove("");
      // Only our own prefix: same priority and a text this plugin uses or used.
      user.data().clear(NodeType.PREFIX.predicate(n -> n.getPriority() == lp.priority() && owned.contains(n.getMetaValue())));
      if (previous != null) {
         // A prefix from before the priority was changed.
         user.data().clear(NodeType.PREFIX.predicate(n -> n.getMetaValue().equals(previous)));
      }
      this.setMeta(user, PREFIX_KEY, prefix.isEmpty() ? null : prefix);
      if (!prefix.isEmpty()) {
         user.data().add(PrefixNode.builder(prefix, lp.priority()).build());
      }
   }

   private void updateGroup(User user, Settings.LuckPerms lp, String group) {
      String previous = this.meta(user, GROUP_KEY);
      Set<String> owned = new HashSet<>();
      lp.groups().values().forEach(g -> owned.add(g.toLowerCase(Locale.ROOT)));
      if (previous != null) {
         owned.add(previous.toLowerCase(Locale.ROOT));
      }
      owned.remove("");
      user.data().clear(NodeType.INHERITANCE.predicate(n -> owned.contains(n.getGroupName().toLowerCase(Locale.ROOT))));
      this.setMeta(user, GROUP_KEY, group.isEmpty() ? null : group);
      if (!group.isEmpty()) {
         user.data().add(InheritanceNode.builder(group).build());
      }
   }

   private String meta(User user, String key) {
      return user.getNodes(NodeType.META).stream().filter(n -> n.getMetaKey().equals(key))
            .map(MetaNode::getMetaValue).findFirst().orElse(null);
   }

   private void setMeta(User user, String key, String value) {
      user.data().clear(NodeType.META.predicate(n -> n.getMetaKey().equals(key)));
      if (value != null) {
         user.data().add(MetaNode.builder(key, value).build());
      }
   }
}
