package de.malik.serversystem.caseopening;

import java.io.File;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import de.malik.serversystem.ServerSystemBootstrap;
import de.malik.serversystem.storage.PersistentYamlStorage;

public class CrystalManager {

    private final ServerSystemBootstrap plugin;
    private final PersistentYamlStorage storage;
    private final File crystalFile;
    private final FileConfiguration crystalConfig;

    public CrystalManager(ServerSystemBootstrap plugin, PersistentYamlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.crystalFile = new File(plugin.getDataFolder(), "crystals.yml");
        File legacyCrystalFile = new File(plugin.getDataFolder().getParentFile(), "CaseOpening-v26.2/crystals.yml");
        boolean databaseHasData = storage.hasData("crystals.yml");
        if (!databaseHasData && !crystalFile.exists() && legacyCrystalFile.isFile()) {
            this.crystalConfig = storage.load("crystals.yml", crystalFile, legacyCrystalFile);
            save();
            plugin.getLogger().info("Kristall-Guthaben wurde aus CaseOpening-v26.2 übernommen.");
        } else {
            this.crystalConfig = storage.load("crystals.yml", crystalFile);
        }
        if (!crystalFile.exists()) {
            save();
        }
    }

    public int getBalance(UUID uuid) {
        return crystalConfig.getInt(uuid.toString(), 0);
    }

    public int getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    public void setBalance(UUID uuid, int amount) {
        crystalConfig.set(uuid.toString(), Math.max(0, amount));
        save();
    }

    public void addBalance(UUID uuid, int amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean takeBalance(UUID uuid, int amount) {
        if (amount <= 0) {
            return false;
        }
        int current = getBalance(uuid);
        if (current < amount) {
            return false;
        }
        setBalance(uuid, current - amount);
        return true;
    }

    public boolean takeBalance(Player player, int amount) {
        return takeBalance(player.getUniqueId(), amount);
    }

    public void save() {
        storage.save("crystals.yml", crystalConfig, crystalFile);
    }
}
