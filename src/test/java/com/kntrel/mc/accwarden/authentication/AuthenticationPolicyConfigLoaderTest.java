package com.kntrel.mc.accwarden.authentication;

import com.kntrel.mc.accwarden.authentication.policy.AuthenticationPolicyConfig;
import com.kntrel.mc.accwarden.authentication.policy.AuthenticationPolicyConfigLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationPolicyConfigLoaderTest {

    @Test
    void parsesTheShippedPolicyConfiguration() {
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(resource);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8)
        );

        AuthenticationPolicyConfig config = AuthenticationPolicyConfigLoader.load(
                yaml.getConfigurationSection("authentication_policy")
        );

        assertEquals(3, config.login().startsAfter());
        assertEquals(Duration.ofMinutes(1), config.login().maximumDelay());
        assertEquals(5, config.account().distinctClientsAtLeast());
    }

    @Test
    void rejectsAmbiguousDurationsWithoutUnits() {
        assertThrows(IllegalArgumentException.class, () -> AuthenticationPolicyConfigLoader.parseDuration("30"));
    }

    @Test
    void rejectsThePreRenameSchema() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);

        assertThrows(IllegalArgumentException.class, () -> AuthenticationPolicyConfigLoader.load(yaml));
    }
}
