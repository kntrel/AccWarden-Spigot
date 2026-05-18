package com.kntrel.mc.accwarden.account;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.kntrel.mc.accwarden.account.exception.InvalidPasswordException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.io.database.DataBaseParser;
import com.kntrel.mc.accwarden.io.database.Enitty;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Account implements Enitty {

    //FIELDS
    private final UUID id_;
    private final String name_;
    private AccountRepository repository_;
    private boolean javaLogged_ = false;
    private boolean bedrockLogged_ = false;
    private LocalDateTime joined_ = LocalDateTime.now();
    private LocalDateTime lastLogged_ = LocalDateTime.now();
    private byte[] hash_ = new byte[0];
    private byte[] salt_ = new byte[16];
    private int minLength_ = 0, maxLength_ = 12;

    //CONSTRUCTORS
    Account(UUID id, String name, AccountRepository repository) {
        this.id_ = id;
        this.name_ = name;
        this.repository_ = repository;

        Random random = new Random();
        for (int i = 0; i < this.salt_.length; i++) {
            this.salt_[i] = (byte) random.nextInt(97, 123);
        }
    }
    Account(String id, String name, AccountRepository repository){
        this(UUID.fromString(id), name, repository);
    }

    //Setters
    public void setJava(boolean b) {
        this.javaLogged_ = b;
    }
    public void setBedrock(boolean b) {
        this.bedrockLogged_ = b;
    }
    public void setSizes(int min, int max) {
        this.minLength_ = min; this.maxLength_ = max;
    }
    private void setSalt(String salt) {
        this.salt_ = salt.getBytes(StandardCharsets.UTF_8);
    }
    private void setHash(String hash) {
        this.hash_ = hash.getBytes(StandardCharsets.UTF_8);
    }
    private void setJoined(LocalDateTime dateTime) {
        this.joined_ = dateTime;
    }
    void setRepository(AccountRepository repository) {
        this.repository_ = repository;
    }

    //GETTERS
    public UUID getId() {
        return this.id_;
    }
    public String getName() {
        return this.name_;
    }
    public LocalDateTime whenJoined() {
        return this.joined_;
    }
    public boolean hasJava() {
        return this.javaLogged_;
    }
    public boolean hasBedrock() {
        return this.bedrockLogged_;
    }
    public boolean hasPlatform(Platform platform) {
        return switch (platform) {
            case JAVA -> this.hasJava();
            case BEDROCK -> this.hasBedrock();
        };
    }
    public boolean isLocked() { return false; }
    public AccountRepository getRepository() {
        return this.repository_;
    }

    //METHODS
    public boolean setPassword(String newPassword, String newPasswordConfirm) throws InvalidPasswordException {
        int size = newPassword.length();
        if (size < Math.max(this.minLength_,1) ) {
            throw new PasswordTooShortException(this,newPassword,this.minLength_);
        }
        if (size > this.maxLength_) {
            throw new PasswordTooLongException(this,newPassword,this.maxLength_);
        }
        BCrypt.HashData hash = BCrypt.withDefaults().hashRaw(6, this.salt_, newPassword.getBytes(StandardCharsets.UTF_8));
        boolean confirm = BCrypt.verifyer().verify(newPasswordConfirm.getBytes(StandardCharsets.UTF_8),hash).verified;
        if (!confirm) { return false; }
        this.hash_ = BCrypt.Version.VERSION_2A.formatter.createHashMessage(hash);
        return true;
    }
    public boolean checkPassword(String toCheck) {
        return BCrypt.verifyer().verify(toCheck.getBytes(StandardCharsets.UTF_8),this.hash_).verified;
    }
    public void save() {
        this.repository_.save(this);
    }
    public void delete() {
        this.repository_.delete(this);
    }
    public void lock() {}

    @Override
    public String toString() {
        return
                "UUID: " + this.id_.toString()
                + "\nSalt: " + new String(this.salt_,StandardCharsets.UTF_8)
                + "\nJava: " + this.javaLogged_
                + "\nBedrock:" + this.bedrockLogged_
                + "\nJoined on: " + this.joined_.toString()
                + "\nLast joined on: " + this.lastLogged_.toString();
    }

    //CLASSES
    public static class Parser implements DataBaseParser<Account> {

        @Override
        public Account toEntity(ResultSet src) throws SQLException {
            Account acc = new Account(src.getString("uuid"), src.getString("name"),null);
            acc.setHash(src.getString("hashed_password"));
            acc.setSalt(src.getString("salt"));
            acc.setJava(src.getBoolean("java"));
            acc.setBedrock(src.getBoolean("bedrock"));
            acc.setJoined(src.getTimestamp("joined").toLocalDateTime());
            return acc;
        }

        @Override
        public Map<String,Object> toRow(Account src) {
            return Map.ofEntries(
                Map.entry("name", src.getName()),
                Map.entry("salt", new String(src.salt_,StandardCharsets.UTF_8)),
                Map.entry("hashed_password", new String(src.hash_,StandardCharsets.UTF_8)),
                Map.entry("java", src.hasJava()),
                Map.entry("bedrock", src.hasBedrock()),
                Map.entry("last_login", src.lastLogged_)
            );
        }
    }
}
