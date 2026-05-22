package com.kntrel.mc.accwarden.bedrock;

import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.platform.Authentication;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.ModalFormResponse;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

final class BedrockLoginForm {

    private enum Flow { REGISTRATION, PLATFORM_LINK }

    private final BedrockPlatformAdapter adapter_;
    private final Player player_;
    private final Account account_;
    private final Flow flow_;
    private final CompletableFuture<Authentication> authenticationFuture_;
    private final CompletableFuture<Account> registrationFuture_;
    private boolean formSent_ = false;
    private int tries_ = 0;

    static BedrockLoginForm authentication(
            BedrockPlatformAdapter adapter,
            Player player,
            Account account,
            CompletableFuture<Authentication> authenticationFuture
    ) {
        return new BedrockLoginForm(adapter, player, account, Flow.PLATFORM_LINK, authenticationFuture, null);
    }

    static BedrockLoginForm registration(
            BedrockPlatformAdapter adapter,
            Player player,
            Account account,
            CompletableFuture<Account> registrationFuture
    ) {
        return new BedrockLoginForm(adapter, player, account, Flow.REGISTRATION, null, registrationFuture);
    }

    private BedrockLoginForm(
            BedrockPlatformAdapter adapter,
            Player player,
            Account account,
            Flow flow,
            CompletableFuture<Authentication> authenticationFuture,
            CompletableFuture<Account> registrationFuture
    ) {
        this.adapter_ = adapter;
        this.player_ = player;
        this.account_ = account;
        this.flow_ = flow;
        this.authenticationFuture_ = authenticationFuture;
        this.registrationFuture_ = registrationFuture;
    }

    boolean isFor(Player player) {
        return this.player_.equals(player);
    }

    boolean hasFormBeenSent() {
        return this.formSent_;
    }

    void sendFormIfNeeded() {
        if (this.formSent_ || !this.player_.isOnline() || !this.adapter_.hasForm(this)) {
            return;
        }
        this.sendForm();
    }

    boolean sendForm() {
        String basePath = "login.bedrock." + switch (this.flow_) {
            case REGISTRATION -> "new";
            case PLATFORM_LINK -> "new_in_bedrock";
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
        if (this.flow_ == Flow.REGISTRATION) {
            formBuilder.input(this.adapter_.plugin_.getRunical()
                    .translate(this.player_, basePath + "fields.confirm_password")
                    .orDefault("")
                    .message(), placeholder);
        }
        formBuilder.closedOrInvalidResultHandler(this::reject_);
        formBuilder.validResultHandler(this.flow_ == Flow.REGISTRATION
                ? this::handleRegistrationResponse_
                : this::handleAuthenticationResponse_);

        boolean sent = this.adapter_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
        this.formSent_ = sent;
        if (!sent) {
            this.adapter_.log("Floodgate did not accept a form for Bedrock player '" + this.player_.getName() + "'. Retrying later.");
        }
        return sent;
    }

    void cancel() {
        if (this.authenticationFuture_ != null) {
            this.authenticationFuture_.complete(Authentication.rejected());
        }
        if (this.registrationFuture_ != null) {
            this.registrationFuture_.cancel(false);
        }
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
            this.adapter_.log("Floodgate did not accept the error form for Bedrock player '" + this.player_.getName() + "'. Retrying login form later.");
        }
    }

    private void completeAuthentication_(Authentication authentication) {
        if (this.authenticationFuture_ != null && this.authenticationFuture_.complete(authentication)) {
            this.removeLater_();
        }
    }

    private void reject_() {
        this.cancel();
        this.removeLater_();
    }

    private void removeLater_() {
        Bukkit.getScheduler().runTask(this.adapter_.plugin_, () -> this.adapter_.removeForm(this));
    }
}
