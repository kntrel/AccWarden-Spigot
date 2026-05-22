package com.kntrel.mc.accwarden.bedrock;

import com.kntrel.mc.accwarden.platform.PlatformAdapter;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

public final class FloodgatePlatformRouter implements PlatformRouter {

    private final FloodgateApi floodgateApi_;
    private final PlatformAdapter javaAdapter_;
    private final PlatformAdapter bedrockAdapter_;

    public FloodgatePlatformRouter(PlatformAdapter javaAdapter, PlatformAdapter bedrockAdapter) {
        this.floodgateApi_ = FloodgateApi.getInstance();
        this.javaAdapter_ = javaAdapter;
        this.bedrockAdapter_ = bedrockAdapter;
    }

    @Override
    public PlatformAdapter getAdapter(Player player) {
        return this.floodgateApi_.isFloodgatePlayer(player.getUniqueId())
                ? this.bedrockAdapter_
                : this.javaAdapter_;
    }
}
