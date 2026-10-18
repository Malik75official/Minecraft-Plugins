package dev.codex.compressedblocks;

import dev.codex.compressedblocks.token.TokenCodec;
import dev.codex.compressedblocks.token.TokenData;
import dev.codex.compressedblocks.token.TokenException;
import dev.codex.compressedblocks.token.TokenInspection;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Stateless domain operations for inspecting, compressing and decompressing
 * item units. Inventory mutation is deliberately left to callers.
 */
public final class CompressionService {
    private final PluginSettings settings;
    private final TokenCodec tokenCodec;

    public CompressionService(PluginSettings settings, TokenCodec tokenCodec) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.tokenCodec = Objects.requireNonNull(tokenCodec, "tokenCodec");
    }

    /**
     * Inspects an ordinary item or a compressed token.
     *
     * @return a normalized unit whose base item has an amount of one
     * @throws TokenException if the item is absent, illegal, blocked or a
     *         corrupt token
     */
    public CompressionUnit inspect(ItemStack item) throws TokenException {
        if (item == null || item.isEmpty()) {
            throw new TokenException("Kein komprimierbarer Gegenstand vorhanden.");
        }

        int stackAmount = requireLegalStackAmount(item);
        TokenInspection inspection = tokenCodec.inspect(item);
        return switch (inspection.status()) {
            case CORRUPT -> throw new TokenException(corruptMessage(inspection));
            case VALID -> inspectToken(inspection, stackAmount);
            case NOT_TOKEN -> inspectOrdinaryItem(item, stackAmount);
        };
    }

    /**
     * Compares two level-specific units. Stack amounts are intentionally
     * ignored; compression level and all base-item metadata must match.
     */
    public boolean sameUnit(CompressionUnit first, CompressionUnit second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return first.level() == second.level()
                && tokenCodec.sameIdentity(first.data(), second.data());
    }

    /**
     * Creates one token at the next compression level. The caller is
     * responsible for atomically consuming exactly {@link CompressionMath#RATIO}
     * matching input units.
     */
    public ItemStack createNextLevelToken(CompressionUnit unit) throws TokenException {
        requireAllowedUnit(unit);
        if (unit.level() >= CompressionMath.MAX_TIER) {
            throw new TokenException(
                    "Maximale Kompressionsstufe "
                            + CompressionMath.tierLabel(CompressionMath.MAX_TIER)
                            + " erreicht."
            );
        }

        return tokenCodec.createToken(unit.baseItem(), unit.level() + 1);
    }

    /**
     * Decompresses one compressed unit by exactly one level.
     *
     * <p>The returned list represents exactly nine output units. It is split
     * into legal stack sizes, so an unstackable base item produces nine
     * one-item stacks while a normally stackable item usually produces one
     * stack of nine.
     */
    public List<ItemStack> decompressOneLevel(CompressionUnit unit) throws TokenException {
        requireAllowedUnit(unit);
        if (!unit.compressed()) {
            throw new TokenException("Nur komprimierte Tokens können entpackt werden.");
        }

        ItemStack outputTemplate = unit.level() == 1
                ? unit.baseItem()
                : tokenCodec.createToken(unit.baseItem(), unit.level() - 1);
        return splitLegally(outputTemplate, CompressionMath.RATIO);
    }

    /** Returns the base-item count represented by one item at this level. */
    public BigInteger unitsPerItem(CompressionUnit unit) throws TokenException {
        requireAllowedUnit(unit);
        return unit.unitsPerItem();
    }

    /** Returns the total base-item count represented by the inspected stack. */
    public BigInteger unitCount(CompressionUnit unit) throws TokenException {
        requireAllowedUnit(unit);
        return unit.unitCount();
    }

    private CompressionUnit inspectToken(TokenInspection inspection, int stackAmount)
            throws TokenException {
        TokenData data = inspection.validData().orElseThrow(
                () -> new TokenException("Tokenprüfung lieferte keine Tokendaten.")
        );
        if (data.level() < 1) {
            throw new TokenException("Komprimierter Token hat eine ungültige Stufe.");
        }

        requireAllowedBase(data.baseItem());
        requirePayloadSize(data);
        return new CompressionUnit(data, stackAmount);
    }

    private CompressionUnit inspectOrdinaryItem(ItemStack item, int stackAmount)
            throws TokenException {
        ItemStack baseItem = item.asOne();
        requireAllowedBase(baseItem);

        byte[] payload;
        try {
            payload = baseItem.serializeAsBytes();
        } catch (RuntimeException exception) {
            throw new TokenException("Gegenstand konnte nicht serialisiert werden.", exception);
        }

        TokenData data = new TokenData(baseItem, 0, payload);
        requirePayloadSize(data);
        return new CompressionUnit(data, stackAmount);
    }

    private void requireAllowedUnit(CompressionUnit unit) throws TokenException {
        if (unit == null) {
            throw new TokenException("Kompressionseinheit fehlt.");
        }
        if (unit.level() < 0) {
            throw new TokenException("Kompressionsstufe darf nicht negativ sein.");
        }
        requireAllowedBase(unit.baseItem());
        requirePayloadSize(unit.data());
    }

    private void requireAllowedBase(ItemStack baseItem) throws TokenException {
        if (baseItem == null || baseItem.isEmpty()) {
            throw new TokenException("Token enthält keinen gültigen Basisgegenstand.");
        }

        Material material = baseItem.getType();
        if (!settings.allows(material)) {
            throw new TokenException("Material ist blockiert: " + material.getKey());
        }
        if (baseItem.getAmount() != 1) {
            throw new TokenException("Basisgegenstand muss genau die Menge 1 haben.");
        }
    }

    private void requirePayloadSize(TokenData data) throws TokenException {
        int payloadBytes = data.payload().length;
        if (payloadBytes == 0) {
            throw new TokenException("Item-Payload ist leer.");
        }
        if (payloadBytes > settings.maxPayloadBytes()) {
            throw new TokenException(
                    "Item-Payload ist zu groß (" + payloadBytes + "/"
                            + settings.maxPayloadBytes() + " Bytes)."
            );
        }
    }

    private static int requireLegalStackAmount(ItemStack item) throws TokenException {
        int amount = item.getAmount();
        int maxStackSize = item.getMaxStackSize();
        if (maxStackSize < 1 || amount < 1 || amount > maxStackSize) {
            throw new TokenException(
                    "Ungültige Stackgröße " + amount + " (Maximum " + maxStackSize + ")."
            );
        }
        return amount;
    }

    private static List<ItemStack> splitLegally(ItemStack template, int totalAmount)
            throws TokenException {
        int maxStackSize = template.getMaxStackSize();
        if (maxStackSize < 1) {
            throw new TokenException("Ausgabegegenstand hat keine gültige Stackgröße.");
        }

        List<ItemStack> output = new ArrayList<>(
                Math.ceilDiv(totalAmount, maxStackSize)
        );
        int remaining = totalAmount;
        while (remaining > 0) {
            int amount = Math.min(remaining, maxStackSize);
            output.add(template.asQuantity(amount));
            remaining -= amount;
        }
        return List.copyOf(output);
    }

    private static String corruptMessage(TokenInspection inspection) {
        String detail = inspection.error();
        if (detail == null || detail.isBlank()) {
            return "Komprimierter Token ist beschädigt.";
        }
        return "Komprimierter Token ist beschädigt: " + detail;
    }
}
