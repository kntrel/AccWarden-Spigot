package com.kntrel.mc.accwarden.command;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.exception.PasswordConfirmationFailedException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.commvoker.bukkit.provided.annotation.Sender;
import com.kntrel.mc.commvoker.bukkit.requirement.RequiresPermission;
import com.kntrel.mc.commvoker.command.Command;
import com.kntrel.mc.commvoker.error.FailTrigger;
import com.kntrel.mc.commvoker.exception.FailedCommandException;
import com.kntrel.mc.commvoker.provided.annotations.Word;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("account")
public class AccountCommand {

    //FIELDS
    private final AccWarden plugin_;
    private final AccountService accountService_;

    //CONSTRUCTOR
    public AccountCommand(AccWarden accWardenInstance) {
        this.plugin_ = accWardenInstance;
        this.accountService_ = this.plugin_.getAccountService();
    }

    //COMMANDS
    @Command("reset")
    public void reset(FailTrigger failTrigger, @Sender Player player) throws FailedCommandException {
        Account acc = this.getAccount_(failTrigger, player);
        acc.delete();
    }

    @Command("reset {player}")
    @RequiresPermission("AccWarden.command.reset")
    public String resetOf(FailTrigger failTrigger, CommandSender sender, Player player) throws FailedCommandException {
        this.reset(failTrigger, player);
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
            @Word String password,
            @Word String confirmPassword
    ) throws FailedCommandException {
        Account acc = this.getAccount_(failTrigger, player);
        try {
            acc.setPassword(password, confirmPassword);
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

        acc.save();

        return this.plugin_.getRunical()
                .translate(player, "command.changePassword.success")
                .orDefault("")
                .message();
    }


    //PRIVATE METHODS
    private Account getAccount_(FailTrigger failTrigger, Player player) throws FailedCommandException {
        Platform platform = this.plugin_.getPlatformRouter().getPlatform(player);
        if (!this.accountService_.exists(player, platform)) {
            failTrigger.fail(this.plugin_.getRunical()
                    .translate(player, "command.unexisting_account")
                    .argument("player", player.getName())
                    .orDefault("")
                    .message());
        }
        return this.accountService_.get(player, platform).orElseThrow();
    }
}
