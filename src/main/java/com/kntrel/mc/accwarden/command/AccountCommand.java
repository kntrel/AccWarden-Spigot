package com.kntrel.mc.accwarden.command;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.io.LangProvider;
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
    private final AccountRepository accountRepository_;
    private final LangProvider langProvider_;

    //CONSTRUCTOR
    public AccountCommand(AccWarden accWardenInstance) {
        this.plugin_ = accWardenInstance;
        this.accountRepository_ = this.plugin_.getAccountRepository();
        this.langProvider_ = this.plugin_.getLangProvider();
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
        return this.langProvider_.getEntry(player,"command.reset.success", player.getName());
    }

    @Command("changePassword {password} {confirmPassword}")
    public String changePassword(
            FailTrigger failTrigger,
            @Sender Player player,
            @Word String password,
            @Word String confirmPassword
    ) throws FailedCommandException {
        Account acc = this.getAccount_(failTrigger, player);
        boolean match = false;
        try {
            match = acc.setPassword(password, confirmPassword);
        } catch (PasswordTooLongException ex) {
            failTrigger.fail(this.langProvider_.getEntry(player,"error.invalid_input.too_long", ex.getMaxLength()));
        } catch (PasswordTooShortException ex) {
            failTrigger.fail(this.langProvider_.getEntry(player,"error.invalid_input.too_short", ex.getMinLength()));
        }

        if (!match) {
            failTrigger.fail(this.langProvider_.getEntry(player,"error.invalid_input.no_match"));
        }

        return this.langProvider_.getEntry(player,"command.changePassword.success");
    }


    //PRIVATE METHODS
    private Account getAccount_(FailTrigger failTrigger, Player player) throws FailedCommandException {
        if (!this.accountRepository_.exists(player)) {
            failTrigger.fail(this.langProvider_.getEntry(player,"command.unexisting_account", player.getName()));
        }
        return this.accountRepository_.retrieve(player);
    }
}
