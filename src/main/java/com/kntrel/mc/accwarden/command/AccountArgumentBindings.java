package com.kntrel.mc.accwarden.command;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.command.annotation.LoggedIn;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.commvoker.argument.binder.ArgumentBinder;
import com.kntrel.mc.commvoker.argument.binding.ArgumentBinding;
import com.kntrel.mc.commvoker.argument.context.ExecutionContext;
import com.kntrel.mc.commvoker.argument.context.ParameterContext;
import com.kntrel.mc.commvoker.exception.CommandMethodRunException;
import com.kntrel.mc.commvoker.spigot.Commvoker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.Optional;

public final class AccountArgumentBindings {

    private AccountArgumentBindings() {}

    public static void register(Commvoker commvoker, AccWarden plugin) {
        Objects.requireNonNull(commvoker, "commvoker");
        Objects.requireNonNull(plugin, "plugin");
        commvoker.registerExceptionHandler(
                AccountResolutionException.class,
                exception -> new CommandMethodRunException(exception.context(), exception.getMessage(), exception)
        );
        commvoker.registerArgument(account(plugin));
    }

    private static ArgumentBinding<CommandSender, ParameterContext, Account> account(AccWarden plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return ArgumentBinder.<CommandSender, Account>implicit(context -> resolve_(plugin, context))
                .toClass(Account.class)
                .requires(Player.class::isInstance)
                .bind();
    }

    private static Account resolve_(AccWarden plugin, ExecutionContext<? extends CommandSender> context) {
        if (!(context.source() instanceof Player player)) {
            throw new AccountResolutionException(context, "Only players can have accounts.");
        }

        Optional<Account> account;
        String failureMessage;
        if (context.isAnnotationPresent(LoggedIn.class)) {
            account = plugin.getSessionService().getLoggedInAccount(player);
            failureMessage = plugin.getRunical()
                    .translate(player, "error.not_allowed.issue_command")
                    .orDefault("")
                    .message();
        } else {
            Platform platform = plugin.getPlatformRouter().getPlatform(player);
            account = plugin.getAccountService().get(player, platform);
            failureMessage = plugin.getRunical()
                    .translate(player, "command.unexisting_account")
                    .argument("player", player.getName())
                    .orDefault("")
                    .message();
        }

        return account.orElseThrow(() -> new AccountResolutionException(context, failureMessage));
    }

    private static final class AccountResolutionException extends RuntimeException {

        private final ExecutionContext<?> context_;

        private AccountResolutionException(ExecutionContext<?> context, String message) {
            super(message);
            this.context_ = context;
        }

        private ExecutionContext<?> context() {
            return this.context_;
        }
    }
}
