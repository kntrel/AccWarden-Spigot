package com.kntrel.mc.accwarden.account.link;

import org.bukkit.entity.Player;

public class PlainAccountLinker implements AccountLinker {

    @Override public AccountLinkResult link(Player player) {
        return AccountLinkResult.NO_LINK;
    }
}
