package com.kntrel.mc.accwarden.account;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.kntrel.mc.accwarden.account.exception.InvalidPasswordException;
import com.kntrel.mc.accwarden.platform.PlatformKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Account {

    //FIELDS
    private final String name_;
    private AccountService service_;
    private UUID uuid_ = null;
    private LocalDateTime joined_ = LocalDateTime.now();
    private LocalDateTime lastLogged_ = LocalDateTime.now();
    private byte[] hash_ = new byte[0];
    private byte[] salt_ = new byte[16];
    private final Set<PlatformKey> joinedFromPlatforms_ = new HashSet<>();

    //CONSTRUCTORS
    protected Account(String name, AccountService service) {
        this.name_ = name;
        this.service_ = service;

        Random random = new Random();
        for (int i = 0; i < this.salt_.length; i++) {
            this.salt_[i] = (byte) random.nextInt(97, 123);
        }
    }

    protected Account(String name) {
        this(name, null);
    }

    //Setters
    public void setUuid(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Account UUID cannot be null.");
        }
        this.uuid_ = uuid;
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
    protected void setJoinedFromPlatforms(Set<PlatformKey> platforms) {
        this.joinedFromPlatforms_.clear();
        this.joinedFromPlatforms_.addAll(platforms);
    }
    void setService(AccountService service) {
        this.service_ = service;
    }
    void setHash(byte[] hash) {
        this.hash_ = hash;
    }
    protected void load(
            UUID uuid,
            String salt,
            String hashedPassword,
            Set<PlatformKey> joinedFromPlatforms,
            LocalDateTime joined,
            LocalDateTime lastLogged
    ) {
        this.uuid_ = uuid;
        this.setSalt(salt);
        this.setHash(hashedPassword);
        this.setJoinedFromPlatforms(joinedFromPlatforms);
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
    public boolean hasUuid() {
        return this.uuid_ != null;
    }
    public boolean isLocked() { return false; }
    public AccountService getService() {
        return this.service_;
    }
    public Optional<UUID> getUuid() {
        return Optional.ofNullable(this.uuid_);
    }
    public String getSalt() {
        return new String(this.salt_, StandardCharsets.UTF_8);
    }
    byte[] getSaltBytes() {
        return this.salt_;
    }
    public String getHashedPassword() {
        return new String(this.hash_, StandardCharsets.UTF_8);
    }
    public Set<PlatformKey> getJoinedFromPlatforms() {
        return Set.copyOf(this.joinedFromPlatforms_);
    }
    public boolean markJoinedFrom(PlatformKey platform) {
        return this.joinedFromPlatforms_.add(platform);
    }

    //METHODS
    public void setPassword(String newPassword, String newPasswordConfirm) throws InvalidPasswordException {
        this.service_.setPassword(this, newPassword, newPasswordConfirm);
    }
    public boolean checkPassword(String toCheck) {
        return BCrypt.verifyer().verify(toCheck.getBytes(StandardCharsets.UTF_8),this.hash_).verified;
    }
    public void save() {
        this.service_.save(this);
    }
    public void delete() {
        this.service_.delete(this);
    }
    public void lock() {}

    @Override
    public String toString() {
        return
                "UUID: " + this.uuid_
                + "\nSalt: " + new String(this.salt_,StandardCharsets.UTF_8)
                + "\nJoined on: " + this.joined_.toString()
                + "\nLast joined on: " + this.lastLogged_.toString();
    }
}
