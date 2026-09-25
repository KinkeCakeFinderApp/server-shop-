package net.srv.eloranks.reward;

import java.util.List;
import org.bukkit.Material;

/** A reward from config.yml (rewards.&lt;id&gt;). */
public record RewardDef(String id, String name, Material icon, List<String> description, Container container,
                        String containerName, List<RewardItemDef> items) {
   public enum Container { BARREL, NONE }
}
