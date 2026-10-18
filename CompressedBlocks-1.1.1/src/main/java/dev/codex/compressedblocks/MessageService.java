package dev.codex.compressedblocks;

import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Renders configured MiniMessage messages and sends them through Adventure. */
public final class MessageService {
    private static final String MESSAGE_ROOT = "messages.";
    private static final String LEGACY_COMPRESSED_MESSAGE =
            "<green>Komprimiert: <count> Rezept(e) zu Stufe <level> (<units>x).</green>";
    private static final String TIER_COMPRESSED_MESSAGE =
            "<green>Komprimiert: <count> Rezept(e) zu <tier> "
                    + "(<units> Original-Items).</green>";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;

    private FileConfiguration config;
    private Component prefix;

    public MessageService(JavaPlugin plugin) {
        this(plugin, MiniMessage.miniMessage());
    }

    MessageService(JavaPlugin plugin, MiniMessage miniMessage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        reload();
    }

    /** Refreshes the configuration reference and pre-renders the static prefix. */
    public void reload() {
        config = plugin.getConfig();
        prefix = deserializeSafely(
                config.getString(MESSAGE_ROOT + "prefix", ""),
                MESSAGE_ROOT + "prefix",
                TagResolver.empty()
        );
    }

    public Component prefix() {
        return prefix;
    }

    public Component message(String key, TagResolver... resolvers) {
        String path = messagePath(key);
        String template = config.getString(path);
        if (template == null) {
            plugin.getLogger().warning("Fehlende Nachricht in config.yml: " + path);
            return Component.text("Fehlende Nachricht: " + path, NamedTextColor.RED);
        }
        if (path.equals(MESSAGE_ROOT + "compressed")
                && template.equals(LEGACY_COMPRESSED_MESSAGE)) {
            template = TIER_COMPRESSED_MESSAGE;
        }
        return deserializeSafely(template, path, TagResolver.resolver(resolvers));
    }

    public Component template(String template, TagResolver... resolvers) {
        Objects.requireNonNull(template, "template");
        return deserializeSafely(template, "interne Nachricht", TagResolver.resolver(resolvers));
    }

    public Component prefixed(Component message) {
        return prefix.append(Objects.requireNonNull(message, "message"));
    }

    public void send(CommandSender recipient, String key, TagResolver... resolvers) {
        Objects.requireNonNull(recipient, "recipient");
        recipient.sendMessage(prefixed(message(key, resolvers)));
    }

    public void sendTemplate(CommandSender recipient, String template, TagResolver... resolvers) {
        Objects.requireNonNull(recipient, "recipient");
        recipient.sendMessage(prefixed(template(template, resolvers)));
    }

    public void sendComponent(CommandSender recipient, Component message) {
        Objects.requireNonNull(recipient, "recipient");
        recipient.sendMessage(prefixed(message));
    }

    public void sendUnprefixed(CommandSender recipient, Component message) {
        Objects.requireNonNull(recipient, "recipient");
        recipient.sendMessage(Objects.requireNonNull(message, "message"));
    }

    private Component deserializeSafely(String template, String source, TagResolver resolver) {
        try {
            return miniMessage.deserialize(template, resolver);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Ungueltiges MiniMessage-Format in " + source, exception);
            return Component.text(template);
        }
    }

    private static String messagePath(String key) {
        Objects.requireNonNull(key, "key");
        return key.startsWith(MESSAGE_ROOT) ? key : MESSAGE_ROOT + key;
    }
}
