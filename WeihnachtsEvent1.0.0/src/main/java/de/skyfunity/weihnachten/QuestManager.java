package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import java.util.List;

public class QuestManager {
    private final WeihnachtsPlugin plugin;

    public QuestManager(WeihnachtsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Placeholder: quest scheduler can be configured later
        plugin.getLogger().info("Quest-System geladen (Zeiten in config.yml einstellbar).");
    }
}