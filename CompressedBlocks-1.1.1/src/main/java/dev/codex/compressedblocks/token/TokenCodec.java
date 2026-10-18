package dev.codex.compressedblocks.token;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Encodes and authenticates compressed-item tokens. */
public final class TokenCodec {
    public static final Material CARRIER = Material.STRUCTURE_VOID;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 18;

    private static final int SCHEMA_VERSION = 1;
    private static final int SIGNATURE_LENGTH_BYTES = 32;
    private static final byte MARKER_VALUE = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] SIGNING_CONTEXT =
            "CompressedBlocks:token".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CARRIER_ID =
            CARRIER.getKey().asString().getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec secretKey;
    private final int maxPayloadBytes;
    private final NamespacedKey markerKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey payloadKey;
    private final NamespacedKey signatureKey;

    /**
     * @param plugin owning plugin; its namespace is used for all token data
     * @param secret exactly 32 random bytes
     * @param maxPayloadBytes maximum accepted serialized base-item size
     */
    public TokenCodec(JavaPlugin plugin, byte[] secret, int maxPayloadBytes) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(secret, "secret");
        if (secret.length != SecretKeyManager.KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "secret muss genau " + SecretKeyManager.KEY_LENGTH_BYTES + " Byte lang sein");
        }
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("maxPayloadBytes muss positiv sein");
        }

        this.secretKey = new SecretKeySpec(secret.clone(), HMAC_ALGORITHM);
        this.maxPayloadBytes = maxPayloadBytes;
        this.markerKey = new NamespacedKey(plugin, "token_marker");
        this.schemaKey = new NamespacedKey(plugin, "token_schema");
        this.levelKey = new NamespacedKey(plugin, "token_level");
        this.payloadKey = new NamespacedKey(plugin, "token_payload");
        this.signatureKey = new NamespacedKey(plugin, "token_signature");
    }

    /**
     * Inspects an item without throwing for malformed or forged token data.
     *
     * @param item item to inspect, possibly {@code null}
     * @return token status and, for valid tokens, decoded data
     */
    public TokenInspection inspect(ItemStack item) {
        if (!isTokenLike(item)) {
            return TokenInspection.notToken();
        }
        if (item == null || item.isEmpty() || item.getType() != CARRIER) {
            return TokenInspection.corrupt("Der Token verwendet ein ungültiges Träger-Item.");
        }

        try {
            PersistentDataContainerView data = item.getPersistentDataContainer();
            Byte marker = data.get(markerKey, PersistentDataType.BYTE);
            if (marker == null || marker.byteValue() != MARKER_VALUE) {
                return TokenInspection.corrupt("Die Token-Markierung fehlt oder ist ungültig.");
            }

            Integer schema = data.get(schemaKey, PersistentDataType.INTEGER);
            if (schema == null || schema.intValue() != SCHEMA_VERSION) {
                return TokenInspection.corrupt("Die Token-Version wird nicht unterstützt.");
            }

            Integer level = data.get(levelKey, PersistentDataType.INTEGER);
            if (level == null || !isValidLevel(level.intValue())) {
                return TokenInspection.corrupt("Die Kompressionsstufe ist ungültig.");
            }

            byte[] payload = data.get(payloadKey, PersistentDataType.BYTE_ARRAY);
            if (payload == null || payload.length == 0 || payload.length > maxPayloadBytes) {
                return TokenInspection.corrupt("Die gespeicherten Item-Daten sind ungültig oder zu groß.");
            }

            byte[] suppliedSignature = data.get(signatureKey, PersistentDataType.BYTE_ARRAY);
            if (suppliedSignature == null || suppliedSignature.length != SIGNATURE_LENGTH_BYTES) {
                return TokenInspection.corrupt("Die Token-Signatur fehlt oder ist ungültig.");
            }

            byte[] expectedSignature = sign(schema.intValue(), level.intValue(), payload);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                return TokenInspection.corrupt("Die Token-Signatur ist ungültig.");
            }

            final ItemStack base;
            try {
                base = ItemStack.deserializeBytes(payload);
            } catch (RuntimeException exception) {
                return TokenInspection.corrupt("Die gespeicherten Item-Daten können nicht gelesen werden.");
            }

            if (base.isEmpty()
                    || base.getType().isAir()
                    || !base.getType().isItem()
                    || base.getAmount() != 1) {
                return TokenInspection.corrupt("Das gespeicherte Basis-Item ist ungültig.");
            }
            if (isTokenLike(base)) {
                return TokenInspection.corrupt("Ein Token darf keinen weiteren Token enthalten.");
            }

            return TokenInspection.valid(new TokenData(base, level.intValue(), payload));
        } catch (GeneralSecurityException exception) {
            return TokenInspection.corrupt("Die Token-Signatur konnte nicht geprüft werden.");
        } catch (RuntimeException exception) {
            return TokenInspection.corrupt("Die Token-Daten haben ein ungültiges Format.");
        }
    }

    /**
     * Returns whether any token-owned PDC field is present. This deliberately also
     * identifies incomplete and corrupt tokens so they cannot be treated as normal items.
     */
    public boolean isTokenLike(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        PersistentDataContainerView data = item.getPersistentDataContainer();
        return data.has(markerKey)
                || data.has(schemaKey)
                || data.has(levelKey)
                || data.has(payloadKey)
                || data.has(signatureKey);
    }

    /** Creates one authenticated, stackable compressed token. */
    public ItemStack createToken(ItemStack base, int level) throws TokenException {
        if (base == null || base.isEmpty() || base.getType().isAir() || !base.getType().isItem()) {
            throw new TokenException("Das Basis-Item ist ungültig.");
        }
        if (!isValidLevel(level)) {
            throw new TokenException(
                    "Die Kompressionsstufe muss zwischen " + MIN_LEVEL + " und " + MAX_LEVEL + " liegen.");
        }
        if (isTokenLike(base)) {
            throw new TokenException("Ein Token kann nicht erneut als Basis-Item gespeichert werden.");
        }

        ItemStack normalized = base.asOne();
        final byte[] payload;
        try {
            payload = normalized.serializeAsBytes();
        } catch (RuntimeException exception) {
            throw new TokenException("Das Basis-Item konnte nicht serialisiert werden.", exception);
        }
        if (payload.length == 0 || payload.length > maxPayloadBytes) {
            throw new TokenException(
                    "Die serialisierten Item-Daten sind größer als das erlaubte Limit von "
                            + maxPayloadBytes
                            + " Byte.");
        }

        final byte[] signature;
        try {
            signature = sign(SCHEMA_VERSION, level, payload);
        } catch (GeneralSecurityException exception) {
            throw new TokenException("Die Token-Signatur konnte nicht erstellt werden.", exception);
        }

        ItemStack token = ItemStack.of(CARRIER);
        token.setData(DataComponentTypes.ITEM_MODEL, normalized.getType().getKey());
        token.setData(DataComponentTypes.ITEM_NAME, tokenName(level));
        token.setData(DataComponentTypes.LORE, tokenLore(normalized, level));
        token.setData(DataComponentTypes.MAX_STACK_SIZE, 64);
        token.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        token.editPersistentDataContainer(data -> writeData(data, level, payload, signature));
        return token;
    }

    /** Compares the signed identity of two already inspected tokens. */
    public boolean sameIdentity(TokenData first, TokenData second) {
        if (first == null || second == null || first.level() != second.level()) {
            return false;
        }
        return MessageDigest.isEqual(first.payload(), second.payload());
    }

    private void writeData(
            PersistentDataContainer data, int level, byte[] payload, byte[] signature) {
        data.set(markerKey, PersistentDataType.BYTE, MARKER_VALUE);
        data.set(schemaKey, PersistentDataType.INTEGER, SCHEMA_VERSION);
        data.set(levelKey, PersistentDataType.INTEGER, level);
        data.set(payloadKey, PersistentDataType.BYTE_ARRAY, payload);
        data.set(signatureKey, PersistentDataType.BYTE_ARRAY, signature);
    }

    private byte[] sign(int schema, int level, byte[] payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(secretKey);
        mac.update(SIGNING_CONTEXT);
        mac.update((byte) 0);
        mac.update(CARRIER_ID);
        mac.update((byte) 0);
        mac.update(ByteBuffer.allocate(Integer.BYTES * 3)
                .putInt(schema)
                .putInt(level)
                .putInt(payload.length)
                .array());
        return mac.doFinal(payload);
    }

    private static boolean isValidLevel(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    private static Component tokenName(int level) {
        return Component.text("Komprimiertes Item", NamedTextColor.AQUA)
                .append(Component.text(" · " + level + "x", NamedTextColor.GRAY));
    }

    private static ItemLore tokenLore(ItemStack base, int level) {
        Component original = Component.text("Original: ", NamedTextColor.DARK_GRAY)
                .append(Component.translatable(base.translationKey(), NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
        Component quantity = Component.text(
                        "Enthält " + BigInteger.valueOf(9L).pow(level) + " gleiche Items",
                        NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        Component safety = Component.text("Manipulationsgeschützt", NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.ITALIC, false);
        return ItemLore.lore(List.of(original, quantity, safety));
    }
}
