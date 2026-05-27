package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;
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
    public Optional<OpenSession> get(Player player, Platform platform) {
        return this.get(platform.accountUuid(player));
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
    public void dispose(Player player, Platform platform) {
        this.dispose(platform.accountUuid(player));
    }
    public Optional<OpenSession> open(Account account, InetSocketAddress address, Platform platform) {
        Optional<UUID> platformId = account.getPlatformUuid(platform);
        if (platformId.isEmpty()) {
            this.log_("Unable to open session: account has no " + platform.toString().toLowerCase() + " UUID.");
            return Optional.empty();
        }
        UUID id = platformId.get();
        this.dispose(id);
        OpenSession session = new OpenSession(account, address, platform);
        this.sessions_.put(id, session);
        this.log_("Session for UUID " + id + " open.");
        return Optional.of(session);
    }
    public Optional<OpenSession> open(Account account, Player player, Platform platform) {
        return this.open(account, player.getAddress(), platform);
    }
    public Optional<OpenSession> claim(UUID uuid, InetSocketAddress address, Platform platform) {
        Optional<SessionEntry> entry = this.findSession_(uuid, platform);
        if (entry.isEmpty()) {
            this.log_("No session opened for UUID " + uuid.toString());
            return Optional.empty();
        }
        OpenSession session = entry.get().session();
        if (!session.address().getAddress().equals(address.getAddress())) {
            this.log_("Found an open session for UUID " + uuid.toString() + ", but the IP address doesn't match");
            return Optional.empty();
        }
        if (!this.crossPlatformSessions_) {
            if (session.platform().equals(platform)) {
                this.log_("UUID '" + uuid + "' joined with an open session.");
                return this.promote_(entry.get(), address, platform);
            } else {
                this.log_( "UUID '" + uuid + "' has an open session, but joined form a different platform. The 'crossPlatformLogin' setting is disabled. Denying access.");
                return Optional.empty();
            }
        }
        if (session.account().hasPlatform(platform)) {
            this.log_("UUID '" + uuid + "' joined with an open session.");
            return this.promote_(entry.get(), address, platform);
        } else {
            this.log_("UUID '" + uuid + "' has an open session, but it's its first time joining from " + platform.toString().toLowerCase() + ". Verification required.");
            return Optional.empty();
        }
    }
    public Optional<OpenSession> claim(String uuid, InetSocketAddress address, Platform platform)
    {
        return this.claim(UUID.fromString(uuid), address, platform);
    }
    public Optional<OpenSession> claim(Player player, Platform platform) {
        return this.claim(platform.accountUuid(player), player.getAddress(), platform);
    }
    public void openNew(Account account, InetSocketAddress address, Platform platform) {
        Optional<UUID> platformId = account.getPlatformUuid(platform);
        if (platformId.isEmpty()) {
            this.log_("Unable to open session: account has no " + platform.toString().toLowerCase() + " UUID.");
            return;
        }
        UUID id = platformId.get();
        this.dispose(id);
        if (this.holdTime_ < 1) { return; }

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
    private Optional<SessionEntry> findSession_(UUID uuid, Platform platform) {
        OpenSession session = this.sessions_.get(uuid);
        if (session != null) {
            return Optional.of(new SessionEntry(uuid, session));
        }
        if (!this.crossPlatformSessions_) {
            return Optional.empty();
        }
        return this.sessions_
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue()
                        .account()
                        .getPlatformUuid(platform)
                        .filter(uuid::equals)
                        .isPresent())
                .findFirst()
                .map(entry -> new SessionEntry(entry.getKey(), entry.getValue()));
    }
    private Optional<OpenSession> promote_(SessionEntry entry, InetSocketAddress address, Platform platform) {
        Optional<UUID> platformId = entry.session().account().getPlatformUuid(platform);
        if (platformId.isEmpty()) {
            this.log_("Unable to claim session: account has no " + platform.toString().toLowerCase() + " UUID.");
            return Optional.empty();
        }
        UUID id = platformId.get();
        this.dispose(entry.key());
        if (!entry.key().equals(id)) {
            this.dispose(id);
        }
        OpenSession session = new OpenSession(entry.session().account(), address, platform);
        this.sessions_.put(id, session);
        return Optional.of(session);
    }

    private record SessionEntry(UUID key, OpenSession session) {}
}
