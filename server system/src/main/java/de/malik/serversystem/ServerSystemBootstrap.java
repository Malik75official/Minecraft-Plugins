package de.malik.serversystem;

import de.malik.serversystem.economy.EconomySystem;
import de.malik.serversystem.inventory.InventorySyncSystem;
import de.malik.serversystem.links.LinkCommandSystem;
import de.malik.serversystem.moderation.ModerationSystem;
import de.malik.serversystem.prefix.PrefixSystem;
import de.malik.serversystem.sidebar.SidebarSystem;
import de.malik.serversystem.spawn.SpawnSystem;
import de.malik.serversystem.storage.YamlStore;
import de.malik.serversystem.teleport.HomeWarpSystem;
import de.malik.serversystem.teleport.FarmweltSystem;
import de.malik.serversystem.utility.UtilitySystem;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerSystemBootstrap extends JavaPlugin {
    private YamlStore playerData;
    private YamlStore warpData;
    private YamlStore moderationData;
    private PrefixSystem prefixSystem;
    private SidebarSystem sidebarSystem;
    private InventorySyncSystem inventorySyncSystem;

    @Override
    public void onEnable() {
        printStartupBanner();
        saveDefaultConfig();
        inventorySyncSystem = new InventorySyncSystem(this);
        if (inventorySyncSystem.isEnabled()) {
            try {
                inventorySyncSystem.initialize();
            } catch (java.sql.SQLException exception) {
                getLogger().severe("MySQL-Inventarsynchronisierung konnte nicht gestartet werden: " + exception.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        playerData = new YamlStore(this, "players.yml");
        warpData = new YamlStore(this, "warps.yml");
        moderationData = new YamlStore(this, "moderation.yml");

        EconomySystem economy = new EconomySystem(this, playerData);
        HomeWarpSystem homeWarps = new HomeWarpSystem(playerData, warpData);
        FarmweltSystem farmwelt = new FarmweltSystem(this);
        SpawnSystem spawn = new SpawnSystem(this);
        ModerationSystem moderation = new ModerationSystem(moderationData);
        prefixSystem = new PrefixSystem(this);
        sidebarSystem = new SidebarSystem(this, economy, prefixSystem);
        UtilitySystem utilities = new UtilitySystem();
        LinkCommandSystem linkCommands = new LinkCommandSystem(this);

        registerCommands(economy, "balance", "pay", "payall", "eco");
        registerCommands(homeWarps, "sethome", "home", "delhome", "homes", "setwarp", "warp", "delwarp");
        registerCommands(farmwelt, "farmwelt");
        registerCommands(spawn, "setspawn", "spawn");
        registerCommands(utilities, "ec", "craft", "amboss", "ofen");
        registerCommands(linkCommands, "discord", "webseite", "webshop");
        registerCommands(moderation, "kick", "ban", "unban", "mute", "unmute");

        registerEvents(spawn, moderation, prefixSystem, sidebarSystem);
        if (inventorySyncSystem.isEnabled()) {
            registerEvents(inventorySyncSystem);
        }
        prefixSystem.start();
        sidebarSystem.start();
        inventorySyncSystem.start();
    }

    private void printStartupBanner() {
        String[] banner = {
                "",
                "   _____ ______ _____  _____ _______ ______ _____  _____ __  __",
                "  / ____|  ____|  __ \\|_   _|__   __|  ____|  __ \\|_   _|  \\/  |",
                " | (___ | |__  | |__) | | |    | |  | |__  | |__) | | | | \\/| |",
                "  \\___ \\|  __| |  _  /  | |    | |  |  __| |  _  /  | | | . ` |",
                "  ____) | |____| | \\ \\ _| |_   | |  | |____| | \\ \\ _| |_| |\\  |",
                " |_____/|______|_|  \\_\\_____|  |_|  |______|_|  \\_\\_____|_| \\_|",
                "",
                "                    Server System by Malik75",
                ""
        };
        for (String line : banner) {
            getLogger().info(line);
        }
    }

    @Override
    public void onDisable() {
        if (inventorySyncSystem != null) {
            inventorySyncSystem.shutdown();
        }
        if (sidebarSystem != null) {
            sidebarSystem.shutdown();
        }
        if (prefixSystem != null) {
            prefixSystem.shutdown();
        }
        save(playerData);
        save(warpData);
        save(moderationData);
    }

    private void registerCommands(CommandExecutor executor, String... names) {
        for (String name : names) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                getLogger().severe("Command '" + name + "' fehlt in plugin.yml.");
                continue;
            }
            command.setExecutor(executor);
        }
    }

    private void registerEvents(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void save(YamlStore store) {
        if (store != null) {
            store.save();
        }
    }
}