package com.kntrel.mc.accwarden.platform;

import org.bukkit.entity.Player;

import java.util.Objects;

public final class JavaOnlyPlatformRouter implements PlatformRouter {

    private final Platform javaPlatform_;

    public JavaOnlyPlatformRouter(Platform javaPlatform) {
        this.javaPlatform_ = Objects.requireNonNull(javaPlatform, "javaPlatform");
    }

    @Override
    public Platform getPlatform(Player player) {
        Objects.requireNonNull(player, "player");
        return this.javaPlatform_;
    }
}
