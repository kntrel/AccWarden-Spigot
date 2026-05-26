package com.kntrel.mc.accwarden.platform.bedrock;

import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Objects;

public final class JavaAndBedrockPlatformRouter implements PlatformRouter {

    private final Platform javaPlatform_;
    private final Platform bedrockPlatform_;

    public JavaAndBedrockPlatformRouter(Platform javaPlatform, Platform bedrockPlatform) {
        this.javaPlatform_ = Objects.requireNonNull(javaPlatform, "javaPlatform");
        this.bedrockPlatform_ = Objects.requireNonNull(bedrockPlatform, "bedrockPlatform");
    }

    @Override
    public Platform getPlatform(Player player) {
        Objects.requireNonNull(player, "player");
        if (this.isFloodgatePlayer_(player)) {
            return this.bedrockPlatform_;
        }
        return this.javaPlatform_;
    }

    private boolean isFloodgatePlayer_(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
