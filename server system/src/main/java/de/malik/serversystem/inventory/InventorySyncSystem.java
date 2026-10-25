package de.malik.serversystem.inventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class InventorySyncSystem implements Listener {
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS serversystem_inventories ("
            + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
            + "storage_contents LONGBLOB NOT NULL, "
            + "armor_contents LONGBLOB NOT NULL, "
            + "offhand_contents LONGBLOB NOT NULL, "
            + "ender_chest_contents LONGBLOB NOT NULL, "
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB";
    private static final String UPSERT = "INSERT INTO serversystem_inventories "
            + "(uuid, storage_contents, armor_contents, offhand_contents, ender_chest_contents) "
            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
            + "storage_contents=VALUES(storage_contents), armor_contents=VALUES(armor_contents), "
            + "offhand_contents=VALUES(offhand_contents), ender_chest_contents=VALUES(ender_chest_contents)";

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final long saveIntervalTicks;
    private final boolean notifyOnSave;
    private final String saveMessage;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ConcurrentMap<UUID, InventorySnapshot> pendingLoads = new ConcurrentHashMap<>();
    private final ExecutorService databaseWriter = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ServerSystem-Inventory-DB");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean initialized;
    private BukkitTask autosaveTask;

    public InventorySyncSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("inventory-sync", false) && config.getBoolean("storage.mysql.enabled", false);
        jdbcUrl = config.getString("storage.mysql.jdbc-url", "jdbc:mariadb://localhost:3306/serversystem");
        username = config.getString("storage.mysql.username", "serversystem");
        password = config.getString("storage.mysql.password", "CHANGE_ME");
        saveIntervalTicks = 5L;
        notifyOnSave = false;
        saveMessage = "";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initialize() throws SQLException {
        if (!enabled) {
            return;
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        }
        initialized = true;
    }

    public void start() {
        if (!enabled || !initialized) {
            return;
        }
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::saveOnlineInventories, saveIntervalTicks, saveIntervalTicks);
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerOnJoin(player);
        }
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (enabled && initialized) {
            List<InventorySnapshot> snapshots;
            try {
                snapshots = captureOnlineInventories();
            } catch (IOException exception) {
                plugin.getLogger().severe("Inventare konnten beim Herunterfahren nicht serialisiert werden: "
                        + exception.getMessage());
                snapshots = List.of();
            }
            if (!snapshots.isEmpty()) {
                try {
                    enqueueSave(snapshots).get(30, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().severe("Inventare konnten beim Herunterfahren nicht vollständig gespeichert werden.");
                } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
                    plugin.getLogger().severe("Inventare konnten beim Herunterfahren nicht vollständig gespeichert werden: "
                            + exception.getMessage());
                }
            }
        }
        databaseWriter.shutdown();
        try {
            if (!databaseWriter.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseWriter.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            databaseWriter.shutdownNow();
        }
        pendingLoads.clear();
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled || !initialized) {
            return;
        }
        try {
            InventorySnapshot snapshot = loadInventory(event.getUniqueId());
            if (snapshot != null) {
                pendingLoads.put(event.getUniqueId(), snapshot);
            } else {
                pendingLoads.remove(event.getUniqueId());
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Inventar für " + event.getName() + " konnte nicht geladen werden: " + exception.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "Der gemeinsame Inventarspeicher ist gerade nicht erreichbar. Bitte versuche es erneut.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || !initialized) {
            return;
        }
        loadPlayerOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled || !initialized) {
            return;
        }
        Player player = event.getPlayer();
        pendingLoads.remove(player.getUniqueId());
        long startedAt = System.nanoTime();
        try {
            enqueueSave(List.of(captureInventory(player))).get(30, TimeUnit.SECONDS);
            sendSavedMessage(player, elapsedMillis(startedAt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Inventar von " + player.getName() + " wurde beim Verlassen nicht bestätigt.");
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            plugin.getLogger().severe("Inventar von " + player.getName() + " konnte beim Verlassen nicht gespeichert werden: "
                    + exception.getMessage());
        } catch (IOException exception) {
            plugin.getLogger().severe("Inventar von " + player.getName() + " konnte nicht serialisiert werden: "
                    + exception.getMessage());
        }
    }

    private void loadPlayerOnJoin(Player player) {
        InventorySnapshot snapshot = pendingLoads.remove(player.getUniqueId());
        if (snapshot == null) {
            long startedAt = System.nanoTime();
            try {
                enqueueSave(List.of(captureInventory(player))).whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Startinventar von " + player.getName() + " konnte nicht gespeichert werden: "
                                + failure.getMessage());
                        return;
                    }
                    notifySaved(player.getUniqueId(), elapsedMillis(startedAt));
                });
            } catch (IOException exception) {
                plugin.getLogger().severe("Startinventar von " + player.getName() + " konnte nicht serialisiert werden: "
                        + exception.getMessage());
            }
            return;
        }
        try {
            ItemStack[] storage = resize(readItems(snapshot.storageContents()), player.getInventory().getStorageContents().length);
            ItemStack[] armor = resize(readItems(snapshot.armorContents()), player.getInventory().getArmorContents().length);
            ItemStack[] offhand = readItems(snapshot.offhandContents());
            ItemStack[] enderChest = resize(readItems(snapshot.enderChestContents()), player.getEnderChest().getSize());

            player.getInventory().setStorageContents(storage);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offhand.length == 0 ? null : offhand[0]);
            player.getEnderChest().setContents(enderChest);
            player.sendMessage("§aInventar erfolgreich geladen.");
        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
            plugin.getLogger().severe("Gespeichertes Inventar von " + player.getName() + " ist ungültig: "
                    + exception.getMessage());
        }
    }

    private void saveOnlineInventories() {
        long startedAt = System.nanoTime();
        try {
            List<InventorySnapshot> snapshots = captureOnlineInventories();
            if (!snapshots.isEmpty()) {
                enqueueSave(snapshots).whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Inventar-Autosave fehlgeschlagen: " + failure.getMessage());
                        return;
                    }
                    long duration = elapsedMillis(startedAt);
                    for (InventorySnapshot snapshot : snapshots) {
                        notifySaved(snapshot.playerId(), duration);
                    }
                });
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Inventar-Autosave konnte nicht serialisiert werden: " + exception.getMessage());
        }
    }

    private void notifySaved(UUID playerId, long durationMillis) {
        if (!notifyOnSave) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                sendSavedMessage(player, durationMillis);
            }
        });
    }

    private void sendSavedMessage(Player player, long durationMillis) {
        if (!notifyOnSave) {
            return;
        }
        String format = saveMessage.replace("{duration}", "<duration>");
        player.sendMessage(miniMessage.deserialize(format,
                Placeholder.unparsed("duration", Long.toString(durationMillis))));
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private List<InventorySnapshot> captureOnlineInventories() throws IOException {
        List<InventorySnapshot> snapshots = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshots.add(captureInventory(player));
        }
        return snapshots;
    }

    private InventorySnapshot captureInventory(Player player) throws IOException {
        return new InventorySnapshot(
                player.getUniqueId(),
                writeItems(player.getInventory().getStorageContents()),
                writeItems(player.getInventory().getArmorContents()),
                writeItems(new ItemStack[]{player.getInventory().getItemInOffHand()}),
                writeItems(player.getEnderChest().getContents()));
    }

    private CompletableFuture<Void> enqueueSave(List<InventorySnapshot> snapshots) {
        List<InventorySnapshot> stableSnapshots = List.copyOf(snapshots);
        return CompletableFuture.runAsync(() -> {
            try {
                saveInventories(stableSnapshots);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseWriter);
    }

    private void saveInventories(List<InventorySnapshot> snapshots) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (InventorySnapshot snapshot : snapshots) {
                    statement.setString(1, snapshot.playerId().toString());
                    statement.setBytes(2, snapshot.storageContents());
                    statement.setBytes(3, snapshot.armorContents());
                    statement.setBytes(4, snapshot.offhandContents());
                    statement.setBytes(5, snapshot.enderChestContents());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private InventorySnapshot loadInventory(UUID playerId) throws SQLException {
        String query = "SELECT storage_contents, armor_contents, offhand_contents, ender_chest_contents "
                + "FROM serversystem_inventories WHERE uuid = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new InventorySnapshot(playerId, result.getBytes(1), result.getBytes(2),
                        result.getBytes(3), result.getBytes(4));
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private byte[] writeItems(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(items.length);
            for (ItemStack item : items) {
                output.writeObject(item);
            }
        }
        return bytes.toByteArray();
    }

    private ItemStack[] readItems(byte[] bytes) throws IOException, ClassNotFoundException {
        if (bytes == null) {
            return new ItemStack[0];
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int size = input.readInt();
            if (size < 0 || size > 100) {
                throw new IOException("Ungültige Inventargröße: " + size);
            }
            ItemStack[] items = new ItemStack[size];
            for (int index = 0; index < size; index++) {
                Object item = input.readObject();
                if (item != null && !(item instanceof ItemStack)) {
                    throw new IOException("Ungültiger Objekttyp im Inventar: " + item.getClass().getName());
                }
                items[index] = (ItemStack) item;
            }
            return items;
        }
    }

    private ItemStack[] resize(ItemStack[] items, int size) {
        return Arrays.copyOf(items, size);
    }

    private record InventorySnapshot(UUID playerId, byte[] storageContents, byte[] armorContents,
                                     byte[] offhandContents, byte[] enderChestContents) {
    }
}