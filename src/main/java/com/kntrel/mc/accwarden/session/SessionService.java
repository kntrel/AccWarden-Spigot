package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.LogginException;
import com.kntrel.mc.accwarden.event.PlayerAccountLoginEvent;
import com.kntrel.mc.accwarden.platform.Authentication;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformAdapter;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public final class SessionService {

    private final AccWarden plugin_;
    private final AccountService accountService_;
    private final SessionHolder sessionHolder_;
    private PlatformRouter router_;

    public SessionService(AccWarden plugin, AccountService accountService, SessionHolder sessionHolder) {
        this.plugin_ = plugin;
        this.accountService_ = accountService;
        this.sessionHolder_ = sessionHolder;
    }

    public void setRouter(PlatformRouter router) {
        this.router_ = router;
    }

    public CompletableFuture<SessionResult> openSession(Player player) {
        try {
            PlatformAdapter adapter = this.requireRouter_().getAdapter(player);
            Platform platform = adapter.getPlatform();
            return this.sessionHolder_
                    .claim(player, platform)
                    .map(session -> CompletableFuture.completedFuture(this.resumeSession_(player, session)))
                    .orElseGet(() -> this.startAuthentication_(player, adapter));
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(this.failFlow_(ex));
        }
    }

    public void rememberSession(Player player) {
        Platform platform = this.requireRouter_().getPlatform(player);
        this.accountService_.get(player, platform)
                .ifPresentOrElse(
                        account -> this.sessionHolder_.openNew(account, player, platform),
                        () -> this.sessionHolder_.dispose(player)
                );
    }

    public void logOut(Player player) {
        this.sessionHolder_.dispose(player);
    }

    private OpenSession openSession_(Player player, Platform platform, Account account) throws LogginException {
        Account preparedAccount = this.accountService_.prepare(account);
        PlayerAccountLoginEvent event = new PlayerAccountLoginEvent(player, platform, preparedAccount);
        this.plugin_.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            throw new LogginException(LogginException.Reason.DENIED, preparedAccount, event.getDeniedMessage());
        }

        preparedAccount.markLoggedIn();
        this.accountService_.save(preparedAccount);
        OpenSession session = this.sessionHolder_
                .open(preparedAccount, player, platform)
                .orElseThrow(() -> new LogginException(LogginException.Reason.DENIED, preparedAccount));

        return session;
    }

    private void sendLoggedIn_(Player player) {
        player.sendMessage(ChatColor.GREEN + this.plugin_.getRunical()
                .translate(player, "info.logged_in")
                .orDefault("")
                .message());
    }

    private CompletableFuture<SessionResult> startAuthentication_(Player player, PlatformAdapter adapter) {
        try {
            return this.await_(adapter.authenticate(player), player)
                    .thenCompose(result -> this.completeAuthentication_(player, adapter, result))
                    .exceptionally(this::failFlow_);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(this.failFlow_(ex));
        }
    }

    private CompletableFuture<SessionResult> completeAuthentication_(Player player, PlatformAdapter adapter, Authentication authentication) {
        return switch (authentication) {
            case Authentication.Passed passed ->
                    CompletableFuture.completedFuture(this.openSessionAndNotify_(player, adapter.getPlatform(), passed.account(), SessionResult::opened));
            case Authentication.Rejected rejected ->
                    CompletableFuture.completedFuture(SessionResult.unauthenticated());
            case Authentication.Unexistent unexistent ->
                    this.startRegistration_(player, adapter);
        };
    }

    private SessionResult openSessionAndNotify_(
            Player player,
            Platform platform,
            Account account,
            Function<OpenSession, SessionResult> resultFactory
    ) {
        try {
            OpenSession session = this.openSession_(player, platform, account);
            this.sendLoggedIn_(player);
            return resultFactory.apply(session);
        } catch (LogginException ex) {
            ex.getPublicMessage().ifPresent(player::sendMessage);
            return SessionResult.unauthenticated();
        }
    }

    private SessionResult resumeSession_(Player player, OpenSession session) {
        this.sendLoggedIn_(player);
        return SessionResult.caches(session);
    }

    private CompletableFuture<SessionResult> startRegistration_(Player player, PlatformAdapter adapter) {
        try {
            return this.await_(adapter.register(player), player)
                    .thenApply(account -> this.openSessionAndNotify_(player, adapter.getPlatform(), account, SessionResult::registered));
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(this.failFlow_(ex));
        }
    }

    private <T> CompletableFuture<T> await_(CompletableFuture<T> future, Player player) {
        CompletableFuture<T> result = new CompletableFuture<>();
        future.whenComplete((value, throwable) -> this.runSync_(() -> {
            Throwable cause = this.unwrap_(throwable);
            if (cause != null) {
                result.completeExceptionally(cause);
                return;
            }
            if (!player.isOnline()) {
                result.completeExceptionally(new CancellationException("Player left before the authentication flow completed."));
                return;
            }
            result.complete(value);
        }));
        return result;
    }

    private SessionResult failFlow_(Throwable throwable) {
        Throwable cause = this.unwrap_(throwable);
        return SessionResult.failed(cause);
    }

    private Throwable unwrap_(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }

    private PlatformRouter requireRouter_() {
        if (this.router_ == null) {
            throw new IllegalStateException("Platform router has not been configured.");
        }
        return this.router_;
    }
}
