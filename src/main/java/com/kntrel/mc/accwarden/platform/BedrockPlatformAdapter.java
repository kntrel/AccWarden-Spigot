package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.ModalFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class BedrockPlatformAdapter implements PlatformAdapter {

    private enum LoginMode { NEW, NEW_IN_PLATFORM }

    private final AccountService accountService_;
    private final AccWarden plugin_;
    private final FloodgateApi floodgateApi_ = FloodgateApi.getInstance();
    private final LinkedList<Login> logins_ = new LinkedList<>();
    private Listener listener_;
    private Level loggingLevel_ = Level.FINEST;

    public BedrockPlatformAdapter(AccountService accountService, AccWarden plugin) {
        this.accountService_ = accountService;
        this.plugin_ = plugin;
    }

    @Override
    public Platform getPlatform() {
        return Platform.BEDROCK;
    }

    @Override
    public CompletableFuture<Authentication> authenticate(Player player) {
        CompletableFuture<Authentication> future = new CompletableFuture<>();
        if (!this.floodgateApi_.isFloodgatePlayer(player.getUniqueId())) {
            future.complete(Authentication.rejected());
            return future;
        }

        Optional<Account> account = this.accountService_.get(player, Platform.BEDROCK);
        if (account.isPresent()) {
            this.log_("Bedrock player with UUID '" + player.getUniqueId() + "' already has Bedrock access.");
            future.complete(Authentication.passed(account.get()));
            return future;
        }

        Optional<Account> linkedJavaAccount = this.findLinkedJavaAccount_(player);
        if (linkedJavaAccount.isEmpty()) {
            future.complete(Authentication.unexistent());
            return future;
        }

        this.addLogin_(new Login(this, player, linkedJavaAccount.get(), LoginMode.NEW_IN_PLATFORM, future, null));
        return future;
    }

    @Override
    public CompletableFuture<Account> register(Player player) {
        CompletableFuture<Account> future = new CompletableFuture<>();
        if (!this.floodgateApi_.isFloodgatePlayer(player.getUniqueId())) {
            future.cancel(false);
            return future;
        }

        Account account = this.accountService_.create(player, Platform.BEDROCK);
        this.addLogin_(new Login(this, player, account, LoginMode.NEW, null, future));
        return future;
    }

    public void setLoggingLevel(Level loggingLevel) {
        this.loggingLevel_ = loggingLevel;
    }

    private Optional<Account> findLinkedJavaAccount_(Player player) {
        return this.accountService_.getByName(player.getName())
                .stream()
                .filter(account -> account.hasJava() && !account.hasBedrock())
                .findFirst();
    }

    private void addLogin_(Login login) {
        this.logins_.add(login);
        if (this.listener_ == null) {
            this.listener_ = new Listener(this);
            this.plugin_.getServer().getPluginManager().registerEvents(this.listener_, this.plugin_);
        }
        //Bukkit.getScheduler().runTask(this.plugin_, login::sendFormIfNeeded);
        //Bukkit.getScheduler().runTaskLater(this.plugin_, login::sendFormIfNeeded, 20L);
        Bukkit.getScheduler().runTaskLater(this.plugin_, login::sendFormIfNeeded, 60L);
    }

    private void removeLogin_(Login login) {
        this.logins_.remove(login);
        this.stopIfIdle_();
    }

    private void stopIfIdle_() {
        if (!this.logins_.isEmpty() || this.listener_ == null) {
            return;
        }
        HandlerList.unregisterAll(this.listener_);
        this.listener_ = null;
    }

    private void log_(String message) {
        this.plugin_.getLogger().log(this.loggingLevel_, message);
    }

    private static final class Login {

        private final BedrockPlatformAdapter adapter_;
        private final Player player_;
        private final Account account_;
        private final LoginMode mode_;
        private final CompletableFuture<Authentication> authenticationFuture_;
        private final CompletableFuture<Account> registrationFuture_;
        private boolean formSent_ = false;
        private int tries_ = 0;

        Login(
                BedrockPlatformAdapter adapter,
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
        }

        boolean hasFormBeenSent() {
            return this.formSent_;
        }

        void sendFormIfNeeded() {
            if (this.formSent_ || !this.player_.isOnline() || !this.adapter_.logins_.contains(this)) {
                return;
            }
            this.sendForm();
        }

        public boolean sendForm() {
            String basePath = "login.bedrock." + switch (this.mode_) {
                case NEW -> "new";
                case NEW_IN_PLATFORM -> "new_in_bedrock";
            } + ".";
            String placeholder = this.adapter_.plugin_.getRunical()
                    .translate(this.player_, basePath + "fields.placeholder")
                    .orDefault("")
                    .message();

            CustomForm.Builder formBuilder = CustomForm.builder();
            formBuilder
                    .label(this.adapter_.plugin_.getRunical()
                            .translate(this.player_, basePath + "disclosure")
                            .orDefault("")
                            .message())
                    .input(this.adapter_.plugin_.getRunical()
                            .translate(this.player_, basePath + "fields.password")
                            .orDefault("")
                            .message(), placeholder);
            if (this.mode_.equals(LoginMode.NEW)) {
                formBuilder.input(this.adapter_.plugin_.getRunical()
                        .translate(this.player_, basePath + "fields.confirm_password")
                        .orDefault("")
                        .message(), placeholder);
            }
            formBuilder.closedOrInvalidResultHandler(this::reject_);
            formBuilder.validResultHandler(this.mode_.equals(LoginMode.NEW)
                    ? this::handleRegistrationResponse_
                    : this::handleAuthenticationResponse_);

            boolean sent = this.adapter_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
            this.formSent_ = sent;
            if (!sent) {
                this.adapter_.log_("Floodgate did not accept a form for Bedrock player '" + this.player_.getName() + "'. Retrying later.");
            }
            return sent;
        }

        private void handleRegistrationResponse_(CustomFormResponse response) {
            String password = response.asInput(1);
            String confirmPassword = response.asInput(2);

            try {
                boolean match = this.account_.setPassword(password, confirmPassword);
                if (!match) {
                    this.sendErrorMessage_(this.adapter_.plugin_.getRunical()
                            .translate(this.player_, "error.invalid_input.no_match")
                            .orDefault("")
                            .message());
                    return;
                }
            } catch (PasswordTooShortException ex) {
                this.sendErrorMessage_(this.adapter_.plugin_.getRunical()
                        .translate(this.player_, "error.invalid_input.too_short")
                        .argument("min", ex.getMinLength())
                        .orDefault("")
                        .message());
                return;
            } catch (PasswordTooLongException ex) {
                this.sendErrorMessage_(this.adapter_.plugin_.getRunical()
                        .translate(this.player_, "error.invalid_input.too_long")
                        .argument("max", ex.getMaxLength())
                        .orDefault("")
                        .message());
                return;
            }

            if (this.registrationFuture_ != null && this.registrationFuture_.complete(this.account_)) {
                this.removeLater_();
            }
        }

        private void handleAuthenticationResponse_(CustomFormResponse response) {
            this.authenticate_(response.asInput(1));
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

            this.adapter_.accountService_.link(this.account_, this.player_, Platform.BEDROCK);
            this.completeAuthentication_(Authentication.passed(this.account_));
        }

        private void handleIncorrectPassword_() {
            this.tries_++;
            String errorBasePath = "error.incorrect_password.";
            AccWardenConfig conf = this.adapter_.plugin_.CONFIG;
            String errorMessage = this.adapter_.plugin_.getRunical()
                    .translate(this.player_, errorBasePath + "fine")
                    .orDefault("")
                    .message();
            if (this.tries_ >= conf.failLoginOdd()) {
                int next = conf.failLoginOdd() + conf.failLoginWarn();
                if (this.tries_ >= next && conf.failLoginAccountLock()) {
                    next += conf.failLoginLock();
                    if (this.tries_ >= next && conf.failLoginLock() > 0) {
                        this.account_.lock();
                    } else {
                        errorMessage = this.adapter_.plugin_.getRunical()
                                .translate(this.player_, errorBasePath + "warn")
                                .orDefault("")
                                .message();
                    }
                } else {
                    errorMessage = this.adapter_.plugin_.getRunical()
                            .translate(this.player_, errorBasePath + "odd")
                            .orDefault("")
                            .message();
                }
            }

            this.sendErrorMessage_(errorMessage);
        }

        private void sendErrorMessage_(String... errors) {
            ModalForm.Builder formBuilder = ModalForm.builder();

            StringBuilder errorBuilder = new StringBuilder();
            Arrays.stream(errors).forEach(error -> errorBuilder.append(error).append("\n"));

            formBuilder
                    .content(errorBuilder.toString())
                    .button1(this.adapter_.plugin_.getRunical()
                            .translate(this.player_, "login.bedrock.error_form.buttonRetry")
                            .orDefault("")
                            .message())
                    .button2(this.adapter_.plugin_.getRunical()
                            .translate(this.player_, "login.bedrock.error_form.buttonQuit")
                            .orDefault("")
                            .message());
            formBuilder.closedOrInvalidResultHandler(this::reject_);
            formBuilder.validResultHandler((ModalFormResponse response) -> {
                if (!response.clickedFirst()) {
                    this.reject_();
                    return;
                }
                this.formSent_ = false;
                this.sendFormIfNeeded();
            });

            boolean sent = this.adapter_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
            if (!sent) {
                this.formSent_ = false;
                this.adapter_.log_("Floodgate did not accept the error form for Bedrock player '" + this.player_.getName() + "'. Retrying login form later.");
            }
        }

        private void completeAuthentication_(Authentication authentication) {
            if (this.authenticationFuture_ != null && this.authenticationFuture_.complete(authentication)) {
                this.removeLater_();
            }
        }

        private void reject_() {
            this.cancel_();
            this.removeLater_();
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
    }

    private static final class Listener implements org.bukkit.event.Listener {

        private final BedrockPlatformAdapter adapter_;

        Listener(BedrockPlatformAdapter adapter) {
            this.adapter_ = adapter;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerMove(PlayerMoveEvent event) {
            Iterator<Login> iterator = this.adapter_.logins_.iterator();
            while (iterator.hasNext()) {
                Login login = iterator.next();
                if (login.player_.equals(event.getPlayer()) && !login.hasFormBeenSent()) {
                    login.sendFormIfNeeded();
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
