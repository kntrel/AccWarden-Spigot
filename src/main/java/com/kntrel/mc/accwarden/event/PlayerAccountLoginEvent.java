package com.kntrel.mc.accwarden.event;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import javax.annotation.Nonnull;

public class PlayerAccountLoginEvent extends PlayerEvent implements Cancellable {

    //EVENT-REQUIRED ================================================
    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
    @Override
    @Nonnull
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    //===============================================================

    //FIELDS
    private final Account account_;
    private final Platform platform_;
    private boolean cancelled_;
    private String deniedMessage_ = null;

    //CONSTRUCTOR
    public PlayerAccountLoginEvent(Player who, Platform from, Account account) {
        super(who);
        this.account_ = account;
        this.platform_ = from;
    }

    //SETTERS
    public void setDeniedMessage(String message) {
        this.deniedMessage_ = message;
    }
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled_ = cancel;
    }

    //GETTERS
    public Account getAccount() {
        return this.account_;
    }
    public Platform getPlatform() {
        return this.platform_;
    }
    public String getDeniedMessage() {
        return this.deniedMessage_;
    }
    @Override
    public boolean isCancelled() {
        return this.cancelled_;
    }

    //Methods
    public void allow() {
        this.setCancelled(false);
        this.setDeniedMessage(null);
    }
    public void disallow(String deniedMessage) {
        this.setCancelled(true);
        this.setDeniedMessage(deniedMessage);
    }
}
