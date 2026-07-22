package com.kntrel.mc.accwarden.command;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.PasswordConfirmationFailedException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.command.annotation.LoggedIn;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.commvoker.bukkit.provided.annotation.Sender;
import com.kntrel.mc.commvoker.bukkit.requirement.RequiresPermission;
import com.kntrel.mc.commvoker.command.Command;
import com.kntrel.mc.commvoker.error.FailTrigger;
import com.kntrel.mc.commvoker.exception.FailedCommandException;
import com.kntrel.mc.commvoker.provided.annotations.Word;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

@Command("account")
public class AccountCommand {

    //FIELDS
    private final AccWarden plugin_;
    private final AccountService accountService_;
    private final AccountViews accountViews_;

    //CONSTRUCTOR
    public AccountCommand(AccWarden accWardenInstance) {
        this.plugin_ = accWardenInstance;
        this.accountService_ = this.plugin_.getAccountService();
        this.accountViews_ = new AccountViews(this.plugin_);
    }

    //COMMANDS
    @Command(extend = true)
    public void account(@Sender Player player, @LoggedIn Account account) {
        Platform platform = this.plugin_.getPlatformRouter().getPlatform(player);
        this.accountViews_
                .overview(player, account)
                .present(player, platform)
                .whenComplete((action, throwable) -> this.runSync_(
                        () -> this.completeAccountView_(player, account, action, throwable)
                ));
    }

    @Command("reset")
    public void reset(@LoggedIn Account account) {
        account.delete();
    }

    @Command("reset {player}")
    @RequiresPermission("AccWarden.command.reset")
    public String resetOf(FailTrigger failTrigger, CommandSender sender, Player player) throws FailedCommandException {
        Account account = this.getAccount_(failTrigger, player);
        account.delete();
        return this.plugin_.getRunical()
                .translate(player, "command.reset.success")
                .argument("player", player.getName())
                .orDefault("")
                .message();
    }

    @Command("changePassword {password} {confirmPassword}")
    public String changePassword(
            FailTrigger failTrigger,
            @Sender Player player,
            @LoggedIn Account account,
            @Word String password,
            @Word String confirmPassword
    ) throws FailedCommandException {
        try {
            account.setPassword(password, confirmPassword);
        } catch (PasswordTooLongException ex) {
            failTrigger.fail(this.plugin_.getRunical()
                    .translate(player, "error.invalid_input.too_long")
                    .argument("max", ex.getMaxLength())
                    .orDefault("")
                    .message());
        } catch (PasswordTooShortException ex) {
            failTrigger.fail(this.plugin_.getRunical()
                    .translate(player, "error.invalid_input.too_short")
                    .argument("min", ex.getMinLength())
                    .orDefault("")
                    .message());
        } catch (PasswordConfirmationFailedException ex) {
            failTrigger.fail(this.plugin_.getRunical()
                    .translate(player, "error.invalid_input.no_match")
                    .orDefault("")
                    .message());
        }

        account.save();

        return this.plugin_.getRunical()
                .translate(player, "command.changePassword.success")
                .orDefault("")
                .message();
    }


    //PRIVATE METHODS
    private void completeAccountView_(
            Player player,
            Account account,
            AccountViews.Action action,
            Throwable throwable
    ) {
        Throwable failure = this.unwrap_(throwable);
        if (failure instanceof CancellationException) {
            return;
        }
        if (failure != null) {
            this.plugin_.getLogger().log(Level.WARNING, "Failed to complete account form for " + player.getName() + ".", failure);
            return;
        }

        switch (action) {
            case AccountViews.Reset _ -> account.delete();
            case AccountViews.ChangePassword change -> this.changePasswordFromView_(player, account, change);
        }
    }

    private void changePasswordFromView_(Player player, Account account, AccountViews.ChangePassword change) {
        try {
            account.setPassword(change.password(), change.confirmation());
            account.save();
            player.sendMessage(ChatColor.GREEN + this.plugin_.getRunical()
                    .translate(player, "command.changePassword.success")
                    .orDefault("Your password has been changed.")
                    .message());
        } catch (PasswordTooLongException exception) {
            player.sendMessage(ChatColor.RED + this.plugin_.getRunical()
                    .translate(player, "error.invalid_input.too_long")
                    .argument("max", exception.getMaxLength())
                    .orDefault("Your password is too long.")
                    .message());
        } catch (PasswordTooShortException exception) {
            player.sendMessage(ChatColor.RED + this.plugin_.getRunical()
                    .translate(player, "error.invalid_input.too_short")
                    .argument("min", exception.getMinLength())
                    .orDefault("Your password is too short.")
                    .message());
        } catch (PasswordConfirmationFailedException exception) {
            player.sendMessage(ChatColor.RED + this.plugin_.getRunical()
                    .translate(player, "error.invalid_input.no_match")
                    .orDefault("Passwords do not match.")
                    .message());
        }
    }

    private Account getAccount_(FailTrigger failTrigger, Player player) throws FailedCommandException {
        Platform platform = this.plugin_.getPlatformRouter().getPlatform(player);
        if (!this.accountService_.exists(player, platform)) {
            this.failMissingAccount_(failTrigger, player);
        }
        return this.accountService_.get(player, platform).orElseThrow();
    }

    private void failMissingAccount_(FailTrigger failTrigger, Player player) throws FailedCommandException {
        failTrigger.fail(this.plugin_.getRunical()
                .translate(player, "command.unexisting_account")
                .argument("player", player.getName())
                .orDefault("")
                .message());
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }

    private Throwable unwrap_(Throwable throwable) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
