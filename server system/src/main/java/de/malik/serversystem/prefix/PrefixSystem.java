package de.malik.serversystem.prefix;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import de.malik.serversystem.storage.PersistentYamlStorage;

public final class PrefixSystem implements Listener {
    private final JavaPlugin plugin;
    private final FileConfiguration prefixConfig;
    private final ConcurrentMap<UUID, PrefixDefinition> activePrefixes = new ConcurrentHashMap<>();
    private final Map<String, PrefixDefinition> prefixDefinitions = new LinkedHashMap<>();
    private final Map<Scoreboard, Map<String, Team>> prefixTeams = new HashMap<>();
    private final Map<UUID, String> appliedPrefixIds = new HashMap<>();
    private final Map<UUID, Scoreboard> appliedScoreboards = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String chatFormat;
    private final String tabFormat;
    private final String nametagFormat;

    public PrefixSystem(JavaPlugin plugin, PersistentYamlStorage storage) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "prefixes.yml");
        if (!file.exists() && !storage.hasData("prefixes.yml")) {
            plugin.saveResource("prefixes.yml", false);
        }
        prefixConfig = storage.load("prefixes.yml", file);
        chatFormat = prefixConfig.getString("chat-format", "<prefix> <player><dark_gray>: <message>");
        tabFormat = prefixConfig.getString("tab-format", "<prefix> <player>");
        nametagFormat = prefixConfig.getString("nametag-format", "<prefix> ");
        loadPrefixes();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshOnlinePrefixes, 1L, 20L);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(Component.text(player.getName()));
        }
        for (Map<String, Team> teams : prefixTeams.values()) {
            for (Team team : teams.values()) {
                team.unregister();
            }
        }
        prefixTeams.clear();
        activePrefixes.clear();
        appliedPrefixIds.clear();
        appliedScoreboards.clear();
    }

    private void loadPrefixes() {
        for (String id : prefixConfig.getStringList("priority")) {
            String path = "prefixes." + id;
            String permission = prefixConfig.getString(path + ".permission", "");
            if (permission.isBlank()) {
                plugin.getLogger().warning("Prefix '" + id + "' hat keine Permission und wird ignoriert.");
                continue;
            }

            String text = prefixConfig.getString(path + ".text", "");
            String nameColor = prefixConfig.getString(path + ".name-color", "<white>");
            PrefixDefinition definition = new PrefixDefinition(permission, nameColor, miniMessage.deserialize(text));
            prefixDefinitions.put(id, definition);
        }
    }

    private String uniqueTeamName(Scoreboard scoreboard, String id) {
        String baseName = "sspx" + Integer.toUnsignedString(id.hashCode(), 36);
        String candidate = baseName;
        int suffixIndex = 1;
        while (scoreboard.getTeam(candidate) != null) {
            String suffix = Integer.toString(suffixIndex++, 36);
            int baseLength = Math.min(baseName.length(), 16 - suffix.length());
            candidate = baseName.substring(0, baseLength) + suffix;
        }
        return candidate;
    }

    private void refreshOnlinePrefixes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPrefix(player);
        }
    }

    public void refreshPrefix(Player player) {
        String selectedId = null;
        PrefixDefinition selected = null;
        for (Map.Entry<String, PrefixDefinition> entry : prefixDefinitions.entrySet()) {
            if (player.hasPermission(entry.getValue().permission())) {
                selectedId = entry.getKey();
                selected = entry.getValue();
                break;
            }
        }

        UUID playerId = player.getUniqueId();
        String previousId = appliedPrefixIds.get(playerId);
        Scoreboard scoreboard = player.getScoreboard();
        Scoreboard previousScoreboard = appliedScoreboards.get(playerId);
        if (Objects.equals(previousId, selectedId) && previousScoreboard == scoreboard) {
            return;
        }
        if (previousId != null) {
            Team previousTeam = getTeam(previousScoreboard, previousId);
            if (previousTeam != null) {
                previousTeam.removeEntry(player.getName());
            }
        }

        if (selected == null) {
            appliedPrefixIds.remove(playerId);
            appliedScoreboards.remove(playerId);
            activePrefixes.remove(playerId);
            player.playerListName(Component.text(player.getName()));
            return;
        }

        Team selectedTeam = getOrCreateTeam(scoreboard, selectedId, selected);
        selectedTeam.addEntry(player.getName());
        appliedPrefixIds.put(playerId, selectedId);
        appliedScoreboards.put(playerId, scoreboard);
        activePrefixes.put(playerId, selected);
        player.playerListName(renderPrefix(tabFormat, selected, player.getName()));
    }

    private Team getTeam(Scoreboard scoreboard, String id) {
        Map<String, Team> teams = prefixTeams.get(scoreboard);
        return teams == null ? null : teams.get(id);
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String id, PrefixDefinition definition) {
        Map<String, Team> teams = prefixTeams.computeIfAbsent(scoreboard, ignored -> new HashMap<>());
        Team team = teams.get(id);
        if (team != null) {
            return team;
        }
        team = scoreboard.registerNewTeam(uniqueTeamName(scoreboard, id));
        team.prefix(renderPrefix(nametagFormat, definition, null));
        teams.put(id, team);
        return team;
    }

    private Component renderPrefix(String format, PrefixDefinition definition, String playerName) {
        Component playerComponent = playerName == null
                ? Component.empty()
                : miniMessage.deserialize(definition.nameColor() + "<name>", Placeholder.unparsed("name", playerName));
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("prefix", definition.component()))
                .resolver(Placeholder.component("player", playerComponent))
                .build();
        return miniMessage.deserialize(format, resolver);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshPrefix(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String prefixId = appliedPrefixIds.remove(playerId);
        Scoreboard scoreboard = appliedScoreboards.remove(playerId);
        activePrefixes.remove(playerId);
        if (prefixId != null) {
            Team team = getTeam(scoreboard, prefixId);
            if (team != null) {
                team.removeEntry(event.getPlayer().getName());
            }
        }
        if (scoreboard != null && scoreboard != Bukkit.getScoreboardManager().getMainScoreboard()) {
            Map<String, Team> teams = prefixTeams.remove(scoreboard);
            if (teams != null) {
                for (Team team : teams.values()) {
                    team.unregister();
                }
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        PrefixDefinition definition = activePrefixes.get(event.getPlayer().getUniqueId());
        if (definition == null) {
            return;
        }
        String playerName = event.getPlayer().getName();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component playerComponent = miniMessage.deserialize(definition.nameColor() + "<name>",
                    Placeholder.unparsed("name", playerName));
            TagResolver resolver = TagResolver.builder()
                    .resolver(Placeholder.component("prefix", definition.component()))
                    .resolver(Placeholder.component("player", playerComponent))
                    .resolver(Placeholder.component("message", message))
                    .build();
            return miniMessage.deserialize(chatFormat, resolver);
        });
    }

    private record PrefixDefinition(String permission, String nameColor, Component component) {
    }
}