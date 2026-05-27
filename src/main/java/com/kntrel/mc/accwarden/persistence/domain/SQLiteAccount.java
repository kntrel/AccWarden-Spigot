package com.kntrel.mc.accwarden.persistence.domain;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public final class SQLiteAccount extends Account {

    SQLiteAccount(
            UUID uuid,
            String name,
            String salt,
            String hashedPassword,
            LocalDateTime joined,
            LocalDateTime lastLogged,
            AccountRepository repository
    ) {
        super(name, repository);
        this.load(uuid, salt, hashedPassword, joined, lastLogged);
    }

}
