package de.malik.serversystem.sidebar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import de.malik.serversystem.economy.EconomySystem;
import de.malik.serversystem.prefix.PrefixSystem;
import de.malik.serversystem.storage.PersistentYamlStorage;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class SidebarSystem implements Listener {
    private static final int MAX_LINES = 15;

    private final JavaPlugin plugin;
    private final EconomySystem economy;
    private final PrefixSystem prefixSystem;
    private final FileConfiguration config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, SidebarView> views = new HashMap<>();
    private final String serverName;
    private final String title;
    private final List<String> lines;

        public SidebarSystem(JavaPlugin plugin, EconomySystem economy, PrefixSystem prefixSystem,
            PersistentYamlStorage storage) {
        this.plugin = plugin;
        this.economy = economy;
        this.prefixSystem = prefixSystem;

        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.exists() && !storage.hasData("scoreboard.yml")) {
            plugin.saveResource("scoreboard.yml", false);
        }
        config = storage.load("scoreboard.yml", file);
        serverName = config.getString("server-name", "Server System");
        title = config.getString("title", "<gold><bold>{server_name}</bold></gold>");
        List<String> configuredLines = config.getStringList("lines");
        if (configuredLines.size() > MAX_LINES) {
            plugin.getLogger().warning("scoreboard.yml hat mehr als 15 Zeilen; zusätzliche Zeilen werden ignoriert.");
        }
        lines = new ArrayList<>(configuredLines.subList(0, Math.min(configuredLines.size(), MAX_LINES)));
    }

    public void start() {
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateOnlinePlayers, 1L, 20L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void shutdown() {
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Map.Entry<UUID, SidebarView> entry : views.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            SidebarView view = entry.getValue();
            if (player != null && player.getScoreboard() == view.scoreboard()) {
                player.setScoreboard(mainScoreboard);
            }
            view.objective().unregister();
        }
        views.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
    }

    private void updateOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        SidebarView view = views.get(playerId);
        if (view == null) {
            view = createView(player);
            views.put(playerId, view);
            player.setScoreboard(view.scoreboard());
            prefixSystem.refreshPrefix(player);
        } else if (player.getScoreboard() != view.scoreboard()) {
            player.setScoreboard(view.scoreboard());
            prefixSystem.refreshPrefix(player);
        }

        view.objective().displayName(render(title, player));
        for (int index = 0; index < view.lines().size(); index++) {
            view.lineTeams().get(index).prefix(render(view.lines().get(index), player));
        }
    }

    private SidebarView createView(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "sssidebar", Criteria.DUMMY, render(title, player));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        List<Team> lineTeams = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Team team = scoreboard.registerNewTeam("ssline" + Integer.toString(index, 36));
            String entry = ChatColor.values()[index].toString();
            team.addEntry(entry);
            team.prefix(render(lines.get(index), player));
            objective.getScore(entry).setScore(lines.size() - index);
            lineTeams.add(team);
        }
        return new SidebarView(scoreboard, objective, lineTeams, lines);
    }

    private Component render(String template, Player player) {
        String miniMessageTemplate = template
                .replace("{server_name}", "<server_name>")
                .replace("{server}", "<server_name>")
                .replace("{player}", "<player>")
                .replace("{player_name}", "<player>")
                .replace("{player_money}", "<player_money>")
                .replace("{online_players}", "<online_players>")
                .replace("{max_players}", "<max_players>")
                .replace("{world}", "<world>");
        TagResolver placeholders = TagResolver.builder()
                .resolver(Placeholder.unparsed("server_name", serverName))
                .resolver(Placeholder.unparsed("player", player.getName()))
                .resolver(Placeholder.unparsed("player_money", economy.getFormattedBalance(player.getUniqueId())))
                .resolver(Placeholder.unparsed("online_players", Integer.toString(Bukkit.getOnlinePlayers().size())))
                .resolver(Placeholder.unparsed("max_players", Integer.toString(Bukkit.getMaxPlayers())))
                .resolver(Placeholder.unparsed("world", player.getWorld().getName()))
                .build();
        return miniMessage.deserialize(miniMessageTemplate, placeholders);
    }

    private record SidebarView(Scoreboard scoreboard, Objective objective, List<Team> lineTeams, List<String> lines) {
    }
}