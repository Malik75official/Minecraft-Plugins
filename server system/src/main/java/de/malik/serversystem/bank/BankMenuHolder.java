package de.malik.serversystem.bank;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BankMenuHolder implements InventoryHolder {
    public enum View {
        PLAYER,
        LEADERBOARD,
        ADMIN
    }

    private final View view;
    private Inventory inventory;

    public BankMenuHolder(View view) {
        this.view = view;
    }

    public View view() {
        return view;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}