package com.kntrel.mc.accwarden.platform.java;

import com.kntrel.mc.accwarden.form.FormTextTone;
import com.kntrel.mc.accwarden.platform.PlatformViews;
import com.kntrel.mc.accwarden.session.AuthenticationAction;
import com.kntrel.mc.accwarden.session.AuthenticationViewState;
import com.kntrel.mc.accwarden.session.RegistrationAction;
import com.kntrel.mc.accwarden.session.RegistrationViewState;
import com.kntrel.mc.accwarden.view.View;
import com.kntrel.mc.accwarden.view.ViewBuilder;
import com.kntrel.mc.accwarden.view.ViewAction;
import com.kntrel.mc.runical.bukkit.Translator;
import org.bukkit.entity.Player;

import java.util.Objects;

final class JavaViews implements PlatformViews {

    private static final String PASSWORD = "password";
    private static final String CONFIRM_PASSWORD = "confirm_password";
    private static final String PASSWORD_REQUIRED = "validation.password.required";
    private static final String CONFIRM_REQUIRED = "validation.confirm_password.required";
    private static final String PASSWORD_MATCH = "validation.password.match";

    private final Translator registrationTranslator_;
    private final Translator authenticationTranslator_;

    JavaViews(Translator registrationTranslator, Translator authenticationTranslator) {
        this.registrationTranslator_ = Objects.requireNonNull(registrationTranslator, "registrationTranslator");
        this.authenticationTranslator_ = Objects.requireNonNull(authenticationTranslator, "authenticationTranslator");
    }

    @Override
    public View<AuthenticationAction> authentication(Player player, AuthenticationViewState state) {
        var view = View.<AuthenticationAction>create();
        view.text(this.authTranslate_(
                player,
                state.isPlatformFirstTime() ? "disclosure.platform_first_time"
                                            : "disclosure.regular",
                state.isPlatformFirstTime() ? "Type the password you entered the first time you joined from Bedrock."
                                            : "Enter your account password."
        ));

        state.failure().ifPresent(failure -> view.text(FormTextTone.ERROR, this.authenticationFailure_(player, failure)));

        return view
            .ifFailed(PASSWORD_REQUIRED)
                .text(FormTextTone.ERROR, this.authTranslate_(player, "error.invalid_input.password_required", "Password is required."))
            .input(PASSWORD, this.authTranslate_(player, "input.password", "Password"))
            .action(ViewAction.<AuthenticationAction>returnValue()
                    .id("login")
                    .label(this.authTranslate_(player, "action.submit", "Log in"))
                    .value(result -> new AuthenticationAction.Password(result.get(PASSWORD)))
                    .validates(PASSWORD_REQUIRED, result -> !result.get(PASSWORD).isBlank()))
            .action(ViewAction.<AuthenticationAction>returnValue()
                    .id("quit")
                    .label(this.authTranslate_(player, "action.leave", "Leave server"))
                    .value(new AuthenticationAction.Quit()))
            .end();
    }

    @Override
    public View<RegistrationAction> registration(Player player, RegistrationViewState state) {
        ViewBuilder.Body<RegistrationAction> view = View.<RegistrationAction>create();

        view.text(this.regTranslate_(player, "disclosure", "This server requires a password to protect your account."));

        state.failure().ifPresent(failure -> view.text(FormTextTone.ERROR, this.registrationFailure_(player, failure)));

        return view
            .ifFailed(PASSWORD_REQUIRED)
                .text(FormTextTone.ERROR, this.regTranslate_(player, "error.invalid_input.password_required", "Password is required."))
            .input(PASSWORD, this.regTranslate_(player, "input.password", "Password"))
            .ifFailed(CONFIRM_REQUIRED)
                .text(FormTextTone.ERROR, this.regTranslate_(player, "error.invalid_input.confirm_password_required", "Missing confirmation!"))
            .ifFailed(PASSWORD_MATCH)
                .text(FormTextTone.ERROR, this.regTranslate_(player, "error.invalid_input.no_match", "Passwords do not match!"))
            .input(CONFIRM_PASSWORD, this.regTranslate_(player, "input.confirm_password", "Confirm password"))
            .action(ViewAction.<RegistrationAction>returnValue()
                    .id("register")
                    .label(this.regTranslate_(player, "action.submit", "Register"))
                    .value(result -> new RegistrationAction.Password(result.get(PASSWORD)))
                    .validates(PASSWORD_REQUIRED, r -> !r.get(PASSWORD).isBlank())
                    .validates(CONFIRM_REQUIRED, r -> !r.get(CONFIRM_PASSWORD).isBlank())
                    .validates(PASSWORD_MATCH, r ->    r.get(PASSWORD).isBlank()
                                                              || r.get(CONFIRM_PASSWORD).isBlank()
                                                              || r.get(PASSWORD).equals(r.get(CONFIRM_PASSWORD))))
            .action(ViewAction.<RegistrationAction>returnValue()
                    .id("quit")
                    .label(this.regTranslate_(player, "action.leave", "Leave server"))
                    .value(new RegistrationAction.Quit()))
            .end();
    }

    private String registrationFailure_(Player player, RegistrationViewState.Failure failure) {
        return switch (failure) {
            case RegistrationViewState.Failure.PasswordTooShort tooShort -> this.registrationTranslator_
                    .translate(player, "error.invalid_input.too_short")
                    .argument("min", tooShort.minLength())
                    .orDefault("Your password is too short.")
                    .message();
            case RegistrationViewState.Failure.PasswordTooLong tooLong -> this.registrationTranslator_
                    .translate(player, "error.invalid_input.too_long")
                    .argument("max", tooLong.maxLength())
                    .orDefault("Your password is too long.")
                    .message();
            case RegistrationViewState.Failure.PasswordRejected _ ->
                    this.regTranslate_(player, "error.password_rejected", "Password rejected.");
            case RegistrationViewState.Failure.Error error -> error.message();
        };
    }

    private String authenticationFailure_(Player player, AuthenticationViewState.Failure failure) {
        return switch (failure) {
            case AuthenticationViewState.Failure.IncorrectPassword _ ->
                    this.authTranslate_(player, "error.wrong_password.fine", "Wrong password!");
            case AuthenticationViewState.Failure.AccountLocked _ ->
                    this.authTranslate_(player, "error.account_locked", "Your account has been locked.");
            case AuthenticationViewState.Failure.AccountNotFound _ ->
                    this.authTranslate_(player, "error.account_not_found", "This account does not exist.");
            case AuthenticationViewState.Failure.Denied denied -> denied.message();
            case AuthenticationViewState.Failure.Error error -> error.message();
        };
    }

    private String translate_(Translator translator, Player player, String key, String fallback) {
        return translator.translate(player, key)
                .orDefault(fallback)
                .message();
    }
    private String authTranslate_(Player player, String key, String fallback) {
        return this.translate_(this.authenticationTranslator_, player, key, fallback);
    }
    private String regTranslate_(Player player, String key, String fallback) {
        return this.translate_(this.registrationTranslator_, player, key, fallback);
    }
}
