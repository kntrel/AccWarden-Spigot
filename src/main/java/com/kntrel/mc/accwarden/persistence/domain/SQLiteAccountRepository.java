package com.kntrel.mc.accwarden.persistence.domain;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLiteDatabase;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLitePersistenceException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SQLiteAccountRepository implements AccountRepository {

    private static final String SELECT_ALL = "SELECT account.* FROM account";

    private final SQLiteDatabase database_;

    public SQLiteAccountRepository(SQLiteDatabase database) {
        this.database_ = database;
    }

    @Override
    public Optional<Account> getByJavaId(UUID id) {
        try {
            return this.findByJavaId(id).map(this::toAccount);
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to fetch account for Java UUID '" + id + "'.", e);
        }
    }

    @Override
    public Optional<Account> getByBedrockId(UUID id) {
        try {
            return this.findByBedrockId(id).map(this::toAccount);
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to fetch account for Bedrock UUID '" + id + "'.", e);
        }
    }

    @Override
    public Set<Account> getAll() {
        String sql = SELECT_ALL + " ORDER BY account.id;";
        try {
            return this.database_.query(sql, AccountRecord.class)
                    .stream()
                    .map(this::toAccount)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to fetch accounts.", e);
        }
    }

    @Override
    public Set<Account> getByName(String name) {
        String sql = SELECT_ALL + " WHERE account.name = ? ORDER BY account.id;";
        try {
            return this.database_.query(sql, AccountRecord.class, name)
                    .stream()
                    .map(this::toAccount)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to fetch accounts named '" + name + "'.", e);
        }
    }

    @Override
    public void save(Account account) {
        try {
            if (account instanceof SQLiteAccount sqliteAccount) {
                if (sqliteAccount.getSQLiteId() < 0) {
                    this.insert(sqliteAccount);
                    return;
                }
                this.update(sqliteAccount);
                return;
            }

            Optional<AccountRecord> persistedAccount = this.findPersistedAccount(account);
            if (persistedAccount.isEmpty()) {
                this.insert(account);
                return;
            }

            AccountRecord current = persistedAccount.get();
            this.database_.update(List.of(toRecord(account, current)));
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to persist account '" + account.getName() + "'.", e);
        }
    }

    @Override
    public void delete(Account account) {
        try {
            if (account instanceof SQLiteAccount sqliteAccount) {
                this.database_.delete(List.of(deleteRecord(sqliteAccount.getSQLiteId())));
                return;
            }

            Optional<AccountRecord> current = this.findPersistedAccount(account);
            if (current.isEmpty()) {
                return;
            }
            this.database_.delete(List.of(current.get()));
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to delete account '" + account.getName() + "'.", e);
        }
    }

    private void update(SQLiteAccount account) throws SQLException {
        this.database_.update(List.of(toRecord(account, account.getSQLiteId(), account.whenJoined())));
    }

    private Optional<AccountRecord> findPersistedAccount(Account account) throws SQLException {
        UUID javaUuid = account.getJavaUuid().orElse(null);
        UUID bedrockUuid = account.getBedrockUuid().orElse(null);

        Optional<AccountRecord> exactMatch = this.findByUuidPair(javaUuid, bedrockUuid);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        LinkedHashSet<UUID> playerUuids = new LinkedHashSet<>();
        if (javaUuid != null) {
            playerUuids.add(javaUuid);
        }
        if (bedrockUuid != null) {
            playerUuids.add(bedrockUuid);
        }

        for (UUID playerUuid : playerUuids) {
            Optional<AccountRecord> found = this.findByJavaId(playerUuid);
            if (found.isPresent()) {
                return found;
            }
            found = this.findByBedrockId(playerUuid);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private void insert(Account account) throws SQLException {
        AccountRecord record = toRecord(account, 0L, account.whenJoined());
        String sql = """
                INSERT INTO account(java_uuid, bedrock_uuid, name, salt, hashed_password, joined, last_login)
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;
        this.database_.execute(
                sql,
                record.javaUuid(),
                record.bedrockUuid(),
                record.name(),
                record.salt(),
                record.hashedPassword(),
                record.joined(),
                record.lastLogin()
        );
    }

    private Optional<AccountRecord> findByJavaId(UUID id) throws SQLException {
        return this.findByUuidColumn("java_uuid", id);
    }

    private Optional<AccountRecord> findByBedrockId(UUID id) throws SQLException {
        return this.findByUuidColumn("bedrock_uuid", id);
    }

    private Optional<AccountRecord> findByUuidPair(UUID javaUuid, UUID bedrockUuid) throws SQLException {
        if (javaUuid == null && bedrockUuid == null) {
            return Optional.empty();
        }

        String sql = SELECT_ALL + """
                 WHERE ((? IS NULL AND account.java_uuid IS NULL) OR account.java_uuid = ?)
                   AND ((? IS NULL AND account.bedrock_uuid IS NULL) OR account.bedrock_uuid = ?);
                """;
        String serializedJavaUuid = javaUuid == null ? null : javaUuid.toString();
        String serializedBedrockUuid = bedrockUuid == null ? null : bedrockUuid.toString();
        return this.database_.queryOne(
                sql,
                AccountRecord.class,
                serializedJavaUuid,
                serializedJavaUuid,
                serializedBedrockUuid,
                serializedBedrockUuid
        );
    }

    private Optional<AccountRecord> findByUuidColumn(String column, UUID id) throws SQLException {
        String sql = SELECT_ALL + " WHERE account." + column + " = ?;";
        return this.database_.queryOne(sql, AccountRecord.class, id.toString());
    }

    private Account toAccount(AccountRecord row) {
        UUID javaUuid = parseUuid(row.javaUuid(), "java_uuid", row.id());
        UUID bedrockUuid = parseUuid(row.bedrockUuid(), "bedrock_uuid", row.id());

        return new SQLiteAccount(
                row.id(),
                javaUuid,
                bedrockUuid,
                row.name(),
                row.salt(),
                row.hashedPassword(),
                row.joined(),
                row.lastLogin(),
                this
        );
    }

    private static AccountRecord toRecord(Account account, long id, LocalDateTime joined) {
        return toRecord(account, id, joined, null, null);
    }

    private static AccountRecord toRecord(Account account, AccountRecord current) {
        return toRecord(account, current.id(), current.joined(), current.javaUuid(), current.bedrockUuid());
    }

    private static AccountRecord toRecord(
            Account account,
            long id,
            LocalDateTime joined,
            String fallbackJavaUuid,
            String fallbackBedrockUuid
    ) {
        String javaUuid = account.getJavaUuid().map(UUID::toString).orElse(fallbackJavaUuid);
        String bedrockUuid = account.getBedrockUuid().map(UUID::toString).orElse(fallbackBedrockUuid);
        if (javaUuid == null && bedrockUuid == null) {
            throw new SQLitePersistenceException("Cannot persist account '" + account.getName() + "' without a Java or Bedrock UUID.");
        }

        return new AccountRecord(
                id,
                javaUuid,
                bedrockUuid,
                account.getName(),
                account.getSalt(),
                account.getHashedPassword(),
                joined,
                account.whenLastLogged()
        );
    }

    private static AccountRecord deleteRecord(long id) {
        return new AccountRecord(id, null, null, "", "", "", null, null);
    }

    private static UUID parseUuid(String value, String column, long id) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SQLitePersistenceException("Invalid UUID in account." + column + " for row " + id + ".", e);
        }
    }
}
