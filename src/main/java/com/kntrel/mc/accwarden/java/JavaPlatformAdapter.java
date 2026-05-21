package com.kntrel.mc.accwarden.java;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.platform.Authentication;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformAdapter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerCustomClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class JavaPlatformAdapter implements PlatformAdapter {

    private final AccountService accountService_;
    private final AccWarden plugin_;
    private final NamespacedKey submitActionKey_;
    private final NamespacedKey leaveActionKey_;
    private final LinkedList<JavaLoginForm> activeForms_ = new LinkedList<>();
    private Listener listener_;
    private Level loggingLevel_ = Level.FINEST;

    public JavaPlatformAdapter(AccountService accountService, AccWarden plugin) {
        this.accountService_ = accountService;
        this.plugin_ = plugin;
        this.submitActionKey_ = new NamespacedKey(plugin, "java_login_submit");
        this.leaveActionKey_ = new NamespacedKey(plugin, "java_login_leave");
    }

    @Override
    public Platform getPlatform() {
        return Platform.JAVA;
    }

    @Override
    public CompletableFuture<Authentication> authenticate(Player player) {
        Optional<Account> account = this.accountService_.get(player, Platform.JAVA);
        if (account.isEmpty()) {
            return CompletableFuture.completedFuture(Authentication.unexistent());
        }

        this.log_(player.getName() + " already has an account.");
        CompletableFuture<Authentication> future = new CompletableFuture<>();
        this.addForm_(JavaLoginForm.authentication(
                this.plugin_,
                player,
                account.get(),
                this.submitActionKey_,
                this.leaveActionKey_,
                future,
                this::removeForm_
        ));
        return future;
    }

    @Override
    public CompletableFuture<Account> register(Player player) {
        this.log_(player.getName() + " is connecting for the first time.");
        CompletableFuture<Account> future = new CompletableFuture<>();
        Account account = this.accountService_.create(player, Platform.JAVA);
        this.addForm_(JavaLoginForm.registration(
                this.plugin_,
                player,
                account,
                this.submitActionKey_,
                this.leaveActionKey_,
                future,
                this::removeForm_
        ));
        return future;
    }

    public void setLoggingLevel(Level loggingLevel) {
        this.loggingLevel_ = loggingLevel;
    }

    private void addForm_(JavaLoginForm form) {
        this.activeForms_.add(form);
        this.startListenerIfNeeded_();
        this.runSync_(form::show);
    }

    private void removeForm_(JavaLoginForm form) {
        this.activeForms_.remove(form);
        form.getPlayer().clearDialog();
        this.stopIfIdle_();
    }

    private void startListenerIfNeeded_() {
        if (this.listener_ != null) {
            return;
        }
        this.listener_ = new Listener(this);
        this.plugin_.getServer().getPluginManager().registerEvents(this.listener_, this.plugin_);
    }

    private void stopIfIdle_() {
        if (!this.activeForms_.isEmpty() || this.listener_ == null) {
            return;
        }
        HandlerList.unregisterAll(this.listener_);
        this.listener_ = null;
    }

    private Optional<JavaLoginForm> findForm_(Player player) {
        return this.activeForms_.stream()
                .filter(form -> form.isFor(player))
                .findFirst();
    }

    private boolean isJavaLoginAction_(NamespacedKey key) {
        return key.equals(this.submitActionKey_) || key.equals(this.leaveActionKey_);
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }

    private void log_(String message) {
        this.plugin_.getLogger().log(this.loggingLevel_, message);
    }

    private static final class Listener implements org.bukkit.event.Listener {

        private final JavaPlatformAdapter adapter_;

        Listener(JavaPlatformAdapter adapter) {
            this.adapter_ = adapter;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerCustomClick(PlayerCustomClickEvent event) {
            if (!this.adapter_.isJavaLoginAction_(event.getId())) {
                return;
            }

            this.adapter_.findForm_(event.getPlayer()).ifPresent(form -> {
                if (event.getId().equals(this.adapter_.leaveActionKey_)) {
                    form.leaveServer();
                    return;
                }
                form.submit(event.getData());
            });
        }

        @EventHandler
        void onPlayerQuit(PlayerQuitEvent event) {
            Iterator<JavaLoginForm> iterator = this.adapter_.activeForms_.iterator();
            while (iterator.hasNext()) {
                JavaLoginForm form = iterator.next();
                if (form.isFor(event.getPlayer())) {
                    form.cancel();
                    iterator.remove();
                }
            }
            this.adapter_.stopIfIdle_();
        }
    }
}
