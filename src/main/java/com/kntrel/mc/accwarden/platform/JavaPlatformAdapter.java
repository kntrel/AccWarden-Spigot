package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class JavaPlatformAdapter implements PlatformAdapter {

    private enum LoginMode { NEW, EXISTING }

    private static final int REFRESH_RATE = 40;

    private final AccountService accountService_;
    private final AccWarden plugin_;
    private final LinkedList<Login> logins_ = new LinkedList<>();
    private Listener listener_;
    private BukkitRunnable informationRefresher_ = null;
    private Level loggingLevel_ = Level.FINEST;

    public JavaPlatformAdapter(AccountService accountService, AccWarden plugin) {
        this.accountService_ = accountService;
        this.plugin_ = plugin;
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

        CompletableFuture<Authentication> future = new CompletableFuture<>();
        this.addLogin_(new Login(this, player, account.get(), LoginMode.EXISTING, future, null));
        return future;
    }

    @Override
    public CompletableFuture<Account> register(Player player) {
        CompletableFuture<Account> future = new CompletableFuture<>();
        Account account = this.accountService_.create(player, Platform.JAVA);
        this.addLogin_(new Login(this, player, account, LoginMode.NEW, null, future));
        return future;
    }

    public void setLoggingLevel(Level loggingLevel) {
        this.loggingLevel_ = loggingLevel;
    }

    private void addLogin_(Login login) {
        this.logins_.add(login);
        if (this.informationRefresher_ == null || this.informationRefresher_.isCancelled()) {
            this.resetRefresher_();
        }
        if (this.listener_ == null) {
            this.listener_ = new Listener(this);
            this.plugin_.getServer().getPluginManager().registerEvents(this.listener_, this.plugin_);
        }
    }

    private void removeLogin_(Login login) {
        this.logins_.remove(login);
        this.stopIfIdle_();
    }

    private void stopIfIdle_() {
        if (!this.logins_.isEmpty()) {
            return;
        }
        if (this.informationRefresher_ != null && !this.informationRefresher_.isCancelled()) {
            this.informationRefresher_.cancel();
        }
        if (this.listener_ != null) {
            HandlerList.unregisterAll(this.listener_);
            this.listener_ = null;
        }
    }

    private void resetRefresher_() {
        this.informationRefresher_ = new BukkitRunnable() {
            @Override
            public void run() {
                JavaPlatformAdapter.this.logins_.forEach(Login::refresh);
            }
        };
        this.informationRefresher_.runTaskTimerAsynchronously(this.plugin_, REFRESH_RATE, REFRESH_RATE);
    }

    private void log_(String message) {
        this.plugin_.getLogger().log(this.loggingLevel_, message);
    }

    private static final class Login {

        private final JavaPlatformAdapter adapter_;
        private final Player player_;
        private final Account account_;
        private final LoginMode mode_;
        private final CompletableFuture<Authentication> authenticationFuture_;
        private final CompletableFuture<Account> registrationFuture_;
        private int tries_ = 0;

        Login(
                JavaPlatformAdapter adapter,
                Player player,
                Account account,
                LoginMode mode,
                CompletableFuture<Authentication> authenticationFuture,
                CompletableFuture<Account> registrationFuture
        ) {
            this.adapter_ = adapter;
            this.player_ = player;
            this.account_ = account;
            this.mode_ = mode;
            this.authenticationFuture_ = authenticationFuture;
            this.registrationFuture_ = registrationFuture;
            this.logMode_();
            this.refresh();
            this.showChatMessage_();
        }

        void refresh() {
            String basePath = "login.java." + switch (this.mode_) {
                case NEW -> "new";
                case EXISTING -> "existing";
            } + ".";

            String title = this.adapter_.plugin_.getRunical()
                    .translate(this.player_, basePath + "title")
                    .orDefault("")
                    .message();
            String subtitle = this.adapter_.plugin_.getRunical()
                    .translate(this.player_, basePath + "subtitle")
                    .orDefault("")
                    .message();
            String actionBar = this.adapter_.plugin_.getRunical()
                    .translate(this.player_, basePath + "actionbar")
                    .orDefault("")
                    .message();

            if (!(title.equals("") && subtitle.equals(""))) {
                this.player_.sendTitle(title, subtitle, 0, REFRESH_RATE, 40);
            }

            if (!actionBar.equals("")) {
                this.player_.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));
            }
        }

        void readMessage(AsyncPlayerChatEvent event) {
            String[] words = event.getMessage().split(" ");

            switch (this.mode_) {
                case NEW -> {
                    if (words.length != 2) {
                        return;
                    }
                    event.setCancelled(true);
                    this.register_(words[0], words[1]);
                }
                case EXISTING -> {
                    if (words.length != 1) {
                        return;
                    }
                    event.setCancelled(true);
                    this.authenticate_(words[0]);
                }
            }
        }

        private void register_(String password, String confirmPassword) {
            try {
                boolean match = this.account_.setPassword(password, confirmPassword);
                if (!match) {
                    this.player_.sendMessage(ChatColor.RED + this.adapter_.plugin_.getRunical()
                            .translate(this.player_, "error.invalid_input.no_match")
                            .orDefault("")
                            .message());
                    this.showChatMessage_();
                    return;
                }
            } catch (PasswordTooLongException ex) {
                this.player_.sendMessage(ChatColor.RED + this.adapter_.plugin_.getRunical()
                        .translate(this.player_, "error.invalid_input.too_long")
                        .argument("max", ex.getMaxLength())
                        .orDefault("")
                        .message());
                this.showChatMessage_();
                return;
            } catch (PasswordTooShortException ex) {
                this.player_.sendMessage(ChatColor.RED + this.adapter_.plugin_.getRunical()
                        .translate(this.player_, "error.invalid_input.too_short")
                        .argument("min", ex.getMinLength())
                        .orDefault("")
                        .message());
                this.showChatMessage_();
                return;
            }

            if (this.registrationFuture_ != null && this.registrationFuture_.complete(this.account_)) {
                this.removeLater_();
            }
        }

        private void authenticate_(String password) {
            if (this.account_.isLocked()) {
                this.completeAuthentication_(Authentication.rejected());
                return;
            }

            if (!this.account_.checkPassword(password)) {
                this.handleIncorrectPassword_();
                return;
            }

            this.completeAuthentication_(Authentication.passed(this.account_));
        }

        private void handleIncorrectPassword_() {
            this.tries_++;
            AccWardenConfig conf = this.adapter_.plugin_.CONFIG;
            String endPath = "fine";
            if (this.tries_ >= conf.failLoginOdd()) {
                int next = conf.failLoginOdd() + conf.failLoginWarn();
                if (this.tries_ >= next && conf.failLoginAccountLock()) {
                    next += conf.failLoginLock();
                    if (this.tries_ >= next && conf.failLoginLock() > 0) {
                        this.account_.lock();
                    } else {
                        endPath = "warn";
                    }
                } else {
                    endPath = "odd";
                }
            }

            this.player_.sendMessage(ChatColor.RED + this.adapter_.plugin_.getRunical()
                    .translate(this.player_, "error.incorrect_password." + endPath)
                    .orDefault("")
                    .message());
            this.showChatMessage_();
        }

        private void completeAuthentication_(Authentication authentication) {
            if (this.authenticationFuture_ != null && this.authenticationFuture_.complete(authentication)) {
                this.removeLater_();
            }
        }

        private void cancel_() {
            if (this.authenticationFuture_ != null) {
                this.authenticationFuture_.complete(Authentication.rejected());
            }
            if (this.registrationFuture_ != null) {
                this.registrationFuture_.cancel(false);
            }
        }

        private void removeLater_() {
            Bukkit.getScheduler().runTask(this.adapter_.plugin_, () -> this.adapter_.removeLogin_(this));
        }

        private void showChatMessage_() {
            String message = this.adapter_.plugin_.getRunical()
                    .translate(this.player_, "login.java." + switch (this.mode_) {
                        case NEW -> "new";
                        case EXISTING -> "existing";
                    } + ".chat")
                    .orDefault("")
                    .message();

            if (!message.equals("")) {
                this.player_.sendMessage(message);
            }
        }

        private void logMode_() {
            switch (this.mode_) {
                case NEW -> this.adapter_.log_(this.player_.getName() + " is connecting for the first time.");
                case EXISTING -> this.adapter_.log_(this.player_.getName() + " already has an account.");
            }
        }
    }

    private static final class Listener implements org.bukkit.event.Listener {

        private final JavaPlatformAdapter adapter_;

        Listener(JavaPlatformAdapter adapter) {
            this.adapter_ = adapter;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerMessage(AsyncPlayerChatEvent event) {
            Iterator<Login> iterator = this.adapter_.logins_.iterator();
            while (iterator.hasNext()) {
                Login login = iterator.next();
                if (login.player_.equals(event.getPlayer())) {
                    login.readMessage(event);
                }
            }
        }

        @EventHandler
        void onPlayerQuit(PlayerQuitEvent event) {
            Iterator<Login> iterator = this.adapter_.logins_.iterator();
            while (iterator.hasNext()) {
                Login login = iterator.next();
                if (login.player_.equals(event.getPlayer())) {
                    login.cancel_();
                    iterator.remove();
                }
            }
            this.adapter_.stopIfIdle_();
        }
    }
}
