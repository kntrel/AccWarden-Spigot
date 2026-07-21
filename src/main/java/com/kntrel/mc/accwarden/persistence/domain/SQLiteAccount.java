package com.kntrel.mc.accwarden.persistence.domain;

import com.kntrel.mc.accwarden.account.Account;

import java.time.LocalDateTime;
import java.util.UUID;

public final class SQLiteAccount extends Account {

    SQLiteAccount(
            UUID uuid,
            String name,
            String salt,
            String hashedPassword,
            LocalDateTime joined,
            LocalDateTime lastLogged
    ) {
        super(name);
        this.load(uuid, salt, hashedPassword, joined, lastLogged);
    }

}
