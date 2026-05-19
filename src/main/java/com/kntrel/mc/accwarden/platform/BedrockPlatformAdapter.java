package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.LogginException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.session.SessionService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.geysermc.cumulus.CustomForm;
import org.geysermc.cumulus.ModalForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.ModalFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.logging.Level;

public final class BedrockPlatformAdapter implements PlatformAdapter {

    private enum LoginMode { NEW, NEW_IN_PLATFORM }

    private final SessionService sessionService_;
    private final AccWarden plugin_;
    private final FloodgateApi floodgateApi_ = FloodgateApi.getInstance();
    private final LinkedList<Login> logins_ = new LinkedList<>();
    private Listener listener_;
    private Level loggingLevel_ = Level.FINEST;

    public BedrockPlatformAdapter(SessionService sessionService, AccWarden plugin) {
        this.sessionService_ = sessionService;
        this.plugin_ = plugin;
    }

    @Override
    public Platform getPlatform() {
        return Platform.BEDROCK;
    }

    @Override
    public Optional<Account> findLinkedAccount(Player player, AccountService accountService) {
        return accountService.getByName(player.getName())
                .stream()
                .filter(account -> account.hasJava() && !account.hasBedrock())
                .findFirst();
    }

    @Override
    public void authenticate(Player player, Account account) {
        FloodgatePlayer floodgatePlayer = this.floodgateApi_.getPlayer(player.getUniqueId());
        if (floodgatePlayer == null) {
            return;
        }

        if (account.hasBedrock()) {
            this.log_("Bedrock player with UUID '" + player.getUniqueId() + "' already has Bedrock access. Logging in.");
            try {
                this.sessionService_.openSession(player, Platform.BEDROCK, account);
                this.sessionService_.sendLoggedIn(player);
            } catch (LogginException ex) {
                ex.getPublicMessage().ifPresent(player::sendMessage);
            }
            return;
        }

        this.addLogin_(new Login(this, player, account, LoginMode.NEW_IN_PLATFORM));
    }

    @Override
    public void collectPassword(Player player, Account account) {
        this.addLogin_(new Login(this, player, account, LoginMode.NEW));
    }

    public void setLoggingLevel(Level loggingLevel) {
        this.loggingLevel_ = loggingLevel;
    }

    private void addLogin_(Login login) {
        this.logins_.add(login);
        if (this.listener_ == null) {
            this.listener_ = new Listener(this);
            this.plugin_.getServer().getPluginManager().registerEvents(this.listener_, this.plugin_);
        }
    }

    private void removeLogin_(Login login) {
        this.logins_.remove(login);
        if (this.logins_.isEmpty() && this.listener_ != null) {
            HandlerList.unregisterAll(this.listener_);
            this.listener_ = null;
        }
    }

    private void log_(String message) {
        this.plugin_.getLogger().log(this.loggingLevel_, message);
    }

    private static final class Login {

        private final BedrockPlatformAdapter adapter_;
        private final Player player_;
        private final Account account_;
        private final LoginMode mode_;
        private int tries_ = 0;

        Login(BedrockPlatformAdapter adapter, Player player, Account account, LoginMode mode) {
            this.adapter_ = adapter;
            this.player_ = player;
            this.account_ = account;
            this.mode_ = mode;
        }

        public void sendForm() {
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
            formBuilder.responseHandler(this.mode_.equals(LoginMode.NEW)
                    ? this::handleRegistrationResponse_
                    : this::handleAuthenticationResponse_);

            this.adapter_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
            this.adapter_.removeLogin_(this);
        }

        private void handleRegistrationResponse_(CustomForm form, String rawResponse) {
            CustomFormResponse response = form.parseResponse(rawResponse);
            if (!response.isCorrect()) {
                this.kick_();
                return;
            }

            String password = response.getInput(1);
            String confirmPassword = response.getInput(2);
            String error = null;

            try {
                boolean match = this.account_.setPassword(password, confirmPassword);
                if (!match) {
                    error = this.adapter_.plugin_.getRunical()
                            .translate(this.player_, "error.invalid_input.no_match")
                            .orDefault("")
                            .message();
                }
            } catch (PasswordTooShortException ex) {
                error = this.adapter_.plugin_.getRunical()
                        .translate(this.player_, "error.invalid_input.too_short")
                        .argument("min", ex.getMinLength())
                        .orDefault("")
                        .message();
            } catch (PasswordTooLongException ex) {
                error = this.adapter_.plugin_.getRunical()
                        .translate(this.player_, "error.invalid_input.too_long")
                        .argument("max", ex.getMaxLength())
                        .orDefault("")
                        .message();
            }

            if (error != null) {
                this.sendErrorMessage_(error);
                return;
            }

            try {
                this.adapter_.sessionService_.register(this.player_, Platform.BEDROCK, this.account_);
                this.adapter_.sessionService_.sendLoggedIn(this.player_);
            } catch (LogginException ex) {
                this.handleLoginException_(ex);
            }
        }

        private void handleAuthenticationResponse_(CustomForm form, String rawResponse) {
            CustomFormResponse response = form.parseResponse(rawResponse);
            if (!response.isCorrect()) {
                this.kick_();
                return;
            }

            this.login_(response.getInput(1));
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
            formBuilder.responseHandler((form, rawResponse) -> {
                ModalFormResponse response = form.parseResponse(rawResponse);
                if (!(response.isCorrect() && response.getResult())) {
                    this.kick_();
                    return;
                }
                this.sendForm();
            });

            this.adapter_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
        }

        private void kick_() {
            this.player_.kickPlayer(this.adapter_.plugin_.getRunical()
                    .translate(this.player_, "error.kicked." + (this.mode_.equals(LoginMode.NEW) ? "not_registered" : "not_logged"))
                    .orDefault("")
                    .message());
        }

        private void login_(String password) {
            try {
                this.adapter_.sessionService_.authenticateWithPassword(
                        this.player_,
                        Platform.BEDROCK,
                        this.account_,
                        password,
                        true
                );
                this.adapter_.sessionService_.sendLoggedIn(this.player_);
            } catch (LogginException ex) {
                this.handleLoginException_(ex);
            }
        }

        private void handleLoginException_(LogginException ex) {
            if (ex.getReason() != LogginException.Reason.INCORRECT_PASSWORD) {
                ex.getPublicMessage().ifPresentOrElse(
                        this::sendErrorMessage_,
                        () -> this.sendErrorMessage_(this.adapter_.plugin_.getRunical()
                                .translate(this.player_, "error.kicked.not_logged")
                                .orDefault("")
                                .message())
                );
                return;
            }

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
                if (login.player_.equals(event.getPlayer())) {
                    login.sendForm();
                }
            }
        }

        @EventHandler
        void onPlayerQuit(PlayerQuitEvent event) {
            Iterator<Login> iterator = this.adapter_.logins_.iterator();
            while (iterator.hasNext()) {
                Login login = iterator.next();
                if (login.player_.equals(event.getPlayer())) {
                    iterator.remove();
                }
            }
        }
    }
}
