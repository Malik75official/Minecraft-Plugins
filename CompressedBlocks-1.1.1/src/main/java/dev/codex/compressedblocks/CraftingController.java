package dev.codex.compressedblocks;

import dev.codex.compressedblocks.token.TokenCodec;
import dev.codex.compressedblocks.token.TokenException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds metadata-aware compression recipes without registering thousands of recipes. */
public final class CraftingController implements Listener {
    private static final String CRAFT_PERMISSION = "compressedblocks.craft";

    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final Supplier<CompressionService> services;
    private final Supplier<TokenCodec> codecs;
    private final MessageService messages;
    private final Set<UUID> pendingPlayers = new HashSet<>();

    public CraftingController(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            Supplier<CompressionService> services,
            Supplier<TokenCodec> codecs,
            MessageService messages
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.services = Objects.requireNonNull(services, "services");
        this.codecs = Objects.requireNonNull(codecs, "codecs");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        HumanEntity viewer = event.getView().getPlayer();
        Analysis analysis = analyze(
                viewer,
                event.getInventory().getMatrix(),
                event.getRecipe(),
                event.isRepair()
        );
        switch (analysis.kind()) {
            case CUSTOM -> event.getInventory().setResult(analysis.plan().preview().clone());
            case BLOCKED -> event.getInventory().setResult(null);
            case VANILLA -> {
                // Keep the result supplied by Minecraft or another plugin.
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT
                || !(event.getView().getTopInventory() instanceof CraftingInventory crafting)
                || event.getClickedInventory() != crafting) {
            return;
        }

        Analysis analysis = analyze(
                event.getWhoClicked(),
                crafting.getMatrix(),
                crafting.getRecipe(),
                false
        );
        if (analysis.kind() == AnalysisKind.VANILLA) {
            return;
        }

        event.setCancelled(true);
        if (analysis.kind() != AnalysisKind.CUSTOM
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!pendingPlayers.add(player.getUniqueId())) {
            return;
        }

        crafting.setResult(null);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                executeOneCraft(player, crafting);
            } finally {
                pendingPlayers.remove(player.getUniqueId());
            }
        });
    }

    private void executeOneCraft(Player player, CraftingInventory expectedInventory) {
        if (!player.isOnline()
                || !player.getOpenInventory().getTopInventory().equals(expectedInventory)) {
            return;
        }

        Analysis current = analyze(
                player,
                expectedInventory.getMatrix(),
                expectedInventory.getRecipe(),
                false
        );
        if (current.kind() != AnalysisKind.CUSTOM) {
            return;
        }

        CraftPlan plan = current.plan();
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalStorage = InventoryPlanner.cloneContents(
                playerInventory.getStorageContents()
        );
        Optional<ItemStack[]> plannedStorage = InventoryPlanner.addAll(
                originalStorage,
                plan.outputs(),
                playerInventory.getMaxStackSize()
        );
        if (plannedStorage.isEmpty()) {
            messages.send(player, "no-space");
            return;
        }

        ItemStack[] originalMatrix = InventoryPlanner.cloneContents(
                expectedInventory.getMatrix()
        );
        ItemStack[] consumedMatrix = InventoryPlanner.cloneContents(originalMatrix);
        for (int slot : plan.inputSlots()) {
            ItemStack item = consumedMatrix[slot];
            if (item == null || item.isEmpty()) {
                return;
            }
            consumedMatrix[slot] = item.getAmount() == 1
                    ? null
                    : item.asQuantity(item.getAmount() - 1);
        }

        try {
            playerInventory.setStorageContents(plannedStorage.orElseThrow());
            expectedInventory.setMatrix(consumedMatrix);
            expectedInventory.setResult(null);
        } catch (RuntimeException exception) {
            playerInventory.setStorageContents(originalStorage);
            expectedInventory.setMatrix(originalMatrix);
            throw exception;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.35F, 1.45F);
        player.updateInventory();
    }

    private Analysis analyze(
            HumanEntity viewer,
            ItemStack[] matrix,
            Recipe vanillaRecipe,
            boolean repair
    ) {
        if (matrix == null || repair) {
            return Analysis.vanilla();
        }

        boolean containsTokenData = false;
        List<Integer> occupiedSlots = new ArrayList<>();
        List<CompressionUnit> units = new ArrayList<>();
        CompressionService service = services.get();
        TokenCodec codec = codecs.get();

        for (ItemStack item : matrix) {
            if (item != null && !item.isEmpty() && codec.isTokenLike(item)) {
                containsTokenData = true;
                break;
            }
        }

        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack item = matrix[slot];
            if (item == null || item.isEmpty()) {
                continue;
            }
            try {
                occupiedSlots.add(slot);
                units.add(service.inspect(item));
            } catch (TokenException exception) {
                return containsTokenData
                        ? Analysis.blocked()
                        : Analysis.vanilla();
            }
        }

        if (containsTokenData && !settings.get().craftingEnabled()) {
            return Analysis.blocked();
        }
        if (!settings.get().craftingEnabled()) {
            return Analysis.vanilla();
        }
        if (!viewer.hasPermission(CRAFT_PERMISSION)) {
            return containsTokenData ? Analysis.blocked() : Analysis.vanilla();
        }

        if (units.size() == 1 && units.getFirst().compressed()) {
            try {
                List<ItemStack> outputs = service.decompressOneLevel(units.getFirst());
                if (settings.get().decompressOnlyStackable() && outputs.size() != 1) {
                    return Analysis.blocked();
                }
                ItemStack preview = outputs.getFirst().clone();
                return Analysis.custom(new CraftPlan(
                        List.copyOf(occupiedSlots),
                        copyItems(outputs),
                        preview
                ));
            } catch (TokenException exception) {
                return Analysis.blocked();
            }
        }

        if (matrix.length == CompressionMath.RATIO
                && occupiedSlots.size() == CompressionMath.RATIO) {
            CompressionUnit first = units.getFirst();
            for (int index = 1; index < units.size(); index++) {
                if (!service.sameUnit(first, units.get(index))) {
                    return containsTokenData ? Analysis.blocked() : Analysis.vanilla();
                }
            }

            if (settings.get().preferVanillaRecipes()
                    && vanillaRecipe != null
                    && !first.compressed()) {
                return Analysis.vanilla();
            }
            try {
                ItemStack output = service.createNextLevelToken(first).asOne();
                return Analysis.custom(new CraftPlan(
                        List.copyOf(occupiedSlots),
                        List.of(output.clone()),
                        output
                ));
            } catch (TokenException exception) {
                return Analysis.blocked();
            }
        }

        return containsTokenData ? Analysis.blocked() : Analysis.vanilla();
    }

    private static List<ItemStack> copyItems(List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }

    private enum AnalysisKind {
        VANILLA,
        CUSTOM,
        BLOCKED
    }

    private record Analysis(AnalysisKind kind, CraftPlan plan) {
        static Analysis vanilla() {
            return new Analysis(AnalysisKind.VANILLA, null);
        }

        static Analysis blocked() {
            return new Analysis(AnalysisKind.BLOCKED, null);
        }

        static Analysis custom(CraftPlan plan) {
            return new Analysis(AnalysisKind.CUSTOM, plan);
        }
    }

    private record CraftPlan(List<Integer> inputSlots, List<ItemStack> outputs, ItemStack preview) {
    }
}
