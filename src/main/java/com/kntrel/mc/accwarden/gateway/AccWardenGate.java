package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.session.SessionResult;
import com.kntrel.mc.accwarden.session.SessionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    private final NamespacedKey notLoggedKey_;
    private final NamespacedKey gameModeKey_;

    public AccWardenGate(AccWarden plugin, AccwarderGatekeeper gatekeeper) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.sessionService_ = this.plugin_.getSessionService();
        this.gatekeeper_ = Objects.requireNonNull(gatekeeper, "gatekeeper");
        this.networkKeyResolver_ = new NetworkKeyResolver(32, 64);
        this.notLoggedKey_ = new NamespacedKey(plugin, "notLogged");
        this.gameModeKey_ = new NamespacedKey(plugin, "loggedGameMode");
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
            this.hold_(player);
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
            this.completeSession_(player, request, result);
        }));
    }

    @EventHandler
    void OnPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (!this.isLogged_(player)) {
            return;
        }
        this.sessionService_.rememberSession(player);
    }

    @EventHandler
    void OnPlayerMove(PlayerMoveEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerOpenInventory(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) { return; }
        if (this.isLogged_(player)) { return; }
        player.closeInventory();
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteract(PlayerInteractEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void OnPlayerUseChat(AsyncPlayerChatEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.send_message")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerIssueCommand(PlayerCommandPreprocessEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.DARK_GRAY + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.issue_command")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void onPlayerDropItem(PlayerDropItemEvent e) {
        if (this.isLogged_(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    private void completeSession_(
            Player player,
            LoginRequest request,
            SessionResult result
    ) {
        if (result instanceof SessionResult.Caches
                || result instanceof SessionResult.Opened
                || result instanceof SessionResult.Registered) {
            this.releaseHold_(player);
            return;
        }
        if (result instanceof SessionResult.Failed failed) {
            this.logFailure_(player, failed.cause());
        }
        if (result instanceof SessionResult.Unauthenticated) {
            if (this.gatekeeper_.recordFailedLogin(request) instanceof Decision.Throttled) {
                this.kick_(player, this.throttledMessage_(player));
                return;
            }
        }
        this.kick_(player);
    }

    private boolean isLogged_(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!container.has(this.notLoggedKey_, PersistentDataType.BYTE)) {
            return true;
        }

        Byte value = container.get(this.notLoggedKey_, PersistentDataType.BYTE);
        return value != null && value <= 0;
    }

    private void hold_(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(this.notLoggedKey_, PersistentDataType.BYTE, (byte) 1);
        if (!container.has(this.gameModeKey_, PersistentDataType.STRING)) {
            container.set(this.gameModeKey_, PersistentDataType.STRING, player.getGameMode().toString());
        }
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void releaseHold_(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.remove(this.notLoggedKey_);
        if (!container.has(this.gameModeKey_, PersistentDataType.STRING)) {
            return;
        }
        String gameModeString = container.get(this.gameModeKey_, PersistentDataType.STRING);
        container.remove(this.gameModeKey_);
        try {
            player.setGameMode(GameMode.valueOf(gameModeString));
        } catch (IllegalArgumentException ignored) {}
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
        this.hold_(player);
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
