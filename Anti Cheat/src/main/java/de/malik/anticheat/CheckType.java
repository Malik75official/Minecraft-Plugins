package de.malik.anticheat;

public enum CheckType {
    SPEED("Speed", "anticheat.bypass.speed"),
    FLY("Fly", "anticheat.bypass.fly"),
    REACH("Reach", "anticheat.bypass.reach"),
    AUTOCLICKER("AutoClicker", "anticheat.bypass.autoclicker"),
    XRAY("X-Ray", "anticheat.bypass.xray");

    private final String displayName;
    private final String bypassPermission;

    CheckType(String displayName, String bypassPermission) {
        this.displayName = displayName;
        this.bypassPermission = bypassPermission;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }
}
