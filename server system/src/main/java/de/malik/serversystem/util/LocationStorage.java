package de.malik.serversystem.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public final class LocationStorage {
    private LocationStorage() {
    }

    public static void write(FileConfiguration data, String path, Location location) {
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    public static Location read(FileConfiguration data, String path) {
        if (!data.contains(path + ".world")) {
            return null;
        }
        String worldName = data.getString(path + ".world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"), (float) data.getDouble(path + ".pitch"));
    }
}