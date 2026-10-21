package de.skyfunity.weihnachten;

import org.bukkit.plugin.java.JavaPlugin;

public final class WeihnachtsPlugin extends JavaPlugin {

    private CookieManager cookies;
    private AdventCalendarManager advent;
    private HeadManager heads;
    private QuestManager quests;
    private ShopManager shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.cookies = new CookieManager(this);
        this.advent = new AdventCalendarManager(this);
        this.heads = new HeadManager(this);
        this.shop = new ShopManager(this);
        this.quests = new QuestManager(this);

        var cmd = getCommand("weihnachtsnpc");
        if (cmd != null) cmd.setExecutor(new AdventNpcCommand(this));
        var c = getCommand("cookieshop");
        if (c != null) c.setExecutor(new CookieShopCommand(this));
        var ca = getCommand("cookieshop-admin");
        if (ca != null) ca.setExecutor(new CookieShopAdminCommand(this));
        var w = getCommand("whead");
        if (w != null) w.setExecutor(new WHeadCommand(this));
        var g = getCommand("gift");
        if (g != null) g.setExecutor(new GiftCommand(this));
        var co = getCommand("cookies");
        if (co != null) co.setExecutor(new CookiesCommand(this));

        getServer().getPluginManager().registerEvents(new AdventNpcListener(this), this);
        getServer().getPluginManager().registerEvents(new HeadListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        quests.start();
        printBanner();
        getLogger().info("WeihnachtsEvent aktiviert.");
    }

    private void printBanner() {
        String r = "\u001B[0m";
        String red = "\u001B[31m";
        String green = "\u001B[32m";
        String yellow = "\u001B[33m";
        String cyan = "\u001B[36m";
        String[] lines = new String[] {
            red + " __      __.__  .__  .__       .__  __   " + r,
            red + "/  \\    /  \\  | |  | |  |     |__|/  |_ " + r,
            green + "\\   \\/\\/   /  | |  | |  |     |  \\   __\\" + r,
            green + " \\        /|  | |  | |  |__   |  ||  |  " + r,
            yellow + "  \\__/\\  / |__| |__| |____/   |__||__|  " + r,
            yellow + "       \\/                               " + r,
            cyan + "   W e i h n a c h t s E v e n t 2 0 2 6  " + r
        };
        for (String l : lines) getLogger().info(l);
    }

    @Override
    public void onDisable() {
        if (cookies != null) cookies.save();
        if (heads != null) heads.save();
        if (advent != null) advent.save();
        if (shop != null) shop.save();
    }

    public CookieManager cookies() { return cookies; }
    public AdventCalendarManager advent() { return advent; }
    public HeadManager heads() { return heads; }
    public QuestManager quests() { return quests; }
    public ShopManager shop() { return shop; }
}