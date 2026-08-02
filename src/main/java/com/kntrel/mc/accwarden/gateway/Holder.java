package com.kntrel.mc.accwarden.gateway;

import org.bukkit.entity.Player;

/**
 * Holds players while they are not allowed to interact with the server.
 */
public interface Holder {

    boolean isHeld(Player player);

    void hold(Player player);

    void unhold(Player player);
}
