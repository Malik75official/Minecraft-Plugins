package dev.codex.compressedblocks;

import dev.codex.compressedblocks.token.TokenException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Handles all player-facing commands without ever dropping overflow items. */
public final class CommandController implements TabExecutor {
    private static final String PERMISSION_COMPRESS = "compressedblocks.compress";
    private static final String PERMISSION_DECOMPRESS = "compressedblocks.decompress";
    private static final String PERMISSION_INSPECT = "compressedblocks.admin.inspect";
    private static final String PERMISSION_RELOAD = "compressedblocks.admin.reload";

    private final JavaPlugin plugin;
    private final Supplier<CompressionService> compressionServices;
    private final MessageService messages;
    private final BooleanSupplier reloadAction;

    /**
     * Creates a controller whose compression service may be replaced after a
     * successful configuration reload.
     */
    public CommandController(
            JavaPlugin plugin,
            Supplier<CompressionService> compressionServices,
            MessageService messages,
            BooleanSupplier reloadAction
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.compressionServices = Objects.requireNonNull(
                compressionServices,
                "compressionServices"
        );
        this.messages = Objects.requireNonNull(messages, "messages");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
    }

    /** Convenience constructor for installations that never replace the service. */
    public CommandController(
            JavaPlugin plugin,
            CompressionService compressionService,
            MessageService messages,
            BooleanSupplier reloadAction
    ) {
        this(plugin, () -> compressionService, messages, reloadAction);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        try {
            return switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "compress" -> handleCompress(sender, label, args);
                case "decompress" -> handleDecompress(sender, label, args);
                case "compressedblocks" -> handleRoot(sender, args);
                default -> false;
            };
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Ausführen von /" + label, exception);
            messages.sendTemplate(
                    sender,
                    "<red>Der Befehl konnte wegen eines internen Fehlers nicht ausgeführt werden.</red>"
            );
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if ((commandName.equals("compress") || commandName.equals("decompress"))
                && args.length == 1) {
            return matching(args[0], List.of("all"));
        }
        if (!commandName.equals("compressedblocks") || args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>(List.of("help", "info"));
        if (sender.hasPermission(PERMISSION_INSPECT)) {
            options.add("inspect");
        }
        if (sender.hasPermission(PERMISSION_RELOAD)) {
            options.add("reload");
        }
        return matching(args[0], options);
    }

    private boolean handleCompress(CommandSender sender, String label, String[] args) {
        if (!requirePermission(sender, PERMISSION_COMPRESS)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Boolean all = parseAllArgument(sender, label, args);
        if (all == null) {
            return true;
        }
        compress(player, all);
        return true;
    }

    private boolean handleDecompress(CommandSender sender, String label, String[] args) {
        if (!requirePermission(sender, PERMISSION_DECOMPRESS)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Boolean all = parseAllArgument(sender, label, args);
        if (all == null) {
            return true;
        }
        decompress(player, all);
        return true;
    }

    private boolean handleRoot(CommandSender sender, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            sendHelp(sender);
            return true;
        }
        if (args.length != 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> sendInfo(sender);
            case "inspect" -> inspect(sender);
            case "reload" -> reload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void compress(Player player, boolean all) {
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (isEmpty(held)) {
            messages.send(player, "empty-hand");
            return;
        }

        CompressionService service = service();
        final CompressionUnit selected;
        final ItemStack result;
        try {
            selected = service.inspect(held);
            result = service.createNextLevelToken(selected).asOne();
        } catch (TokenException exception) {
            sendServiceFailure(player, exception);
            return;
        }

        ItemStack[] original = cloneContents(inventory.getStorageContents());
        MatchSet matches = findMatches(original, selected, service);
        int possibleRecipes = matches.amount() / CompressionMath.RATIO;
        if (possibleRecipes < 1) {
            messages.send(player, "not-enough");
            return;
        }

        int requestedRecipes = all ? possibleRecipes : 1;
        Plan plan = findLargestPlan(
                original,
                matches.slots(),
                requestedRecipes,
                CompressionMath.RATIO,
                result,
                1,
                inventory.getMaxStackSize()
        );
        if (plan == null) {
            messages.send(player, "no-space");
            return;
        }

        inventory.setStorageContents(plan.contents());
        int resultingLevel = selected.level() + 1;
        BigInteger representedUnits = CompressionMath.unitsAtLevel(resultingLevel);
        messages.send(
                player,
                "compressed",
                Placeholder.unparsed("count", Integer.toString(plan.operations())),
                Placeholder.unparsed("level", Integer.toString(resultingLevel)),
                Placeholder.unparsed("tier", CompressionMath.tierLabel(resultingLevel)),
                Placeholder.unparsed("units", representedUnits.toString())
        );
    }

    private void decompress(Player player, boolean all) {
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (isEmpty(held)) {
            messages.send(player, "empty-hand");
            return;
        }

        CompressionService service = service();
        final CompressionUnit selected;
        final OutputUnit output;
        try {
            selected = service.inspect(held);
            if (!selected.compressed()) {
                messages.send(player, "not-compressed");
                return;
            }
            output = normalizeOutput(service.decompressOneLevel(selected));
        } catch (TokenException exception) {
            sendServiceFailure(player, exception);
            return;
        }

        ItemStack[] original = cloneContents(inventory.getStorageContents());
        MatchSet matches = findMatches(original, selected, service);
        int requestedTokens = all ? matches.amount() : 1;
        Plan plan = findLargestPlan(
                original,
                matches.slots(),
                requestedTokens,
                1,
                output.template(),
                output.amount(),
                inventory.getMaxStackSize()
        );
        if (plan == null) {
            messages.send(player, "no-space");
            return;
        }

        inventory.setStorageContents(plan.contents());
        messages.send(
                player,
                "decompressed",
                Placeholder.unparsed("count", Integer.toString(plan.operations()))
        );
    }

    private void inspect(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_INSPECT)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (isEmpty(held)) {
            messages.send(player, "empty-hand");
            return;
        }

        try {
            CompressionUnit unit = service().inspect(held);
            String state = unit.compressed() ? "gültiger Token" : "normales Item";
            String tier = unit.compressed()
                    ? displayTier(unit.level())
                    : "Original";
            messages.sendTemplate(
                    player,
                    "<aqua>Inspektion:</aqua> <gray><state></gray> | "
                            + "<white><material></white> | Kompressionsstufe <tier> | Stack <stack> | "
                            + "<units> Basiseinheit(en) | Payload <bytes> Byte",
                    Placeholder.unparsed("state", state),
                    Placeholder.unparsed("material", unit.baseItem().getType().getKey().asString()),
                    Placeholder.unparsed("tier", tier),
                    Placeholder.unparsed("stack", Integer.toString(unit.stackAmount())),
                    Placeholder.unparsed("units", unit.unitCount().toString()),
                    Placeholder.unparsed("bytes", Integer.toString(unit.data().payload().length))
            );
        } catch (TokenException exception) {
            sendServiceFailure(player, exception);
        }
    }

    private void reload(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_RELOAD)) {
            return;
        }

        try {
            if (!reloadAction.getAsBoolean()) {
                messages.sendTemplate(sender, "<red>Die Konfiguration konnte nicht geladen werden.</red>");
                return;
            }
            messages.reload();
            messages.send(sender, "reloaded");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konfiguration konnte nicht geladen werden", exception);
            messages.sendTemplate(sender, "<red>Die Konfiguration konnte nicht geladen werden.</red>");
        }
    }

    private void sendHelp(CommandSender sender) {
        messages.sendTemplate(sender, "<aqua>/compress [all]</aqua> <gray>– 9 gleiche Items komprimieren</gray>");
        messages.sendTemplate(sender, "<aqua>/decompress [all]</aqua> <gray>– Tokens um eine x-Stufe entpacken</gray>");
        messages.sendTemplate(sender, "<aqua>/compressedblocks info</aqua> <gray>– Plugin-Informationen</gray>");
        if (sender.hasPermission(PERMISSION_INSPECT)) {
            messages.sendTemplate(sender, "<aqua>/compressedblocks inspect</aqua> <gray>– Hand-Item prüfen</gray>");
        }
        if (sender.hasPermission(PERMISSION_RELOAD)) {
            messages.sendTemplate(sender, "<aqua>/compressedblocks reload</aqua> <gray>– Konfiguration neu laden</gray>");
        }
    }

    private void sendInfo(CommandSender sender) {
        var meta = plugin.getPluginMeta();
        messages.sendTemplate(
                sender,
                "<aqua><name></aqua> <gray>v<version></gray> – "
                        + "verlustfreie 9:1-Item-Komprimierung für Paper <white>26.2</white>.",
                Placeholder.unparsed("name", meta.getName()),
                Placeholder.unparsed("version", meta.getVersion())
        );
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages.send(sender, "no-permission");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "players-only");
        return null;
    }

