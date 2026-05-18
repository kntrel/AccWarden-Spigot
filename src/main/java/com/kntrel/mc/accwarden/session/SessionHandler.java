package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.AccountService;
import org.bukkit.entity.Player;
import java.util.logging.Level;

public abstract class SessionHandler {

    //ASSETS
    protected enum LoginMode { NEW, NEW_IN_PLATFORM, EXISTING }

    //FIELDS
    protected final AccountService accountService;
    protected final SessionHolder sessionHolder;
    protected final AccWarden plugin;
    private Level loggingLevel_ = Level.FINEST;

    //CONSTRUCTORS
    public SessionHandler(AccountService accountService, SessionHolder sessionHolder, AccWarden plugin) {
        this.accountService = accountService;
        this.sessionHolder = sessionHolder;
        this.plugin = plugin;
    }

    //SETTERS
    public void setLoggingLevel(Level loggingLevel) {
        this.loggingLevel_ = loggingLevel;
    }

    //GETTERS
    public AccountService getAccountService() {
        return this.accountService;
    }
    public SessionHolder getSessionHolder() {
        return this.sessionHolder;
    }

    //METHODS
    protected void log(String message, Level level) {
        this.plugin.getLogger().log(level, message);
    }
    protected void log(String message) {
        this.log(message, this.loggingLevel_);
    }

    //ABSTRACT METHODS
    abstract public void handle(Player player);

}
