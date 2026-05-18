package com.kntrel.mc.accwarden.persistence.domain;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public final class SQLiteAccount extends Account {

    private final long id_;

    SQLiteAccount(
            long id,
            UUID javaUuid,
            UUID bedrockUuid,
            String name,
            String salt,
            String hashedPassword,
            LocalDateTime joined,
            LocalDateTime lastLogged,
            AccountRepository repository
    ) {
        super(name, repository);
        this.id_ = id;
        this.load(javaUuid, bedrockUuid, salt, hashedPassword, joined, lastLogged);
    }

    long getSQLiteId() {
        return this.id_;
    }

}
