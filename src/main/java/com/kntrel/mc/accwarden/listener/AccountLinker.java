package com.kntrel.mc.accwarden.listener;

import com.google.common.base.Charsets;
import com.kntrel.mc.accwarden.AccWarden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import java.util.UUID;

public class AccountLinker implements Listener {
    //FIELDS
    private final AccWarden plugin_;
    private final FloodgateApi floodgateApi_ = FloodgateApi.getInstance();

    //CONSTRUCTORS
    public AccountLinker(AccWarden plugin) {
        if (!this.floodgateApi_.getPlayerPrefix().equals("")) {
            throw new IllegalArgumentException("PlayerLinker requires the Floodgate players' prefix to be an empty string.");
        }
        this.plugin_ = plugin;
    }

    //Event handlers
    @EventHandler
    void onBedrockPlayerLogin(AsyncPlayerPreLoginEvent e) {
        if (!this.floodgateApi_.isFloodgatePlayer(e.getUniqueId())) { return; }
        FloodgatePlayer fgPlayer = this.floodgateApi_.getPlayer(e.getUniqueId());
        if (fgPlayer == null) { return; }
        if (fgPlayer.isLinked()) { return; }

        byte[] nameBytes = ("OfflinePlayer:" + e.getName()).getBytes(Charsets.UTF_8);
        this.floodgateApi_.getPlayerLink().linkPlayer(e.getUniqueId(),UUID.nameUUIDFromBytes(nameBytes), e.getName());

        e.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                this.plugin_.getLangProvider().getEntry(this.plugin_.CONFIG.defaultLanguage(),"info.account_linked")
        );
    }
}
