package com.kntrel.mc.accwarden.platform;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DefaultPlatformRouter implements PlatformRouter {

    private final Platform javaPlatform_;
    @Nullable
    private final Platform bedrockPlatform_;

    public DefaultPlatformRouter(Platform javaPlatform, @Nullable Platform bedrockPlatform) {
        this.javaPlatform_ = Objects.requireNonNull(javaPlatform, "javaPlatform");
        this.bedrockPlatform_ = bedrockPlatform;
    }

    @Override
    public Platform getPlatform(Player player) {
        Objects.requireNonNull(player, "player");
        if (this.bedrockPlatform_ != null && this.isFloodgatePlayer_(player)) {
            return this.bedrockPlatform_;
        }
        return this.javaPlatform_;
    }

    private boolean isFloodgatePlayer_(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        }
        catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
