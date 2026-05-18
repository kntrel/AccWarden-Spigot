package com.kntrel.mc.accwarden.account;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.exception.LogginException;
import com.kntrel.mc.accwarden.event.PlayerAccountLoginEvent;
import com.kntrel.mc.accwarden.persistence.domain.SQLiteAccountRepository;
import com.kntrel.mc.accwarden.session.BedrockSessionHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NonNull;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AccountService implements AccountRepository {

    private final AccWarden plugin_;
    private final AccountRepository delegate_;
    private final NamespacedKey notLoggedKey_;
    private final NamespacedKey gameModeKey_;
    private final int passwordMinLength_;
    private final int passwordMaxLength_;

    public AccountService(AccWarden plugin, AccountRepository delegate) {
        this.plugin_ = plugin;
        this.delegate_ = delegate;
        this.notLoggedKey_ = new NamespacedKey(plugin, "notLogged");
        this.gameModeKey_ = new NamespacedKey(plugin, "loggedGameMode");
        this.passwordMinLength_ = plugin.CONFIG.passwordMinSize();
        this.passwordMaxLength_ = plugin.CONFIG.passwordMaxSize();
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
        Account account = new Account(player.getName(), this);
        this.link(account, player, this.getPlatform(player));
        return this.prepare(account);
    }

    public Account getOrCreate(Player player) {
        return this.get(player, this.getPlatform(player)).orElseGet(() -> this.create(player));
    }

    public Optional<Account> get(Player player, Platform platform) {
        return switch (platform) {
            case JAVA -> this.getByJavaId(player.getUniqueId());
            case BEDROCK -> this.getByBedrockId(player.getUniqueId());
        };
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

    @NonNull
    public Account login(Player player, String password) throws LogginException {
        Account account = this.get(player, this.getPlatform(player)).orElseThrow(() -> new LogginException(LogginException.Reason.ACCOUNT_NOT_FOUND));
        if (account.isLocked()) {
            throw new LogginException(LogginException.Reason.ACCOUNT_LOCKED, account);
        }
        if (!account.checkPassword(password)) {
            throw new LogginException(LogginException.Reason.INCORRECT_PASSWORD, account);
        }
        return this.openSession(player, account);
    }

    @NonNull
    public Account openSession(Player player, Account account) throws LogginException {
        Account preparedAccount = this.prepare(account);
        Platform platform = (this.plugin_.isBedrockOn() && BedrockSessionHandler.isBedrock(player))
                ? Platform.BEDROCK
                : Platform.JAVA;
        PlayerAccountLoginEvent event = new PlayerAccountLoginEvent(player, platform, preparedAccount);
        this.plugin_.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            throw new LogginException(LogginException.Reason.DENIED, preparedAccount, event.getDeniedMessage());
        }

        preparedAccount.markLoggedIn();
        this.save(preparedAccount);

        PersistentDataContainer container = player.getPersistentDataContainer();
        container.remove(this.notLoggedKey_);
        if (container.has(this.gameModeKey_, PersistentDataType.STRING)) {
            String gameModeString = container.get(this.gameModeKey_, PersistentDataType.STRING);
            container.remove(this.gameModeKey_);
            try {
                player.setGameMode(GameMode.valueOf(gameModeString));
                return preparedAccount;
            } catch (IllegalArgumentException ignored) {
            }
        }
        player.setGameMode(this.plugin_.getServer().getDefaultGameMode());
        return preparedAccount;
    }

    public void reset(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(this.notLoggedKey_, PersistentDataType.BYTE, (byte) 1);
        if (!container.has(this.gameModeKey_, PersistentDataType.STRING)) {
            container.set(this.gameModeKey_, PersistentDataType.STRING, player.getGameMode().toString());
        }
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void logOut(Player player) {
        this.reset(player);
    }

    public boolean isLogged(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!container.has(this.notLoggedKey_, PersistentDataType.BYTE)) {
            return true;
        }

        Byte value = container.get(this.notLoggedKey_, PersistentDataType.BYTE);
        if (value == null) {
            return false;
        }

        return value <= 0;
    }

    private Account prepare(Account account) {
        account.setRepository(this);
        account.setSizes(this.passwordMinLength_, this.passwordMaxLength_);
        return account;
    }

    public void link(Account account, Player player, Platform platform) {
        switch (platform) {
            case JAVA -> account.linkJava(player.getUniqueId());
            case BEDROCK -> account.linkBedrock(player.getUniqueId());
        }
    }

    private void kickDeletedAccount(Player player) {
        player.kickPlayer(this.plugin_.getRunical()
                .translate(player, "info.account_deleted")
                .orDefault("")
                .message());
    }

    private Platform getPlatform(Player player) {
        return this.plugin_.isBedrockOn() && BedrockSessionHandler.isBedrock(player)
                ? Platform.BEDROCK
                : Platform.JAVA;
    }
}
