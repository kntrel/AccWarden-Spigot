package com.kntrel.mc.accwarden.platform;

import org.bukkit.entity.Player;

public interface PlatformRouter {

    PlatformAdapter getAdapter(Player player);

    default Platform getPlatform(Player player) {
        return this.getAdapter(player).getPlatform();
    }
}
