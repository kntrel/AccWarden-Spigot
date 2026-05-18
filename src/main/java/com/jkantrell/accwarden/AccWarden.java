package com.jkantrell.accwarden;

import com.jkantrell.accwarden.accoint.Account;
import com.jkantrell.accwarden.accoint.AccountRepository;
import com.jkantrell.accwarden.command.AccountCommand;
import com.jkantrell.accwarden.io.Config;
import com.jkantrell.accwarden.io.LangProvider;
import com.jkantrell.accwarden.io.database.DataBase;
import com.jkantrell.accwarden.listener.AccWardenListener;
import com.jkantrell.accwarden.listener.AccountLinker;
import com.jkantrell.accwarden.session.LoginManager;
import com.jkantrell.accwarden.session.SessionHolder;
import com.kntrel.mc.commvoker.spigot.Commvoker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public final class AccWarden extends JavaPlugin {

    //STATIC FIELDS
    private static AccWarden instance_ = null;

    //FIELDS
    public final Config CONFIG = new Config("");
    private final DataBase dataBase_ = new DataBase();
    private AccountRepository accountRepository_ = null;
    private SessionHolder sessionHolder_ = null;
    private LangProvider langProvider_ = null;
    private boolean bedrockOn_ = false;

    //PLUGIN LOGIC
    @Override
    public void onEnable() {
        //Configuration initialization
        String pluginPath = this.getDataFolder().getPath();
        this.CONFIG.setFilePath(pluginPath + "/config.yml");
        try {
            this.CONFIG.load();
        } catch (FileNotFoundException e) {
            this.saveResource("config.yml",true);
            this.onEnable();
            return;
        }

        //Main instance setup
        AccWarden.instance_ = this;

        //Language setup
        this.langProvider_ = new LangProvider(this, "lang");
        this.langProvider_.setDefaultLanguage(this.CONFIG.defaultLanguage);
        this.langProvider_.setLoggingLevel(Level.INFO);

        //Database setup
        this.dataBase_.setFilePath(pluginPath + "/database.db");
        this.dataBase_.setUp();
        try {
            this.dataBase_.executeSQL(new String(this.getResource("dbSetup.sql").readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Accounts and sessions setup
        this.dataBase_.addEntity(Account.class, new Account.Parser(), "accounts");
        this.accountRepository_ = new AccountRepository(this.dataBase_);
        this.accountRepository_.setSizes(this.CONFIG.passwordMinSize, this.CONFIG.passwordMaxSize);
        this.accountRepository_.setLangProvider(this.langProvider_);
        this.sessionHolder_ = new SessionHolder(this);
        this.sessionHolder_.setHoldTime(this.CONFIG.sessionHoldTime);
        this.sessionHolder_.setCrossPlatformSessions(this.CONFIG.crossPlatformSessions);
        LoginManager.setUp(this);

        //Floodgate setup
        if (Bukkit.getServer().getPluginManager().getPlugin("floodgate") != null) {
            this.bedrockOn_ = true;
        }

        //Listener setup
        this.getServer().getPluginManager().registerEvents(new AccWardenListener(this), this);
        if (this.isBedrockOn() && this.CONFIG.playerNameAutoLinking) {
            try {
                this.getServer().getPluginManager().registerEvents(new AccountLinker(this), this);
            } catch (IllegalArgumentException e) {
                this.getLogger().severe(
                    "playerNameAutoLink is enabled, but Floodgate's 'username-prefix' setting is not set to an empty string. Accounts won't be linked."
                    + "\nSet username prefixes to an empty string (\"\") and restart the server."
                );
            }
        }

        //Setting up commands
        Commvoker commvoker = new Commvoker(this);
        commvoker.register(new AccountCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    //GETTERS
    public DataBase getDataBase() {
        return this.dataBase_;
    }
    public AccountRepository getAccountRepository() {
        return this.accountRepository_;
    }
    public SessionHolder getSessionHolder() {
        return this.sessionHolder_;
    }
    public LangProvider getLangProvider() {
        return this.langProvider_;
    }
    public boolean isBedrockOn() {
        return this.bedrockOn_;
    }
    public static AccWarden getInstance() {
        return AccWarden.instance_;
    }
}
