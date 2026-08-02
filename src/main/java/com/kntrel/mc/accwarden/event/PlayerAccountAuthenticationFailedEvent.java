package com.kntrel.mc.accwarden.event;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Fired after a player submits incorrect credentials for an account.
 * Listeners may kick the player to prevent the authentication flow from offering another attempt.
 */
public final class PlayerAccountAuthenticationFailedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Account account_;
    private final Platform platform_;
    private boolean kicked_;

    public PlayerAccountAuthenticationFailedEvent(
            Player player,
            Platform platform,
            Account account
    ) {
        super(Objects.requireNonNull(player, "player"));
        this.platform_ = Objects.requireNonNull(platform, "platform");
        this.account_ = Objects.requireNonNull(account, "account");
    }

    public Account getAccount() {
        return this.account_;
    }

    public Platform getPlatform() {
        return this.platform_;
    }

    /**
     * Kicks the player and marks this failure as terminal for the authentication flow.
     */
    public void kick(String message) {
        this.kicked_ = true;
        this.getPlayer().kickPlayer(Objects.requireNonNull(message, "message"));
    }

    /**
     * Returns whether a listener kicked the player while handling this event.
     */
    public boolean isKicked() {
        return this.kicked_;
    }

    @Override
    @Nonnull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
