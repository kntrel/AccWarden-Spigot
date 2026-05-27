package com.kntrel.mc.accwarden.persistence.domain;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLiteDatabase;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLitePersistenceException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
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
    public Optional<Account> getByUUID(UUID id) {
        try {
            return this.findByUUID(id).map(this::toAccount);
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to fetch account for UUID '" + id + "'.", e);
        }
    }

    @Override
    public Set<Account> getAll() {
        String sql = SELECT_ALL + " ORDER BY account.uuid;";
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
        String sql = SELECT_ALL + " WHERE LOWER(TRIM(account.name)) = LOWER(TRIM(?)) ORDER BY account.uuid;";
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
            Optional<AccountRecord> persistedAccount = this.findPersistedAccount(account);
            if (persistedAccount.isEmpty()) {
                this.insert(account);
                return;
            }
            this.update(account, persistedAccount.get().joined());
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to persist account '" + account.getName() + "'.", e);
        }
    }

    @Override
    public void delete(Account account) {
        try {
            Optional<AccountRecord> current = this.findPersistedAccount(account);
            if (current.isEmpty()) {
                return;
            }
            this.database_.delete(List.of(deleteRecord(current.get().uuid())));
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to delete account '" + account.getName() + "'.", e);
        }
    }

    private Optional<AccountRecord> findPersistedAccount(Account account) throws SQLException {
        UUID uuid = account.getUuid()
                .orElseThrow(() -> new SQLitePersistenceException("Cannot persist account '" + account.getName() + "' without a UUID."));
        return this.findByUUID(uuid);
    }

    private void insert(Account account) throws SQLException {
        this.database_.insert(List.of(toRecord(account, account.whenJoined())));
    }

    private void update(Account account, LocalDateTime joined) throws SQLException {
        this.database_.update(List.of(toRecord(account, joined)));
    }

    private Optional<AccountRecord> findByUUID(UUID id) throws SQLException {
        String sql = SELECT_ALL + " WHERE account.uuid = ?;";
        return this.database_.queryOne(sql, AccountRecord.class, id.toString());
    }

    private Account toAccount(AccountRecord row) {
        return new SQLiteAccount(
                parseUuid(row.uuid(), "uuid"),
                row.name(),
                row.salt(),
                row.hashedPassword(),
                row.joined(),
                row.lastLogin(),
                this
        );
    }

    private static AccountRecord toRecord(Account account, LocalDateTime joined) {
        String uuid = account.getUuid()
                .map(UUID::toString)
                .orElseThrow(() -> new SQLitePersistenceException("Cannot persist account '" + account.getName() + "' without a UUID."));

        return new AccountRecord(
                uuid,
                account.getName(),
                account.getSalt(),
                account.getHashedPassword(),
                joined,
                account.whenLastLogged()
        );
    }

    private static AccountRecord deleteRecord(String uuid) {
        return new AccountRecord(uuid, "", "", "", null, null);
    }

    private static UUID parseUuid(String value, String column) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SQLitePersistenceException("Invalid UUID in account." + column + ".", e);
        }
    }
}
