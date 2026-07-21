package com.kntrel.mc.accwarden.persistence.domain;

import java.time.LocalDateTime;

import static com.kntrel.mc.accwarden.persistence.sqlite.DTO.*;

@Table("account")
record AccountRecord(
        @Id @Column("uuid") String uuid,
        @Column("name") String name,
        @Column("salt") String salt,
        @Column("hashed_password") String hashedPassword,
        @Column("platform_joined") byte platformJoined,
        @Column("joined") LocalDateTime joined,
        @Column("last_login") LocalDateTime lastLogin
) {}
