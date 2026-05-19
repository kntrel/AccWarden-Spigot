package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import org.bukkit.entity.Player;

import java.util.Optional;

public interface PlatformAdapter {

    Platform getPlatform();

    default Optional<Account> findLinkedAccount(Player player, AccountService accountService) {
        return Optional.empty();
    }

    void authenticate(Player player, Account account);

    void collectPassword(Player player, Account account);
}
