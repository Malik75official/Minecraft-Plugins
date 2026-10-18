package dev.codex.compressedblocks;

import dev.codex.compressedblocks.token.SecretKeyManager;
import dev.codex.compressedblocks.token.TokenCodec;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper entry point. */
public final class CompressedBlocksPlugin extends JavaPlugin {
    private byte[] secret;
    private MessageService messages;
    private volatile PluginSettings settings;
    private volatile TokenCodec tokenCodec;
    private volatile CompressionService compressionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            secret = SecretKeyManager.loadOrCreate(this);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "secret.key konnte nicht geladen werden", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new MessageService(this);
        if (!rebuildServices()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CommandController commands = new CommandController(
                this,
                this::compressionService,
                messages,
                this::reloadPlugin
        );
        registerCommand("compressedblocks", commands);
        registerCommand("compress", commands);
        registerCommand("decompress", commands);

        getServer().getPluginManager().registerEvents(
                new CraftingController(
                        this,
                        this::settings,
                        this::compressionService,
                        this::tokenCodec,
                        messages
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new TokenProtectionListener(this::tokenCodec),
                this
        );

        getLogger().info(
                "CompressedBlocks " + getPluginMeta().getVersion()
                        + " aktiv: alle Items, Kompressionsstufen 1x-"
                        + CompressionMath.tierLabel(CompressionMath.MAX_TIER)
        );
    }

    boolean reloadPlugin() {
        reloadConfig();
        messages.reload();
        return rebuildServices();
    }

    private boolean rebuildServices() {
        try {
            PluginSettings newSettings = PluginSettings.load(getConfig(), getLogger());
            TokenCodec newCodec = new TokenCodec(this, secret, newSettings.maxPayloadBytes());
            CompressionService newService = new CompressionService(newSettings, newCodec);
            settings = newSettings;
            tokenCodec = newCodec;
            compressionService = newService;
            return true;
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Plugin-Konfiguration ist ungueltig", exception);
            return false;
        }
    }

    private void registerCommand(String name, CommandController controller) {
        PluginCommand command = Objects.requireNonNull(
                getCommand(name),
                "Befehl fehlt in plugin.yml: " + name
        );
        command.setExecutor(controller);
        command.setTabCompleter(controller);
    }

    private PluginSettings settings() {
        return Objects.requireNonNull(settings, "settings");
    }

    private TokenCodec tokenCodec() {
        return Objects.requireNonNull(tokenCodec, "tokenCodec");
    }

    private CompressionService compressionService() {
        return Objects.requireNonNull(compressionService, "compressionService");
    }
}
