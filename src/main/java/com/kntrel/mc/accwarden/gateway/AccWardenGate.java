package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.event.PlayerAccountAuthenticationFailedEvent;
import com.kntrel.mc.accwarden.session.SessionResult;
import com.kntrel.mc.accwarden.session.SessionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class AccWardenGate implements Listener {

    private final AccWarden plugin_;
    private final SessionService sessionService_;
    private final AccwarderGatekeeper gatekeeper_;
    private final NetworkKeyResolver networkKeyResolver_;
    private final Holder holder_;

    public AccWardenGate(AccWarden plugin, AccwarderGatekeeper gatekeeper, Holder holder) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.sessionService_ = this.plugin_.getSessionService();
        this.gatekeeper_ = Objects.requireNonNull(gatekeeper, "gatekeeper");
        this.networkKeyResolver_ = new NetworkKeyResolver(32, 64);
        this.holder_ = Objects.requireNonNull(holder, "holder");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void OnPlayerLogin(PlayerLoginEvent e) {
        if (e.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Decision decision = this.gatekeeper_.considerConnection(
                this.networkKeyResolver_.resolve(e.getAddress())
        );
        if (decision instanceof Decision.Throttled) {
            e.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    this.throttledMessage_(e.getPlayer())
            );
        }
    }

    @EventHandler
    void OnPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        NetworkKey network = this.networkKeyResolver_.resolve(player);
        LoginRequest request = new LoginRequest(player.getUniqueId(), network);

        if (this.gatekeeper_.considerLogin(request) instanceof Decision.Throttled) {
            this.kick_(player, this.throttledMessage_(player));
            return;
        }

        CompletableFuture<SessionResult> session = this.sessionService_.openSession(player);
        if (!session.isDone()) {
            this.holder_.hold(player);
        }

        session.whenComplete((result, throwable) -> this.runSync_(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (throwable != null) {
                this.logFailure_(player, throwable);
                this.kick_(player);
                return;
            }
            this.completeSession_(player, result);
        }));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void OnPlayerAuthenticationFailed(PlayerAccountAuthenticationFailedEvent e) {
        Player player = e.getPlayer();
        LoginRequest request = new LoginRequest(
                player.getUniqueId(),
                this.networkKeyResolver_.resolve(player)
        );
        if (this.gatekeeper_.recordFailedLogin(request) instanceof Decision.Throttled) {
            e.kick(this.throttledMessage_(player));
        }
    }

    @EventHandler
    void OnPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (this.holder_.isHeld(player)) {
            return;
        }
        this.sessionService_.rememberSession(player);
    }

    private void completeSession_(Player player, SessionResult result) {
        if (result instanceof SessionResult.Caches
                || result instanceof SessionResult.Opened
                || result instanceof SessionResult.Registered) {
            this.holder_.unhold(player);
            return;
        }
        if (result instanceof SessionResult.Failed failed) {
            this.logFailure_(player, failed.cause());
        }
        this.kick_(player);
    }

    private void kick_(Player player) {
        this.kick_(
                player,
                this.plugin_.getRunical()
                        .translate(player, "error.kicked.not_logged")
                        .orDefault("")
                        .message()
        );
    }

    private void kick_(Player player, String message) {
        this.holder_.hold(player);
        player.kickPlayer(message);
    }

    private String throttledMessage_(Player player) {
        return this.plugin_.getRunical()
                .translate(player, "error.kicked.throttled")
                .orDefault("Too many authentication attempts. Please try again later.")
                .message();
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }

    private void logFailure_(Player player, Throwable throwable) {
        Throwable failure = unwrap_(throwable);
        if (failure instanceof CancellationException) {
            return;
        }
        this.plugin_.getLogger().log(
                Level.SEVERE,
                "Failed to complete account session flow for " + player.getName() + ".",
                failure
        );
    }

    private static Throwable unwrap_(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
