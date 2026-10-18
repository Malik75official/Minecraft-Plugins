package dev.codex.compressedblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void legacyMaximumOfThreeCannotLimitTheFixedNineTierProgression() {
        PluginSettings legacyThree = PluginSettings.load(configWithMaximum(3), logger());
        PluginSettings formerDefaultNine = PluginSettings.load(configWithMaximum(9), logger());

        assertEquals(9, CompressionMath.MAX_TIER);
        assertEquals(formerDefaultNine, legacyThree);
    }

    private static YamlConfiguration configWithMaximum(int maximum) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("max-compression-level", maximum);
        return config;
    }

    private static Logger logger() {
        return Logger.getLogger(PluginSettingsTest.class.getName());
    }
}
