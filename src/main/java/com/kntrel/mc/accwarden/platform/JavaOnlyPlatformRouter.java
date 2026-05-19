package com.kntrel.mc.accwarden.platform;

import org.bukkit.entity.Player;

public final class JavaOnlyPlatformRouter implements PlatformRouter {

    private final PlatformAdapter javaAdapter_;

    public JavaOnlyPlatformRouter(PlatformAdapter javaAdapter) {
        this.javaAdapter_ = javaAdapter;
    }

    @Override
    public PlatformAdapter getAdapter(Player player) {
        return this.javaAdapter_;
    }
}
