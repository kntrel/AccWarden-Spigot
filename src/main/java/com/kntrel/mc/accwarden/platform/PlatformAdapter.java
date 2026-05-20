package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.account.Account;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface PlatformAdapter {

    Platform getPlatform();

    CompletableFuture<Authentication> authenticate(Player player);

    CompletableFuture<Account> register(Player player);
}
