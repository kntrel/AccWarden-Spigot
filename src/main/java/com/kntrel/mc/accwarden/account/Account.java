package com.kntrel.mc.accwarden.account;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.kntrel.mc.accwarden.account.exception.InvalidPasswordException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.platform.Platform;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class Account {

    //FIELDS
    private final String name_;
    private AccountRepository repository_;
    private UUID javaUuid_ = null;
    private UUID bedrockUuid_ = null;
    private LocalDateTime joined_ = LocalDateTime.now();
    private LocalDateTime lastLogged_ = LocalDateTime.now();
    private byte[] hash_ = new byte[0];
    private byte[] salt_ = new byte[16];
    private int minLength_ = 0, maxLength_ = 12;

    //CONSTRUCTORS
    protected Account(String name, AccountRepository repository) {
        this.name_ = name;
        this.repository_ = repository;

        Random random = new Random();
        for (int i = 0; i < this.salt_.length; i++) {
            this.salt_[i] = (byte) random.nextInt(97, 123);
        }
    }

    //Setters
    public void linkJava(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Java UUID cannot be null.");
        }
        this.javaUuid_ = uuid;
    }
    public void linkBedrock(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Bedrock UUID cannot be null.");
        }
        this.bedrockUuid_ = uuid;
    }
    public void setSizes(int min, int max) {
        this.minLength_ = min; this.maxLength_ = max;
    }
    protected void setSalt(String salt) {
        this.salt_ = salt.getBytes(StandardCharsets.UTF_8);
    }
    protected void setHash(String hash) {
        this.hash_ = hash.getBytes(StandardCharsets.UTF_8);
    }
    protected void setJoined(LocalDateTime dateTime) {
        this.joined_ = dateTime;
    }
    protected void setLastLogged(LocalDateTime dateTime) {
        this.lastLogged_ = dateTime;
    }
    void setRepository(AccountRepository repository) {
        this.repository_ = repository;
    }
    protected void load(
            UUID javaUuid,
            UUID bedrockUuid,
            String salt,
            String hashedPassword,
            LocalDateTime joined,
            LocalDateTime lastLogged
    ) {
        this.javaUuid_ = javaUuid;
        this.bedrockUuid_ = bedrockUuid;
        this.setSalt(salt);
        this.setHash(hashedPassword);
        this.setJoined(joined == null ? LocalDateTime.now() : joined);
        this.setLastLogged(lastLogged == null ? this.joined_ : lastLogged);
    }
    public void markLoggedIn() {
        this.lastLogged_ = LocalDateTime.now();
    }

    //GETTERS
    public String getName() {
        return this.name_;
    }
    public LocalDateTime whenJoined() {
        return this.joined_;
    }
    public LocalDateTime whenLastLogged() {
        return this.lastLogged_;
    }
    public boolean hasJava() {
        return this.javaUuid_ != null;
    }
    public boolean hasBedrock() {
        return this.bedrockUuid_ != null;
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
    public Optional<UUID> getJavaUuid() {
        return Optional.ofNullable(this.javaUuid_);
    }
    public Optional<UUID> getBedrockUuid() {
        return Optional.ofNullable(this.bedrockUuid_);
    }
    public Optional<UUID> getPlatformUuid(Platform platform) {
        return switch (platform) {
            case JAVA -> this.getJavaUuid();
            case BEDROCK -> this.getBedrockUuid();
        };
    }
    public String getSalt() {
        return new String(this.salt_, StandardCharsets.UTF_8);
    }
    public String getHashedPassword() {
        return new String(this.hash_, StandardCharsets.UTF_8);
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
                "Java UUID: " + this.javaUuid_
                + "\nBedrock UUID: " + this.bedrockUuid_
                + "\nSalt: " + new String(this.salt_,StandardCharsets.UTF_8)
                + "\nJava: " + this.hasJava()
                + "\nBedrock:" + this.hasBedrock()
                + "\nJoined on: " + this.joined_.toString()
                + "\nLast joined on: " + this.lastLogged_.toString();
    }
}
