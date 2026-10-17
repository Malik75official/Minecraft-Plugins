package de.malik.mobsystem;

import org.bukkit.plugin.java.JavaPlugin;

public final class MobSystemPlugin extends JavaPlugin {
    private MobDisplayManager mobDisplayManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mobDisplayManager = new MobDisplayManager(this);
        getServer().getPluginManager().registerEvents(mobDisplayManager, this);
        mobDisplayManager.start();
        if (!registerMobSpawnCommand()) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (mobDisplayManager != null) {
            mobDisplayManager.shutdown();
        }
    }

    private boolean registerMobSpawnCommand() {
        MobSpawnCommand mobSpawnCommand = new MobSpawnCommand(this, mobDisplayManager);
        var command = getCommand("mobspawn");
        if (command == null) {
            getLogger().severe("Der Befehl 'mobspawn' fehlt in plugin.yml.");
            return false;
        }

        command.setExecutor(mobSpawnCommand);
        command.setTabCompleter(mobSpawnCommand);
        return true;
    }
}
