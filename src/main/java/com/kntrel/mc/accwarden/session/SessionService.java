package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.InvalidPasswordException;
import com.kntrel.mc.accwarden.account.exception.LogginException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.event.PlayerAccountAuthenticationFailedEvent;
import com.kntrel.mc.accwarden.event.PlayerAccountLoginEvent;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.logging.Level;

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
        CompletableFuture<SessionResult> future = new CompletableFuture<>();
        SessionJob job = SessionJob.forPlayer(player)
                .onComplete(c -> future.complete(c.result()))
                .build();
        this.openSession(job);
        return future;
    }

    public CompletableFuture<SessionResult> openSession(SessionJob job) {
        Objects.requireNonNull(job, "job");
        Player player = Objects.requireNonNull(job.player(), "job.player()");
        CompletableFuture<SessionResult> flow;
        try {
            Platform platform = this.requireRouter_().getPlatform(player);
            flow = this.sessionHolder_
                    .claim(player, platform)
                    .map(cached -> CompletableFuture.completedFuture(this.resumeSession_(player, cached)))
                    .orElseGet(() -> {
                        job.onUncached(new SessionJob.Uncached(player, platform));
                        return this.startSession_(job, player, platform);
                    });
        } catch (RuntimeException ex) {
            flow = CompletableFuture.completedFuture(this.failFlow_(ex));
        }
        return this.completeJob_(job, player, flow);
    }

    public void rememberSession(Player player) {
        Platform platform = this.requireRouter_().getPlatform(player);
        this.accountService_.get(player, platform)
                .ifPresentOrElse(
                        account -> this.sessionHolder_.openNew(account, player, platform),
                        () -> this.sessionHolder_.dispose(player, platform)
                );
    }

    public void logOut(Player player) {
        Platform platform = this.requireRouter_().getPlatform(player);
        this.sessionHolder_.dispose(player, platform);
    }

    public Optional<Account> getLoggedInAccount(Player player) {
        Platform platform = this.requireRouter_().getPlatform(player);
        return this.sessionHolder_.get(player, platform).map(OpenSession::account);
    }

    private OpenSession openSession_(Player player, Platform platform, Account account) throws LogginException {
        Account preparedAccount = this.accountService_.prepare(account);
        PlayerAccountLoginEvent event = new PlayerAccountLoginEvent(player, platform, preparedAccount);
        this.plugin_.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            throw new LogginException(LogginException.Reason.DENIED, preparedAccount, event.getDeniedMessage());
        }

        preparedAccount.markLoggedIn();
        preparedAccount.markJoinedFrom(platform.key());
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

    private CompletableFuture<SessionResult> startSession_(SessionJob job, Player player, Platform platform) {
        Optional<Account> account = this.accountService_.get(player, platform);
        if (account.isEmpty()) {
            return this.beginRegistration_(job, player, platform);
        }

        AuthenticationViewState authenticationViewState = account.get().getJoinedFromPlatforms().contains(platform.key())
                ? AuthenticationViewState.regular()
                : AuthenticationViewState.platformFirstTime();

        job.beforeAuthentication(new SessionJob.Authentication(player, platform, account.get()));
        return this.startAuthentication_(job, player, platform, account.get(), authenticationViewState);
    }

    private CompletableFuture<SessionResult> startAuthentication_(
            SessionJob job,
            Player player,
            Platform platform,
            Account account,
            AuthenticationViewState state
    ) {
        try {
            return this.await_(platform.views().authentication(player, state).present(player, platform), player)
                    .thenCompose(action -> this.completeAuthentication_(job, player, platform, account, state, action))
                    .exceptionally(this::failFlow_);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(this.failFlow_(ex));
        }
    }

    private CompletableFuture<SessionResult> completeAuthentication_(
            SessionJob job,
            Player player,
            Platform platform,
            Account account,
            AuthenticationViewState state,
            AuthenticationAction action
    ) {
        if (action instanceof AuthenticationAction.Password password) {
            try {
                Account loggedAccount = this.accountService_.authenticate(account, password.password());
                return CompletableFuture.completedFuture(this.openSessionAndNotify_(player, platform, loggedAccount, SessionResult::opened));
            } catch (LogginException ex) {
                return this.authenticationFailure_(job, player, platform, account, state, ex);
            }
        }
        if (action instanceof AuthenticationAction.Quit) {
            return CompletableFuture.completedFuture(SessionResult.unauthenticated());
        }
        if (action instanceof AuthenticationAction.Error error) {
            this.sendIfPresent_(player, error.message());
            return CompletableFuture.completedFuture(SessionResult.unauthenticated());
        }
        return CompletableFuture.completedFuture(this.failFlow_(new IllegalStateException("Unsupported authentication action: " + action)));
    }

    private CompletableFuture<SessionResult> authenticationFailure_(
            SessionJob job,
            Player player,
            Platform platform,
            Account account,
            AuthenticationViewState state,
            LogginException exception
    ) {
        return switch (exception.getReason()) {
            case INCORRECT_PASSWORD -> {
                PlayerAccountAuthenticationFailedEvent event =
                        new PlayerAccountAuthenticationFailedEvent(player, platform, account);
                this.plugin_.getServer().getPluginManager().callEvent(event);
                if (event.isKicked()) {
                    yield CompletableFuture.completedFuture(SessionResult.unauthenticated());
                }
                yield this.startAuthentication_(
                        job,
                        player,
                        platform,
                        account,
                        state.nextIncorrectPassword()
                );
            }
            case ACCOUNT_LOCKED -> {
                player.sendMessage(ChatColor.RED + this.plugin_.getRunical()
                        .translate(player, "kicked_message.locked")
                        .orDefault("")
                        .message());
                yield CompletableFuture.completedFuture(SessionResult.unauthenticated());
            }
            case ACCOUNT_NOT_FOUND -> this.beginRegistration_(job, player, platform);
            case DENIED -> {
                exception.getPublicMessage().ifPresent(player::sendMessage);
                yield CompletableFuture.completedFuture(SessionResult.unauthenticated());
            }
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

    private CompletableFuture<SessionResult> beginRegistration_(SessionJob job, Player player, Platform platform) {
        job.beforeRegistration(new SessionJob.Registration(player, platform));
        return this.startRegistration_(player, platform, RegistrationViewState.initial());
    }

    private CompletableFuture<SessionResult> startRegistration_(Player player, Platform platform, RegistrationViewState state) {
        try {
            return this.await_(platform.views().registration(player, state).present(player, platform), player)
                    .thenCompose(action -> this.completeRegistration_(player, platform, action))
                    .exceptionally(this::failFlow_);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(this.failFlow_(ex));
        }
    }

    private CompletableFuture<SessionResult> completeRegistration_(Player player, Platform platform, RegistrationAction action) {
        return switch (action) {
            case RegistrationAction.Password(String password) -> {
                try {
                    Account account = this.accountService_.register(player, platform, password);
                    yield CompletableFuture.completedFuture(this.openSessionAndNotify_(player, platform, account, SessionResult::registered));
                } catch (PasswordTooShortException ex) {
                    yield this.startRegistration_(
                            player,
                            platform,
                            RegistrationViewState.failed(new RegistrationViewState.Failure.PasswordTooShort(ex.getMinLength()))
                    );
                } catch (PasswordTooLongException ex) {
                    yield this.startRegistration_(
                            player,
                            platform,
                            RegistrationViewState.failed(new RegistrationViewState.Failure.PasswordTooLong(ex.getMaxLength()))
                    );
                } catch (InvalidPasswordException ex) {
                    yield this.startRegistration_(
                            player,
                            platform,
                            RegistrationViewState.failed(new RegistrationViewState.Failure.PasswordRejected())
                    );
                }
            }
            case RegistrationAction.Quit() -> CompletableFuture.completedFuture(SessionResult.unauthenticated());
            case RegistrationAction.Error(String message) -> {
                this.sendIfPresent_(player, message);
                yield CompletableFuture.completedFuture(SessionResult.unauthenticated());
            }
        };
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

    private CompletableFuture<SessionResult> completeJob_(
            SessionJob job,
            Player player,
            CompletableFuture<SessionResult> flow
    ) {
        CompletableFuture<SessionResult> completed = new CompletableFuture<>();
        flow.whenComplete((result, throwable) -> this.runSync_(() -> {
            SessionResult completion = throwable == null ? result : this.failFlow_(throwable);
            try {
                job.onComplete(new SessionJob.Completion(player, completion));
            } catch (RuntimeException ex) {
                this.plugin_.getLogger().log(Level.SEVERE, "Failed to complete a session job callback.", ex);
            }
            completed.complete(completion);
        }));
        return completed;
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

    private void sendIfPresent_(Player player, String message) {
        if (message != null && !message.isBlank()) {
            player.sendMessage(message);
        }
    }

    private PlatformRouter requireRouter_() {
        if (this.router_ == null) {
            throw new IllegalStateException("Platform router has not been configured.");
        }
        return this.router_;
    }
}
