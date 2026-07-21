package com.kntrel.mc.accwarden.persistence.domain;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.PlatformKey;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;

public final class SQLiteAccount extends Account {

    SQLiteAccount(
            UUID uuid,
            String name,
            String salt,
            String hashedPassword,
            Set<PlatformKey> joinedFromPlatforms,
            LocalDateTime joined,
            LocalDateTime lastLogged
    ) {
        super(name);
        this.load(uuid, salt, hashedPassword, joinedFromPlatforms, joined, lastLogged);
    }

}
