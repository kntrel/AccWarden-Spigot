package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.form.FormRenderer;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface Platform {

    String displayName();

    PlatformKey key();

    FormRenderer formRenderer();

    PlatformViews views();

    default UUID accountUuid(Player player) {
        return player.getUniqueId();
    }

    default boolean isJava() {
        return this.key() == PlatformKey.JAVA;
    }

    default boolean isBedrock() {
        return this.key() == PlatformKey.BEDROCK;
    }
}
