package de.malik.serversystem.moderation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.malik.serversystem.storage.YamlStore;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public final class ModerationSystem implements CommandExecutor, Listener, org.bukkit.command.TabCompleter {
    private static final Pattern BAN_DURATION_PART = Pattern.compile("(\\d+)([smhdwy])", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Preset> PRESETS = new HashMap<>();
    static {
        PRESETS.put("hacking", new Preset("Hacking / Cheating", "30d", "ban"));
        PRESETS.put("xray", new Preset("X-Ray / Resource pack abuse", "14d", "ban"));
        PRESETS.put("chat-spam", new Preset("Chat spam", "1h", "mute"));
        PRESETS.put("advertising", new Preset("Advertising other servers", "7d", "mute"));
        PRESETS.put("hate-speech", new Preset("Hate speech / Discrimination", "30d", "mute"));
        PRESETS.put("griefing", new Preset("Griefing", "7d", "ban"));
        PRESETS.put("bug-abuse", new Preset("Bug / Exploit abuse", "7d", "ban"));
        PRESETS.put("inappropriate", new Preset("Inappropriate behaviour", null, "warn"));
        PRESETS.put("ban-evasion", new Preset("Ban evasion", "perm", "ban"));
    }
    private final YamlStore moderationStore;
    private final FileConfiguration moderation;
    private final ConcurrentMap<UUID, Long> mutedPlayers = new ConcurrentHashMap<>();

    public ModerationSystem(YamlStore moderationStore) {
        this.moderationStore = moderationStore;
        moderation = moderationStore.data();
        loadMutes();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "kick" -> kick(sender, args);
            case "ban" -> ban(sender, args);
            case "unban" -> unban(sender, args);
            case "mute" -> mute(sender, args);
            case "unmute" -> unmute(sender, args);
            default -> sender.sendMessage("Unbekannter Moderationsbefehl.");
        }
        return true;
    }

    private void loadMutes() {
        if (moderation.getConfigurationSection("mutes") == null) {
            return;
        }
        org.bukkit.configuration.ConfigurationSection mutes = moderation.getConfigurationSection("mutes");
        if (mutes == null) {
            return;
        }
        for (String key : mutes.getKeys(false)) {
            long expiresAt = moderation.getLong("mutes." + key + ".expiresAt");
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                pluginLogWarning("Ungültige UUID in moderation.yml unter mutes." + key + "; Eintrag wird ignoriert.");
                moderation.set("mutes." + key, null);
                continue;
            }
            if (expiresAt == 0 || expiresAt > System.currentTimeMillis()) {
                mutedPlayers.put(uuid, expiresAt);
            } else {
                moderation.set("mutes." + key, null);
            }
        }
    }

    private void pluginLogWarning(String message) {
        moderationStore.logWarning(message);
    }

    private void kick(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Verwendung: /kick <Spieler> [Grund]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("Dieser Spieler ist nicht online.");
            return;
        }
        target.kickPlayer(joinArgs(args, 1, "Vom Server geworfen."));
        sender.sendMessage(target.getName() + " wurde gekickt.");
    }

    private void ban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Verwendung: /ban <Spieler> <Grund|Dauer> [Grund] oder /ban <Spieler> <Preset>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("Zum Bannen muss der Spieler online sein.");
            return;
        }

        Preset preset = PRESETS.get(args[1].toLowerCase(Locale.ROOT));
        if (preset != null) {
            if (preset.type().equals("mute")) {
                long duration = preset.duration() == null ? 0 : parseBanDuration(preset.duration());
                long expiresAt = duration == 0 ? 0 : System.currentTimeMillis() + duration;
                UUID uuid = target.getUniqueId();
                mutedPlayers.put(uuid, expiresAt);
                moderation.set("mutes." + uuid + ".expiresAt", expiresAt);
                moderation.set("mutes." + uuid + ".reason", preset.display());
                moderation.set("mutes." + uuid + ".name", target.getName());
                moderationStore.save();
                sender.sendMessage(target.getName() + " wurde stummgeschaltet (" + preset.display() + ").");
                return;
            }
            if (preset.type().equals("warn")) {
                target.sendMessage("§eWarnung: " + preset.display());
                sender.sendMessage(target.getName() + " wurde verwarnt: " + preset.display());
                return;
            }

            long duration = preset.duration() == null ? 0 : parseBanDuration(preset.duration());
            long now = System.currentTimeMillis();
            if (duration < 0 || (duration > 0 && duration > Long.MAX_VALUE - now)) {
                sender.sendMessage("Ungültige Preset-Dauer.");
                return;
            }
            String reason = preset.display();
            UUID uuid = target.getUniqueId();
            moderation.set("bans." + uuid + ".reason", reason);
            moderation.set("bans." + uuid + ".name", target.getName());
            moderation.set("bans." + uuid + ".expiresAt", duration == 0 ? 0 : now + duration);
            moderationStore.save();
            String durationText = preset.duration() == null ? "permanent"
                    : (preset.duration().equalsIgnoreCase("perm") ? "permanent" : preset.duration());
            target.kickPlayer("Du wurdest " + durationText + " gebannt: " + reason);
            sender.sendMessage(target.getName() + " wurde gebannt.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("Verwendung: /ban <Spieler> <Dauer|perma> <Grund>");
            return;
        }
        long duration = parseBanDuration(args[1]);
        long now = System.currentTimeMillis();
        if (duration < 0 || (duration > 0 && duration > Long.MAX_VALUE - now)) {
            sender.sendMessage("Dauerformat: 10m, 1h, 1d, 1y, 2h30m oder perma.");
            return;
        }
        String reason = joinArgs(args, 2, "");
        UUID uuid = target.getUniqueId();
        moderation.set("bans." + uuid + ".reason", reason);
        moderation.set("bans." + uuid + ".name", target.getName());
        moderation.set("bans." + uuid + ".expiresAt", duration == 0 ? 0 : now + duration);
        moderationStore.save();
        String durationText = args[1].equalsIgnoreCase("perma") ? "permanent" : args[1];
        target.kickPlayer("Du wurdest " + durationText + " gebannt: " + reason);
        sender.sendMessage(target.getName() + " wurde gebannt.");
    }

    private void unban(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /unban <Spieler>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String path = "bans." + target.getUniqueId();
        if (!moderation.contains(path)) {
            sender.sendMessage("Für diesen Spieler wurde kein Bann gefunden.");
            return;
        }
        moderation.set(path, null);
        moderationStore.save();
        sender.sendMessage("Bann aufgehoben.");
    }

    private void mute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Verwendung: /mute <Spieler> [Dauer] [Grund]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("Zum Stummschalten muss der Spieler online sein.");
            return;
        }
        long duration = 0;
        int reasonIndex = 1;
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("perm")) {
                reasonIndex = 2;
            } else {
                duration = parseDuration(args[1]);
                if (duration < 0) {
                    sender.sendMessage("Dauerformat: 30s, 15m, 2h, 7d oder perm.");
                    return;
                }
                reasonIndex = 2;
            }
        }
        String reason = joinArgs(args, reasonIndex, "Kein Grund angegeben.");
        long expiresAt = duration == 0 ? 0 : System.currentTimeMillis() + duration;
        UUID uuid = target.getUniqueId();
        mutedPlayers.put(uuid, expiresAt);
        moderation.set("mutes." + uuid + ".expiresAt", expiresAt);
        moderation.set("mutes." + uuid + ".reason", reason);
        moderation.set("mutes." + uuid + ".name", target.getName());
        moderationStore.save();
        sender.sendMessage(target.getName() + " wurde stummgeschaltet.");
    }

    private void unmute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /unmute <Spieler>");
            return;
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
        if (mutedPlayers.remove(uuid) == null) {
            sender.sendMessage("Dieser Spieler ist nicht stummgeschaltet.");
            return;
        }
        moderation.set("mutes." + uuid, null);
        moderationStore.save();
        sender.sendMessage("Stummschaltung aufgehoben.");
    }

    private long parseDuration(String input) {
        if (input.length() < 2) {
            return -1;
        }
        try {
            long amount = Long.parseLong(input.substring(0, input.length() - 1));
            long multiplier = switch (input.substring(input.length() - 1).toLowerCase(Locale.ROOT)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> -1;
            };
            return amount > 0 && multiplier > 0 && amount <= Long.MAX_VALUE / multiplier ? amount * multiplier : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private long parseBanDuration(String input) {
        if (input.equalsIgnoreCase("perma")) {
            return 0;
        }
        Matcher matcher = BAN_DURATION_PART.matcher(input);
        long total = 0;
        int position = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != position) {
                    return -1;
                }
                long amount = Long.parseLong(matcher.group(1));
                long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "s" -> 1_000L;
                    case "m" -> 60_000L;
                    case "h" -> 3_600_000L;
                    case "d" -> 86_400_000L;
                    case "w" -> 604_800_000L;
                    case "y" -> 31_536_000_000L;
                    default -> -1;
                };
                total = Math.addExact(total, Math.multiplyExact(amount, multiplier));
                position = matcher.end();
            }
        } catch (ArithmeticException | NumberFormatException exception) {
            return -1;
        }
        return position == input.length() && total > 0 ? total : -1;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("ban") && args.length == 2) {
            return new java.util.ArrayList<>(PRESETS.keySet());
        }
        return java.util.List.of();
    }

    private String joinArgs(String[] args, int start, String fallback) {
        return start >= args.length ? fallback : String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String banPath = "bans." + event.getPlayer().getUniqueId();
        if (moderation.contains(banPath)) {
            long expiresAt = moderation.getLong(banPath + ".expiresAt", 0);
            if (expiresAt == 0 || expiresAt > System.currentTimeMillis()) {
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                        "Du bist gebannt: " + moderation.getString(banPath + ".reason", "Gebannt."));
            } else {
                moderation.set(banPath, null);
                moderationStore.save();
            }
        }
        Long expiresAt = mutedPlayers.get(event.getPlayer().getUniqueId());
        if (expiresAt != null && expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            mutedPlayers.remove(event.getPlayer().getUniqueId());
            moderation.set("mutes." + event.getPlayer().getUniqueId(), null);
            moderationStore.save();
        }
    }

    private record Preset(String display, String duration, String type) {
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Long expiresAt = mutedPlayers.get(event.getPlayer().getUniqueId());
        if (expiresAt == null) {
            return;
        }
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            mutedPlayers.remove(event.getPlayer().getUniqueId());
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("Du bist stummgeschaltet.");
    }
}