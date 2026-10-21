package de.skyfunity.weihnachten;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class Quest {
    public final String id;
    public final String name;
    public final String description;
    public final Type type;
    public final int target;
    public final int slot; // 0,1,2 -> 14,16,18 Uhr
    public final List<ItemStack> rewardItems;
    public final int rewardCookies;

    public enum Type { WALK, JUMP, SNOWBALL, MINE_DIAMOND, KILL_PIG, KILL_MOB, EAT, FISH, PLACE_BLOCK, BREAK_BLOCK, CRAFT, ENCHANT, TRADE, SMELT, BREW, TAME, SHEAR, SLEEP, ENTER_NETHER, ENTER_END, KILL_PLAYER }

    public Quest(String id, String name, String description, Type type, int target, int slot, List<ItemStack> rewardItems, int rewardCookies) {
        this.id = id; this.name = name; this.description = description; this.type = type;
        this.target = target; this.slot = slot; this.rewardItems = rewardItems; this.rewardCookies = rewardCookies;
    }
}