    private Boolean parseAllArgument(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            return true;
        }
        messages.sendTemplate(sender, "<yellow>Verwendung: /<command> [all]</yellow>",
                Placeholder.unparsed("command", label));
        return null;
    }

    private void sendServiceFailure(CommandSender sender, TokenException exception) {
        String detail = Objects.requireNonNullElse(exception.getMessage(), "Unbekannter Fehler");
        String normalized = detail.toLowerCase(Locale.GERMAN);
        if (normalized.contains("beschädigt") || normalized.contains("signatur")) {
            messages.send(sender, "corrupt-token");
        } else if (normalized.contains("blockiert")) {
            messages.send(sender, "blocked-item");
        } else if (normalized.contains("maximale kompressionsstufe")) {
            messages.send(sender, "max-level");
        } else {
            messages.sendTemplate(
                    sender,
                    "<red>Vorgang fehlgeschlagen:</red> <gray><error></gray>",
                    Placeholder.unparsed("error", detail)
            );
        }
    }

    private CompressionService service() {
        return Objects.requireNonNull(compressionServices.get(), "compression service");
    }

    private static String displayTier(int level) {
        return level <= CompressionMath.MAX_TIER
                ? CompressionMath.tierLabel(level)
                : level + "x (Legacy)";
    }

    /**
     * Tries complete batch sizes from largest to smallest. This deliberately
     * handles the non-monotonic full-inventory case where consuming an entire
     * source stack frees a slot, while a smaller operation would not.
     */
    private static Plan findLargestPlan(
            ItemStack[] original,
            boolean[] matchingSlots,
            int maximumOperations,
            int consumedPerOperation,
            ItemStack outputTemplate,
            int outputPerOperation,
            int inventoryMaxStackSize
    ) {
        for (int operations = maximumOperations; operations >= 1; operations--) {
            ItemStack[] simulated = cloneContents(original);
            long consumed = (long) operations * consumedPerOperation;
            long produced = (long) operations * outputPerOperation;
            if (remove(simulated, matchingSlots, consumed)
                    && add(simulated, outputTemplate, produced, inventoryMaxStackSize)) {
                return new Plan(simulated, operations);
            }
        }
        return null;
    }

    private static MatchSet findMatches(
            ItemStack[] contents,
            CompressionUnit selected,
            CompressionService service
    ) {
        boolean[] matches = new boolean[contents.length];
        int amount = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (isEmpty(item)) {
                continue;
            }
            try {
                CompressionUnit candidate = service.inspect(item);
                if (service.sameUnit(selected, candidate)) {
                    matches[slot] = true;
                    amount = Math.addExact(amount, item.getAmount());
                }
            } catch (TokenException ignored) {
                // An unrelated illegal/corrupt stack must not poison this transaction.
            }
        }
        return new MatchSet(matches, amount);
    }

    private static OutputUnit normalizeOutput(List<ItemStack> outputs) throws TokenException {
        if (outputs == null || outputs.isEmpty()) {
            throw new TokenException("Entpacken lieferte keine Ausgabe.");
        }

        ItemStack first = outputs.getFirst();
        if (isEmpty(first)) {
            throw new TokenException("Entpacken lieferte einen leeren Gegenstand.");
        }
        ItemStack template = first.asOne();
        int amount = 0;
        for (ItemStack output : outputs) {
            if (isEmpty(output) || !template.isSimilar(output)) {
                throw new TokenException("Entpack-Ausgabe besitzt uneinheitliche Metadaten.");
            }
            if (output.getAmount() > output.getMaxStackSize()) {
                throw new TokenException("Entpack-Ausgabe überschreitet ihre Stackgröße.");
            }
            amount = Math.addExact(amount, output.getAmount());
        }
        if (amount != CompressionMath.RATIO) {
            throw new TokenException("Entpack-Ausgabe enthält nicht genau 9 Einheiten.");
        }
        return new OutputUnit(template, amount);
    }

    private static boolean remove(ItemStack[] contents, boolean[] matchingSlots, long amount) {
        long remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (!matchingSlots[slot]) {
                continue;
            }
            ItemStack item = contents[slot];
            if (isEmpty(item)) {
                continue;
            }

            int taken = (int) Math.min(remaining, item.getAmount());
            int left = item.getAmount() - taken;
            contents[slot] = left == 0 ? null : item.asQuantity(left);
            remaining -= taken;
        }
        return remaining == 0;
    }

    private static boolean add(
            ItemStack[] contents,
            ItemStack template,
            long amount,
            int inventoryMaxStackSize
    ) {
        if (isEmpty(template) || amount < 0) {
            return false;
        }
        long remaining = amount;

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack current = contents[slot];
            if (isEmpty(current) || !current.isSimilar(template)) {
                continue;
            }
            int maximum = Math.min(inventoryMaxStackSize, current.getMaxStackSize());
            int capacity = Math.max(0, maximum - current.getAmount());
            if (capacity == 0) {
                continue;
            }
            int inserted = (int) Math.min(remaining, capacity);
            contents[slot] = current.asQuantity(current.getAmount() + inserted);
            remaining -= inserted;
        }

        int maximum = Math.min(inventoryMaxStackSize, template.getMaxStackSize());
        if (maximum < 1) {
            return false;
        }
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (!isEmpty(contents[slot])) {
                continue;
            }
            int inserted = (int) Math.min(remaining, maximum);
            contents[slot] = template.asQuantity(inserted);
            remaining -= inserted;
        }
        return remaining == 0;
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            clone[slot] = source[slot] == null ? null : source[slot].clone();
        }
        return clone;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static List<String> matching(String prefix, List<String> candidates) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.startsWith(normalized)).toList();
    }

    private record MatchSet(boolean[] slots, int amount) {
    }

    private record OutputUnit(ItemStack template, int amount) {
    }

    private record Plan(ItemStack[] contents, int operations) {
    }
}
