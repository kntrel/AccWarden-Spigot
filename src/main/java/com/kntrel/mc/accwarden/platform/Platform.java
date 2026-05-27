package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.form.FormRenderer;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface Platform {

    String JAVA_KEY = "java";
    String BEDROCK_KEY = "bedrock";

    String displayName();

    String key();

    FormRenderer formRenderer();

    PlatformViews views();

    default UUID accountUuid(Player player) {
        return player.getUniqueId();
    }

    default boolean isJava() {
        return this.key().equals(JAVA_KEY);
    }

    default boolean isBedrock() {
        return this.key().equals(BEDROCK_KEY);
    }
}
