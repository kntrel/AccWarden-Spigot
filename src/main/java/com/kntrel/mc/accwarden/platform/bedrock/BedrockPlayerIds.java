package com.kntrel.mc.accwarden.platform.bedrock;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.LinkedPlayer;

import java.util.Objects;
import java.util.UUID;

final class BedrockPlayerIds {

    private BedrockPlayerIds() {}

    static UUID accountUuid(FloodgateApi floodgateApi, Player player) {
        Objects.requireNonNull(floodgateApi, "floodgateApi");
        Objects.requireNonNull(player, "player");

        FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(player.getUniqueId());
        if (floodgatePlayer == null) {
            return player.getUniqueId();
        }

        LinkedPlayer linkedPlayer = floodgatePlayer.getLinkedPlayer();
        if (linkedPlayer != null) {
            return linkedPlayer.getBedrockId();
        }
        return floodgatePlayer.getJavaUniqueId();
    }
}
