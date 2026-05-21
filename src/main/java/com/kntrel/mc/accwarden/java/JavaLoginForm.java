package com.kntrel.mc.accwarden.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.platform.Authentication;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.dialog.Dialog;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.action.ActionButton;
import net.md_5.bungee.api.dialog.action.CustomClickAction;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;
import net.md_5.bungee.api.dialog.input.DialogInput;
import net.md_5.bungee.api.dialog.input.TextInput;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class JavaLoginForm {

    private enum Flow {
        REGISTRATION(
                "new",
                "Register",
                "Register",
                "This server requires a password to protect your account."
        ),
        AUTHENTICATION(
                "existing",
                "Log in",
                "Log in",
                "Enter your account password."
        );

        private final String langKey_;
        private final String defaultTitle_;
        private final String defaultButton_;
        private final String defaultDisclosure_;

        Flow(String langKey, String defaultTitle, String defaultButton, String defaultDisclosure) {
            this.langKey_ = langKey;
            this.defaultTitle_ = defaultTitle;
            this.defaultButton_ = defaultButton;
            this.defaultDisclosure_ = defaultDisclosure;
        }

        String langPath() {
            return "login.java." + this.langKey_ + ".";
        }

        boolean usesConfirmPassword() {
            return this == REGISTRATION;
        }
    }

    private record Submission(String password, String confirmPassword) {}

    private static final String PASSWORD_INPUT = "password";
    private static final String CONFIRM_PASSWORD_INPUT = "confirm_password";
    private static final int DIALOG_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;

    private final AccWarden plugin_;
    private final Player player_;
    private final Account account_;
    private final Flow flow_;
    private final NamespacedKey submitActionKey_;
    private final NamespacedKey leaveActionKey_;
    private final CompletableFuture<Authentication> authenticationFuture_;
    private final CompletableFuture<Account> registrationFuture_;
    private final Consumer<JavaLoginForm> completionHandler_;
    private boolean active_ = true;
    private int tries_ = 0;

    static JavaLoginForm authentication(
            AccWarden plugin,
            Player player,
            Account account,
            NamespacedKey submitActionKey,
            NamespacedKey leaveActionKey,
            CompletableFuture<Authentication> authenticationFuture,
            Consumer<JavaLoginForm> completionHandler
    ) {
        return new JavaLoginForm(
                plugin,
                player,
                account,
                Flow.AUTHENTICATION,
                submitActionKey,
                leaveActionKey,
                authenticationFuture,
                null,
                completionHandler
        );
    }

    static JavaLoginForm registration(
            AccWarden plugin,
            Player player,
            Account account,
            NamespacedKey submitActionKey,
            NamespacedKey leaveActionKey,
            CompletableFuture<Account> registrationFuture,
            Consumer<JavaLoginForm> completionHandler
    ) {
        return new JavaLoginForm(
                plugin,
                player,
                account,
                Flow.REGISTRATION,
                submitActionKey,
                leaveActionKey,
                null,
                registrationFuture,
                completionHandler
        );
    }

    private JavaLoginForm(
            AccWarden plugin,
            Player player,
            Account account,
            Flow flow,
            NamespacedKey submitActionKey,
            NamespacedKey leaveActionKey,
            CompletableFuture<Authentication> authenticationFuture,
            CompletableFuture<Account> registrationFuture,
            Consumer<JavaLoginForm> completionHandler
    ) {
        this.plugin_ = plugin;
        this.player_ = player;
        this.account_ = account;
        this.flow_ = flow;
        this.submitActionKey_ = submitActionKey;
        this.leaveActionKey_ = leaveActionKey;
        this.authenticationFuture_ = authenticationFuture;
        this.registrationFuture_ = registrationFuture;
        this.completionHandler_ = completionHandler;
    }

    Player getPlayer() {
        return this.player_;
    }

    boolean isFor(Player player) {
        return this.player_.equals(player);
    }

    void show() {
        this.show_(null);
    }

    void submit(JsonElement data) {
        Submission submission = this.readSubmission_(data);
        if (this.flow_ == Flow.REGISTRATION) {
            this.register_(submission);
            return;
        }
        this.authenticate_(submission.password());
    }

    void leaveServer() {
        this.cancel();
        this.clearDialog_();
        this.completionHandler_.accept(this);
    }

    void cancel() {
        this.active_ = false;
        if (this.authenticationFuture_ != null) {
            this.authenticationFuture_.complete(Authentication.rejected());
        }
        if (this.registrationFuture_ != null) {
            this.registrationFuture_.cancel(false);
        }
    }

    private void register_(Submission submission) {
        try {
            boolean match = this.account_.setPassword(submission.password(), submission.confirmPassword());
            if (!match) {
                this.showError_("error.invalid_input.no_match");
                return;
            }
        } catch (PasswordTooLongException ex) {
            this.show_(this.plugin_.getRunical()
                    .translate(this.player_, "error.invalid_input.too_long")
                    .argument("max", ex.getMaxLength())
                    .orDefault("")
                    .message());
            return;
        } catch (PasswordTooShortException ex) {
            this.show_(this.plugin_.getRunical()
                    .translate(this.player_, "error.invalid_input.too_short")
                    .argument("min", ex.getMinLength())
                    .orDefault("")
                    .message());
            return;
        }

        if (this.registrationFuture_ != null && this.registrationFuture_.complete(this.account_)) {
            this.finish_();
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
        AccWardenConfig config = this.plugin_.CONFIG;
        String errorKey = "error.incorrect_password.fine";

        if (this.tries_ >= config.failLoginOdd()) {
            int warnAt = config.failLoginOdd() + config.failLoginWarn();
            if (this.tries_ >= warnAt && config.failLoginAccountLock()) {
                int lockAt = warnAt + config.failLoginLock();
                if (this.tries_ >= lockAt && config.failLoginLock() > 0) {
                    this.account_.lock();
                } else {
                    errorKey = "error.incorrect_password.warn";
                }
            } else {
                errorKey = "error.incorrect_password.odd";
            }
        }

        this.showError_(errorKey);
    }

    private void completeAuthentication_(Authentication authentication) {
        if (this.authenticationFuture_ != null && this.authenticationFuture_.complete(authentication)) {
            this.finish_();
        }
    }

    private void showError_(String langKey) {
        this.show_(this.plugin_.getRunical()
                .translate(this.player_, langKey)
                .orDefault("")
                .message());
    }

    private void finish_() {
        this.active_ = false;
        this.clearDialog_();
        this.completionHandler_.accept(this);
    }

    private void clearDialog_() {
        if (this.player_.isOnline()) {
            this.runSync_(this.player_::clearDialog);
        }
    }

    private void show_(String errorMessage) {
        if (!this.active_ || !this.player_.isOnline()) {
            return;
        }
        this.runSync_(() -> {
            if (this.active_ && this.player_.isOnline()) {
                this.player_.showDialog(this.createDialog_(errorMessage));
            }
        });
    }

    private Dialog createDialog_(String errorMessage) {
        DialogBase base = new DialogBase(this.component_(this.translate_("title", this.flow_.defaultTitle_)));
        base.body(this.body_(errorMessage))
                .inputs(this.inputs_())
                .canCloseWithEscape(false)
                .pause(false)
                .afterAction(DialogBase.AfterAction.NONE);

        return new MultiActionDialog(
                base,
                List.of(this.submitButton_(), this.leaveServerButton_()),
                1,
                null
        );
    }

    private List<DialogBody> body_(String errorMessage) {
        List<DialogBody> body = new ArrayList<>();
        if (errorMessage != null && !errorMessage.isBlank()) {
            body.add(new PlainMessageBody(this.component_(errorMessage, ChatColor.RED), DIALOG_WIDTH));
        }

        String disclosure = this.translate_("disclosure", this.flow_.defaultDisclosure_);
        if (!disclosure.isBlank()) {
            body.add(new PlainMessageBody(this.component_(disclosure), DIALOG_WIDTH));
        }

        return body;
    }

    private List<DialogInput> inputs_() {
        int maxLength = this.plugin_.CONFIG.passwordMaxSize();
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(this.passwordInput_(PASSWORD_INPUT, this.translate_("fields.password", "Password"), maxLength));

        if (this.flow_.usesConfirmPassword()) {
            inputs.add(this.passwordInput_(
                    CONFIRM_PASSWORD_INPUT,
                    this.translate_("fields.confirm_password", "Confirm password"),
                    maxLength
            ));
        }

        return inputs;
    }

    private TextInput passwordInput_(String key, String label, int maxLength) {
        return new TextInput(key, DIALOG_WIDTH, this.component_(label), true, "", maxLength);
    }

    private ActionButton submitButton_() {
        return new ActionButton(
                this.component_(this.translate_("button", this.flow_.defaultButton_)),
                null,
                BUTTON_WIDTH,
                new CustomClickAction(this.submitActionKey_.toString())
        );
    }

    private ActionButton leaveServerButton_() {
        return new ActionButton(
                this.component_(this.translate_("login.java.dialog.buttonLeaveServer", "Leave server")),
                null,
                BUTTON_WIDTH,
                new CustomClickAction(this.leaveActionKey_.toString())
        );
    }

    private Submission readSubmission_(JsonElement data) {
        return new Submission(
                this.readInput_(data, PASSWORD_INPUT),
                this.readInput_(data, CONFIRM_PASSWORD_INPUT)
        );
    }

    private String readInput_(JsonElement data, String key) {
        if (data == null || !data.isJsonObject()) {
            return "";
        }

        JsonObject object = data.getAsJsonObject();
        Optional<String> directValue = this.readString_(object, key);
        if (directValue.isPresent()) {
            return directValue.get();
        }

        for (String nestedKey : List.of("inputs", "values", "data")) {
            Optional<String> nestedValue = this.readObject_(object, nestedKey)
                    .flatMap(nested -> this.readString_(nested, key));
            if (nestedValue.isPresent()) {
                return nestedValue.get();
            }
        }

        return "";
    }

    private Optional<JsonObject> readObject_(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) {
            return Optional.empty();
        }
        return Optional.of(value.getAsJsonObject());
    }

    private Optional<String> readString_(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(value.getAsString());
    }

    private BaseComponent component_(String text) {
        return new TextComponent(text);
    }

    private BaseComponent component_(String text, ChatColor color) {
        TextComponent component = new TextComponent(text);
        component.setColor(color);
        return component;
    }

    private String translate_(String key, String fallback) {
        String path = this.isAbsoluteTranslationPath_(key) ? key : this.flow_.langPath() + key;
        String message = this.plugin_.getRunical()
                .translate(this.player_, path)
                .orDefault("")
                .message();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    private boolean isAbsoluteTranslationPath_(String key) {
        return key.startsWith("command.")
                || key.startsWith("error.")
                || key.startsWith("info.")
                || key.startsWith("login.");
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }
}
