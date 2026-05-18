package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.Platform;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class SessionHolder {

    //FIELDS
    private final HashMap<UUID, OpenSession> sessions_ = new HashMap<>();
    private final HashMap<UUID, BukkitRunnable> closers_ = new HashMap<>();
    private final JavaPlugin plugin_;
    private Level loggingLevel_ = Level.FINEST;
    private boolean crossPlatformSessions_ = false;
    private int holdTime_ = 0;

    //CONSTRUCTORS
    public SessionHolder(JavaPlugin plugin) {
        this.plugin_ = plugin;
    }

    //SETTERS
    public void setCrossPlatformSessions(boolean b) {
        this.crossPlatformSessions_ = b;
    }
    public void setHoldTime(int seconds) {
        this.holdTime_ = seconds;
    }
    public void setLoggingLevel(Level level) {
        this.loggingLevel_ = level;
    }

    //METHODS
    public Optional<OpenSession> get(UUID uuid) {
        return Optional.ofNullable(this.sessions_.get(uuid));
    }
    public Optional<OpenSession> get(String uuid) {
        return this.get(UUID.fromString(uuid));
    }
    public Optional<OpenSession> get(Player player) {
        return this.get(player.getUniqueId());
    }
    public boolean has(UUID uuid) {
        return this.get(uuid).isPresent();
    }
    public boolean has(String uuid) {
        return this.get(uuid).isPresent();
    }
    public boolean has(Player player) {
        return this.get(player).isPresent();
    }
    public void dispose(UUID uuid) {
        if (!this.sessions_.containsKey(uuid)) { return; }
        this.sessions_.remove(uuid);
        BukkitRunnable closer = this.closers_.remove(uuid);
        if (closer == null) { return; }
        if (!closer.isCancelled()) { closer.cancel(); }
    }
    public void dispose(String uuid) {
        this.dispose(UUID.fromString(uuid));
    }
    public void dispose(Player player) {
        this.dispose(player.getUniqueId());
    }
    public boolean claim(UUID uuid, InetSocketAddress address, Platform platform) {
        OpenSession session = this.sessions_.get(uuid);
        if (session == null) {
            this.log_("No session opened for UUID " + uuid.toString());
            return false;
        }
        this.dispose(uuid);
        if (!session.address().getAddress().equals(address.getAddress())) {
            this.log_("Found an open session for UUID " + uuid.toString() + ", but the IP address doesn't match");
            return false;
        }
        if (!this.crossPlatformSessions_) {
            if (session.platform().equals(platform)) {
                this.log_("UUID '" + uuid + "' joined with an open session.");
                return true;
            } else {
                this.log_( "UUID '" + uuid + "' has an open session, but joined form a different platform. The 'crossPlatformLogin' setting is disabled. Denying access.");
                return false;
            }
        }
        if (session.account().hasPlatform(platform)) {
            this.log_("UUID '" + uuid + "' joined with an open session.");
            return true;
        } else {
            this.log_("UUID '" + uuid + "' has an open session, but it's its first time joining from " + platform.toString().toLowerCase() + ". Verification required.");
            return false;
        }
    }
    public boolean claim(String uuid, InetSocketAddress address, Platform platform)
    {
        return this.claim(UUID.fromString(uuid), address, platform);
    }
    public boolean claim(Player player, Platform platform) {
        return this.claim(player.getUniqueId(), player.getAddress(), platform);
    }
    public void openNew(Account account, InetSocketAddress address, Platform platform) {
        if (this.holdTime_ < 1) { return; }
        Optional<UUID> platformId = account.getPlatformUuid(platform);
        if (platformId.isEmpty()) {
            this.log_("Unable to open session: account has no " + platform.toString().toLowerCase() + " UUID.");
            return;
        }
        UUID id = platformId.get();

        BukkitRunnable closer = new BukkitRunnable() {
            private UUID id_ = id;

            @Override
            public void run() {
                SessionHolder.this.dispose(id_);
                SessionHolder.this.log_("Closed session for UUID '" + id_ + "' after " + SessionHolder.this.holdTime_ + " seconds.");
            }
        };

        this.sessions_.put(id, new OpenSession(account, address, platform));
        this.closers_.put(id, closer);

        closer.runTaskLater(this.plugin_, ((long) this.holdTime_) * 20);

        this.log_("Session for UUID " + id + " open.");

    }
    public void openNew(Account account, Player player, Platform platform) {
        this.openNew(account,player.getAddress(),platform);
    }

    //PRIVATE METHODS
    private void log_(String message, Level level) {
        this.plugin_.getLogger().log(level, message);
    }
    private void log_(String message) {
        this.log_(message, this.loggingLevel_);
    }
}
