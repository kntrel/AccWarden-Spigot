package com.kntrel.mc.accwarden.command;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.form.FormTextTone;
import com.kntrel.mc.accwarden.view.View;
import com.kntrel.mc.accwarden.view.ViewAction;
import com.kntrel.mc.accwarden.view.ViewResult;
import com.kntrel.mc.runical.bukkit.Translator;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class AccountViews {

    private static final String PASSWORD = "password";
    private static final String CONFIRM_PASSWORD = "confirm_password";
    private static final String PASSWORD_REQUIRED = "validation.password.required";
    private static final String CONFIRM_REQUIRED = "validation.confirm_password.required";
    private static final String PASSWORD_MATCH = "validation.password.match";
    private static final String PASSWORD_TOO_SHORT = "validation.password.too_short";
    private static final String PASSWORD_TOO_LONG = "validation.password.too_long";
    private static final int INPUT_MAX_LENGTH = 256;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Translator translator_;
    private final int passwordMinLength_;
    private final int passwordMaxLength_;

    AccountViews(AccWarden plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.translator_ = plugin.getRunical().getChild("account_form");
        this.passwordMinLength_ = Math.max(plugin.getAccWardenConfig().passwordMinSize(), 1);
        this.passwordMaxLength_ = plugin.getAccWardenConfig().passwordMaxSize();
    }

    View<Action> overview(Player player, Account account) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(account, "account");

        String overview = this.translator_.translate(player, "overview")
                .argument("name", account.getName())
                .argument("last_login", this.format_(account.whenLastLogged()))
                .argument("joined", this.format_(account.whenJoined()))
                .argument("platforms", this.platforms_(account))
                .orDefault("&6&l✦ Account ✦&r\n&8━━━━━━━━━━━━━━━━&r\n&7Name &8› &f{name}&r\n&7Last login &8› &f{last_login}&r\n&7Joined &8› &f{joined}&r\n&7Platforms &8› &f[{platforms}]")
                .message();

        return View.<Action>create()
                .text(ChatColor.translateAlternateColorCodes('&', overview))
                .action(ViewAction.<Action>link()
                        .id("reset")
                        .label(this.translate_(player, "action.reset", "Reset account"))
                        .view(this.link_(() -> this.reset_(player, account))))
                .action(ViewAction.<Action>link()
                        .id("change_password")
                        .label(this.translate_(player, "action.change_password", "Change password"))
                        .view(this.link_(() -> this.changePassword_(player, account))))
                .end();
    }

    private View<Action> reset_(Player player, Account account) {
        return View.<Action>create()
                .text(FormTextTone.WARNING, this.translate_(
                        player,
                        "reset.disclaimer",
                        "Resetting deletes your account and leaves it unprotected. Anyone will be able to claim it by registering a new password."
                ))
                .action(ViewAction.<Action>returnValue()
                        .id("confirm_reset")
                        .label(this.translate_(player, "reset.action.confirm", "Reset account"))
                        .value(new Reset()))
                .action(ViewAction.<Action>link()
                        .id("cancel_reset")
                        .label(this.translate_(player, "reset.action.cancel", "Cancel"))
                        .view(this.link_(() -> this.overview(player, account))))
                .exitAction(ViewAction.<Action>link()
                        .id("close_reset")
                        .label(this.translate_(player, "reset.action.cancel", "Cancel"))
                        .view(this.link_(() -> this.overview(player, account))))
                .end();
    }

    private View<Action> changePassword_(Player player, Account account) {
        return View.<Action>create()
                .text(this.translate_(
                        player,
                        "change_password.disclosure",
                        "Enter and confirm the new password for your account."
                ))
                .ifFailed(PASSWORD_REQUIRED)
                    .text(FormTextTone.ERROR, this.translate_(player, "change_password.error.password_required", "Password is required."))
                .ifFailed(PASSWORD_TOO_SHORT)
                    .text(FormTextTone.ERROR, this.translator_.translate(player, "change_password.error.too_short")
                            .argument("min", this.passwordMinLength_)
                            .orDefault("Your password must contain at least {min} characters.")
                            .message())
                .ifFailed(PASSWORD_TOO_LONG)
                    .text(FormTextTone.ERROR, this.translator_.translate(player, "change_password.error.too_long")
                            .argument("max", this.passwordMaxLength_)
                            .orDefault("Your password cannot contain more than {max} characters.")
                            .message())
                .input(
                        PASSWORD,
                        this.translate_(player, "change_password.input.password", "New password"),
                        this.translate_(player, "change_password.input.password_placeholder", "Enter a new password"),
                        "",
                        true,
                        INPUT_MAX_LENGTH
                )
                .ifFailed(CONFIRM_REQUIRED)
                    .text(FormTextTone.ERROR, this.translate_(player, "change_password.error.confirm_password_required", "Password confirmation is required."))
                .ifFailed(PASSWORD_MATCH)
                    .text(FormTextTone.ERROR, this.translate_(player, "change_password.error.no_match", "Passwords do not match."))
                .input(
                        CONFIRM_PASSWORD,
                        this.translate_(player, "change_password.input.confirm_password", "Confirm password"),
                        this.translate_(player, "change_password.input.confirm_password_placeholder", "Repeat the new password"),
                        "",
                        true,
                        INPUT_MAX_LENGTH
                )
                .action(ViewAction.<Action>returnValue()
                        .id("change_password")
                        .label(this.translate_(player, "change_password.action.submit", "Change password"))
                        .value(result -> new ChangePassword(result.get(PASSWORD), result.get(CONFIRM_PASSWORD)))
                        .validates(PASSWORD_REQUIRED, result -> !result.get(PASSWORD).isBlank())
                        .validates(PASSWORD_TOO_SHORT, result -> result.get(PASSWORD).isBlank()
                                || result.get(PASSWORD).length() >= this.passwordMinLength_)
                        .validates(PASSWORD_TOO_LONG, result -> result.get(PASSWORD).length() <= this.passwordMaxLength_)
                        .validates(CONFIRM_REQUIRED, result -> !result.get(CONFIRM_PASSWORD).isBlank())
                        .validates(PASSWORD_MATCH, result -> result.get(PASSWORD).isBlank()
                                || result.get(CONFIRM_PASSWORD).isBlank()
                                || result.get(PASSWORD).equals(result.get(CONFIRM_PASSWORD))))
                .exitAction(ViewAction.<Action>link()
                        .id("close_change_password")
                        .label(this.translate_(player, "change_password.action.back", "Back"))
                        .view(this.link_(() -> this.overview(player, account))))
                .end();
    }

    private String platforms_(Account account) {
        return account.getJoinedFromPlatforms()
                .stream()
                .sorted()
                .map(platform -> switch (platform) {
                    case JAVA -> "Java";
                    case BEDROCK -> "Bedrock";
                })
                .collect(Collectors.joining(", "));
    }

    private String translate_(Player player, String key, String fallback) {
        return this.translator_.translate(player, key)
                .orDefault(fallback)
                .message();
    }

    private String format_(LocalDateTime date) {
        return DATE_FORMAT.format(date);
    }

    private Function<ViewResult, View<Action>> link_(Supplier<View<Action>> viewFactory) {
        return _ -> viewFactory.get();
    }

    sealed interface Action permits Reset, ChangePassword {}

    record Reset() implements Action {}

    record ChangePassword(String password, String confirmation) implements Action {}
}
