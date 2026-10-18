package de.malik.serversystem.caseopening;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseMenuHolder implements InventoryHolder {
    public enum Kind {
        SELECTION,
        ANIMATION,
        ADMIN_ROOT,
        CASE_EDITOR,
        REWARD_EDITOR,
        DELETE_CONFIRMATION
    }

    private final Kind kind;
    private final String caseId;
    private final int rewardIndex;
    private final Map<Integer, String> entries = new HashMap<>();
    private Inventory inventory;

    public CaseMenuHolder(Kind kind, String caseId, int rewardIndex) {
        this.kind = kind;
        this.caseId = caseId;
        this.rewardIndex = rewardIndex;
    }

    public Kind getKind() {
        return kind;
    }

    public String getCaseId() {
        return caseId;
    }

    public int getRewardIndex() {
        return rewardIndex;
    }

    public void bind(int slot, String value) {
        entries.put(slot, value);
    }

    public String getEntry(int slot) {
        return entries.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}