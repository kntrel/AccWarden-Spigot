package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.BiConsumer;

public final class SessionHolder {

    //FIELDS
    private final HashMap<UUID, OpenSession> sessions_ = new HashMap<>();
    private final HashMap<UUID, BukkitRunnable> closers_ = new HashMap<>();
    private final Logger logger_;
    private final BiConsumer<BukkitRunnable, Long> scheduleCloser_;
    private Level loggingLevel_ = Level.FINEST;
    private int holdTime_ = 0;

    //CONSTRUCTORS
    public SessionHolder(JavaPlugin plugin) {
        this(
                Objects.requireNonNull(plugin, "plugin").getLogger(),
                (closer, delay) -> closer.runTaskLater(plugin, delay)
        );
    }

    SessionHolder(Logger logger, BiConsumer<BukkitRunnable, Long> scheduleCloser) {
        this.logger_ = Objects.requireNonNull(logger, "logger");
        this.scheduleCloser_ = Objects.requireNonNull(scheduleCloser, "scheduleCloser");
    }

    //SETTERS
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
        Optional<UUID> accountId = account.getUuid();
        if (accountId.isEmpty()) {
            this.log_("Unable to open session: account has no UUID.");
            return Optional.empty();
        }
        UUID id = accountId.get();
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
        Optional<SessionEntry> entry = this.findSession_(uuid);
        if (entry.isEmpty()) {
            this.log_("No session opened for UUID " + uuid.toString());
            return Optional.empty();
        }
        OpenSession session = entry.get().session();
        if (!session.address().getAddress().equals(address.getAddress())) {
            this.log_("Found an open session for UUID " + uuid.toString() + ", but the IP address doesn't match");
            return Optional.empty();
        }
        if (session.platform().key() != platform.key()) {
            this.log_("UUID '" + uuid + "' has a cached session from "
                    + session.platform().displayName() + ", but is joining from "
                    + platform.displayName() + ". Cached authentication never crosses platform boundaries.");
            return Optional.empty();
        }
        this.log_("UUID '" + uuid + "' joined with an open session.");
        return this.promote_(entry.get(), address, platform);
    }
    public Optional<OpenSession> claim(String uuid, InetSocketAddress address, Platform platform)
    {
        return this.claim(UUID.fromString(uuid), address, platform);
    }
    public Optional<OpenSession> claim(Player player, Platform platform) {
        return this.claim(platform.accountUuid(player), player.getAddress(), platform);
    }
    public void openNew(Account account, InetSocketAddress address, Platform platform) {
        Optional<UUID> accountId = account.getUuid();
        if (accountId.isEmpty()) {
            this.log_("Unable to open session: account has no UUID.");
            return;
        }
        UUID id = accountId.get();
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

        this.scheduleCloser_.accept(closer, ((long) this.holdTime_) * 20);

        this.log_("Session for UUID " + id + " open.");

    }
    public void openNew(Account account, Player player, Platform platform) {
        this.openNew(account,player.getAddress(),platform);
    }

    //PRIVATE METHODS
    private void log_(String message, Level level) {
        this.logger_.log(level, message);
    }
    private void log_(String message) {
        this.log_(message, this.loggingLevel_);
    }
    private Optional<SessionEntry> findSession_(UUID uuid) {
        OpenSession session = this.sessions_.get(uuid);
        if (session != null) {
            return Optional.of(new SessionEntry(uuid, session));
        }
        return Optional.empty();
    }
    private Optional<OpenSession> promote_(SessionEntry entry, InetSocketAddress address, Platform platform) {
        Optional<UUID> accountId = entry.session().account().getUuid();
        if (accountId.isEmpty()) {
            this.log_("Unable to claim session: account has no UUID.");
            return Optional.empty();
        }
        UUID id = accountId.get();
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
