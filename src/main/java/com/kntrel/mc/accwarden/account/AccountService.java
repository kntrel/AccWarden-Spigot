package com.kntrel.mc.accwarden.account;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.exception.LogginException;
import com.kntrel.mc.accwarden.account.exception.InvalidPasswordException;
import com.kntrel.mc.accwarden.persistence.domain.SQLiteAccountRepository;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AccountService implements AccountRepository {

    private final AccWarden plugin_;
    private final AccountRepository delegate_;
    private final int passwordMinLength_;
    private final int passwordMaxLength_;

    public AccountService(AccWarden plugin, AccountRepository delegate) {
        this.plugin_ = plugin;
        this.delegate_ = delegate;
        this.passwordMinLength_ = plugin.getAccWardenConfig().passwordMinSize();
        this.passwordMaxLength_ = plugin.getAccWardenConfig().passwordMaxSize();
    }

    public static AccountService create(AccWarden plugin) {
        return new AccountService(plugin, new SQLiteAccountRepository(plugin.getSQLiteDatabase()));
    }

    @Override
    public Optional<Account> getByJavaId(UUID id) {
        return this.delegate_.getByJavaId(id).map(this::prepare);
    }

    @Override
    public Optional<Account> getByBedrockId(UUID id) {
        return this.delegate_.getByBedrockId(id).map(this::prepare);
    }

    @Override
    public Set<Account> getAll() {
        return this.delegate_.getAll()
                .stream()
                .map(this::prepare)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Set<Account> getByName(String name) {
        return this.delegate_.getByName(name)
                .stream()
                .map(this::prepare)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public Account create(Player player) {
        return this.create(player, this.plugin_.getPlatformRouter().getPlatform(player));
    }

    public Account create(Player player, Platform platform) {
        Account account = new Account(player.getName(), this);
        this.link(account, player, platform);
        return this.prepare(account);
    }

    public Account getOrCreate(Player player) {
        return this.getOrCreate(player, this.plugin_.getPlatformRouter().getPlatform(player));
    }

    public Account getOrCreate(Player player, Platform platform) {
        return this.get(player, platform).orElseGet(() -> this.create(player, platform));
    }

    public Optional<Account> get(Player player, Platform platform) {
        if (platform.isJava()) { return this.getByJavaId(player.getUniqueId()); }
        if (platform.isBedrock()) { return this.getByBedrockId(player.getUniqueId()); }
        throw new IllegalArgumentException("Unsupported platform: " + platform.key());
    }

    public Optional<Account> getFirstTimePlatformAccount(Player player, Platform platform) {
        if (!this.plugin_.getAccWardenConfig().playerNameAutoLinking()) {
            return Optional.empty();
        }
        return this.getByName(player.getName())
                .stream()
                .filter(account -> !account.hasPlatform(platform))
                .findFirst();
    }

    public boolean exists(Player player, Platform platform) {
        return this.get(player, platform).isPresent();
    }

    @Override
    public void save(Account account) {
        this.delegate_.save(account);
    }

    @Override
    public void delete(Account account) {
        this.delegate_.delete(account);
        account.getJavaUuid().map(Bukkit::getPlayer).ifPresent(this::kickDeletedAccount);
        account.getBedrockUuid().map(Bukkit::getPlayer).ifPresent(this::kickDeletedAccount);
    }

    @Nonnull
    public Account login(Player player, String password) throws LogginException {
        return this.login(player, this.plugin_.getPlatformRouter().getPlatform(player), password);
    }

    @Nonnull
    public Account login(Player player, Platform platform, String password) throws LogginException {
        Account account = this.get(player, platform).orElseThrow(() -> new LogginException(LogginException.Reason.ACCOUNT_NOT_FOUND));
        return this.authenticate(account, password);
    }

    @Nonnull
    public Account authenticate(Account account, String password) throws LogginException {
        account = this.prepare(account);
        if (account.isLocked()) {
            throw new LogginException(LogginException.Reason.ACCOUNT_LOCKED, account);
        }
        if (!account.checkPassword(password)) {
            throw new LogginException(LogginException.Reason.INCORRECT_PASSWORD, account);
        }
        return account;
    }

    public Account register(Player player, Platform platform, String password) throws InvalidPasswordException {
        Account account = this.create(player, platform);
        account.setPassword(password, password);
        this.save(account);
        return account;
    }

    public Account prepare(Account account) {
        account.setRepository(this);
        account.setSizes(this.passwordMinLength_, this.passwordMaxLength_);
        return account;
    }

    public void link(Account account, Player player, Platform platform) {
        if (platform.isJava()) {
            account.linkJava(player.getUniqueId());
            return;
        }
        if (platform.isBedrock()) {
            account.linkBedrock(player.getUniqueId());
            return;
        }
        throw new IllegalArgumentException("Unsupported platform: " + platform.key());
    }

    private void kickDeletedAccount(Player player) {
        player.kickPlayer(this.plugin_.getRunical()
                .translate(player, "info.account_deleted")
                .orDefault("")
                .message());
    }
}
