package com.kntrel.mc.accwarden;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccWardenConfigTest {

    @Test
    void loadsStructuredHoldWorldConfiguration() {
        MemoryConfiguration source = new MemoryConfiguration();
        ConfigurationSection holdWorld = source.createSection("hold_world");
        holdWorld.set("enabled", true);
        holdWorld.set("name", "authentication_void");
        holdWorld.set("dimension", "THE_END");

        AccWardenConfig.HoldWorld config = AccWardenConfig.load(source).holdWorld();

        assertTrue(config.enabled());
        assertEquals("authentication_void", config.name());
        assertEquals("THE_END", config.dimension());
    }

    @Test
    void ignoresLegacyHoldWorldNames() {
        MemoryConfiguration source = new MemoryConfiguration();
        source.set("holdWorld", "legacy_hold");
        ConfigurationSection misspelled = source.createSection("wold_world");
        misspelled.set("enabled", true);
        misspelled.set("name", "misspelled_hold");

        AccWardenConfig.HoldWorld config = AccWardenConfig.load(source).holdWorld();

        assertFalse(config.enabled());
        assertEquals(AccWardenConfig.HoldWorld.DEFAULT, config);
    }
}
