package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.event.PlayerAccountAuthenticationFailedEvent;
import com.kntrel.mc.accwarden.session.SessionJob;
import com.kntrel.mc.accwarden.session.SessionResult;
import com.kntrel.mc.accwarden.session.SessionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class AccWardenGate implements Listener {

    private final AccWarden plugin_;
    private final SessionService sessionService_;
    private final AccwarderGatekeeper gatekeeper_;
    private final NetworkKeyResolver networkKeyResolver_;
    private final Holder holder_;
    private final ThrottleMessageFormatter throttleMessageFormatter_;

    public AccWardenGate(AccWarden plugin, AccwarderGatekeeper gatekeeper, Holder holder) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.sessionService_ = this.plugin_.getSessionService();
        this.gatekeeper_ = Objects.requireNonNull(gatekeeper, "gatekeeper");
        this.networkKeyResolver_ = new NetworkKeyResolver(32, 64);
        this.holder_ = Objects.requireNonNull(holder, "holder");
        this.throttleMessageFormatter_ = new ThrottleMessageFormatter(this.plugin_.getRunical());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void OnPlayerLogin(PlayerLoginEvent e) {
        if (e.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Decision decision = this.gatekeeper_.considerConnection(
                this.networkKeyResolver_.resolve(e.getAddress())
        );
        if (decision instanceof Decision.Throttled throttled) {
            e.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    this.throttledMessage_(e.getPlayer(), throttled)
            );
        }
    }

    @EventHandler
    void OnPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        NetworkKey network = this.networkKeyResolver_.resolve(player);
        LoginRequest request = new LoginRequest(player.getUniqueId(), network);

        Decision decision = this.gatekeeper_.considerLogin(request);
        if (decision instanceof Decision.Throttled throttled) {
            this.kick_(player, this.throttledMessage_(player, throttled));
            return;
        }

        SessionJob job = SessionJob.forPlayer(player)
                .onUncached(context -> this.holder_.hold(context.player()))
                .onComplete(completion -> {
                    if (completion.player().isOnline()) {
                        this.completeSession_(completion.player(), completion.result());
                    }
                })
                .build();
        this.sessionService_.openSession(job);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void OnPlayerAuthenticationFailed(PlayerAccountAuthenticationFailedEvent e) {
        Player player = e.getPlayer();
        LoginRequest request = new LoginRequest(
                player.getUniqueId(),
                this.networkKeyResolver_.resolve(player)
        );
        Decision decision = this.gatekeeper_.recordFailedLogin(request);
        if (decision instanceof Decision.Throttled throttled) {
            e.kick(this.throttledMessage_(player, throttled));
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
                        .translate(player, "kicked_message.not_logged")
                        .orDefault("")
                        .message()
        );
    }

    private void kick_(Player player, String message) {
        this.holder_.hold(player);
        player.kickPlayer(message);
    }

    private String throttledMessage_(Player player, Decision.Throttled throttled) {
        return this.throttleMessageFormatter_.format(player, throttled);
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
