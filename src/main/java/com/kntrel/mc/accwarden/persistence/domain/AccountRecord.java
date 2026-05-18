package com.kntrel.mc.accwarden.persistence.domain;

import java.time.LocalDateTime;

import static com.kntrel.mc.accwarden.persistence.sqlite.DTO.*;

@Table("account")
record AccountRecord(
        @Id @Column("id") long id,
        @Column("java_uuid") String javaUuid,
        @Column("bedrock_uuid") String bedrockUuid,
        @Column("name") String name,
        @Column("salt") String salt,
        @Column("hashed_password") String hashedPassword,
        @Column("joined") LocalDateTime joined,
        @Column("last_login") LocalDateTime lastLogin
) {}
