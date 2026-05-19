package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.LogginException;
import com.kntrel.mc.accwarden.event.PlayerAccountLoginEvent;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformAdapter;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NonNull;

public final class SessionService {

    private final AccWarden plugin_;
    private final AccountService accountService_;
    private final SessionHolder sessionHolder_;
    private final NamespacedKey notLoggedKey_;
    private final NamespacedKey gameModeKey_;
    private PlatformRouter router_;

    public SessionService(AccWarden plugin, AccountService accountService, SessionHolder sessionHolder) {
        this.plugin_ = plugin;
        this.accountService_ = accountService;
        this.sessionHolder_ = sessionHolder;
        this.notLoggedKey_ = new NamespacedKey(plugin, "notLogged");
        this.gameModeKey_ = new NamespacedKey(plugin, "loggedGameMode");
    }

    public void setRouter(PlatformRouter router) {
        this.router_ = router;
    }

    public void giveSession(Player player) {
        PlatformAdapter adapter = this.requireRouter_().getAdapter(player);
        Platform platform = adapter.getPlatform();

        if (this.sessionHolder_.claim(player, platform)) {
            this.accountService_.get(player, platform)
                    .ifPresentOrElse(
                            account -> this.openSessionOrReport_(player, platform, account),
                            () -> this.startRegistration_(player, platform, adapter)
                    );
            return;
        }

        this.accountService_.get(player, platform)
                .or(() -> adapter.findLinkedAccount(player, this.accountService_))
                .ifPresentOrElse(
                        account -> this.startAuthentication_(player, adapter, account),
                        () -> this.startRegistration_(player, platform, adapter)
                );
    }

    public void rememberSession(Player player) {
        if (!this.isLogged(player)) {
            return;
        }

        Platform platform = this.requireRouter_().getPlatform(player);
        if (!this.accountService_.exists(player, platform)) {
            this.sessionHolder_.dispose(player);
            return;
        }

        Account account = this.accountService_.get(player, platform).orElseThrow();
        this.sessionHolder_.openNew(account, player, platform);
    }

    public void logOut(Player player) {
        this.sessionHolder_.dispose(player);
        this.reset_(player);
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

    public void register(Player player, Platform platform, Account account) throws LogginException {
        this.accountService_.link(account, player, platform);
        this.accountService_.save(account);
        this.openSession(player, platform, account);
    }

    @NonNull
    public Account authenticateWithPassword(Player player, Platform platform, Account account, String password, boolean linkPlatform) throws LogginException {
        Account preparedAccount = this.accountService_.prepare(account);
        if (preparedAccount.isLocked()) {
            throw new LogginException(LogginException.Reason.ACCOUNT_LOCKED, preparedAccount);
        }
        if (!preparedAccount.checkPassword(password)) {
            throw new LogginException(LogginException.Reason.INCORRECT_PASSWORD, preparedAccount);
        }
        if (linkPlatform && !preparedAccount.hasPlatform(platform)) {
            this.accountService_.link(preparedAccount, player, platform);
            this.accountService_.save(preparedAccount);
        }
        return this.openSession(player, platform, preparedAccount);
    }

    @NonNull
    public Account openSession(Player player, Platform platform, Account account) throws LogginException {
        Account preparedAccount = this.accountService_.prepare(account);
        PlayerAccountLoginEvent event = new PlayerAccountLoginEvent(player, platform, preparedAccount);
        this.plugin_.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            throw new LogginException(LogginException.Reason.DENIED, preparedAccount, event.getDeniedMessage());
        }

        preparedAccount.markLoggedIn();
        this.accountService_.save(preparedAccount);
        this.sessionHolder_.open(preparedAccount, player, platform);

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

    public void sendLoggedIn(Player player) {
        player.sendMessage(ChatColor.GREEN + this.plugin_.getRunical()
                .translate(player, "info.logged_in")
                .orDefault("")
                .message());
    }

    AccWarden getPlugin() {
        return this.plugin_;
    }

    private void startAuthentication_(Player player, PlatformAdapter adapter, Account account) {
        this.reset_(player);
        adapter.authenticate(player, account);
    }

    private void startRegistration_(Player player, Platform platform, PlatformAdapter adapter) {
        this.reset_(player);
        adapter.collectPassword(player, this.accountService_.create(player, platform));
    }

    private void openSessionOrReport_(Player player, Platform platform, Account account) {
        try {
            this.openSession(player, platform, account);
            this.sendLoggedIn(player);
        } catch (LogginException ex) {
            ex.getPublicMessage().ifPresent(player::sendMessage);
        }
    }

    private void reset_(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(this.notLoggedKey_, PersistentDataType.BYTE, (byte) 1);
        if (!container.has(this.gameModeKey_, PersistentDataType.STRING)) {
            container.set(this.gameModeKey_, PersistentDataType.STRING, player.getGameMode().toString());
        }
        player.setGameMode(GameMode.SPECTATOR);
    }

    private PlatformRouter requireRouter_() {
        if (this.router_ == null) {
            throw new IllegalStateException("Platform router has not been configured.");
        }
        return this.router_;
    }
}
