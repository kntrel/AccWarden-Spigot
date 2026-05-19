package com.kntrel.mc.accwarden;

import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.command.AccountCommand;
import com.kntrel.mc.accwarden.listener.AccWardenListener;
import com.kntrel.mc.accwarden.listener.AccountLinker;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLiteDatabase;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLiteDatabaseInitializer;
import com.kntrel.mc.accwarden.platform.BedrockPlatformAdapter;
import com.kntrel.mc.accwarden.platform.FloodgatePlatformRouter;
import com.kntrel.mc.accwarden.platform.JavaOnlyPlatformRouter;
import com.kntrel.mc.accwarden.platform.JavaPlatformAdapter;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import com.kntrel.mc.accwarden.session.SessionHolder;
import com.kntrel.mc.accwarden.session.SessionService;
import com.kntrel.mc.commvoker.spigot.Commvoker;
import com.kntrel.mc.runical.bukkit.Runical;
import com.kntrel.mc.runical.core.RunicalOptions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.logging.Level;

public final class AccWarden extends JavaPlugin {

    //STATIC FIELDS
    private static AccWarden instance_ = null;

    //FIELDS
    public AccWardenConfig CONFIG = AccWardenConfig.DEFAULT;
    private SQLiteDatabase sqliteDatabase_ = null;
    private AccountService accountService_ = null;
    private SessionHolder sessionHolder_ = null;
    private SessionService sessionService_ = null;
    private PlatformRouter platformRouter_ = null;
    private Runical runical_ = null;
    private boolean bedrockOn_ = false;

    //PLUGIN LOGIC
    @Override
    public void onEnable() {
        //Configuration initialization
        String pluginPath = this.getDataFolder().getPath();
        File configFile = new File(pluginPath, "config.yml");
        if (!configFile.exists()) {
            this.saveResource("config.yml", false);
        }
        this.CONFIG = AccWardenConfig.load(YamlConfiguration.loadConfiguration(configFile));

        //Main instance setup
        AccWarden.instance_ = this;

        //Language setup
        this.runical_ = new Runical(
                this,
                "lang",
                RunicalOptions.builder().defaultLocale(this.CONFIG.defaultLanguage()).build()
        );

        //Database setup
        this.sqliteDatabase_ = SQLiteDatabaseInitializer.openDatabase(this, Path.of(pluginPath, "database.db"));

        //Accounts and sessions setup
        this.accountService_ = AccountService.create(this);
        this.sessionHolder_ = new SessionHolder(this);
        this.sessionHolder_.setHoldTime(this.CONFIG.sessionHoldTime());
        this.sessionHolder_.setCrossPlatformSessions(this.CONFIG.crossPlatformSessions());
        this.sessionHolder_.setLoggingLevel(Level.INFO);
        this.sessionService_ = new SessionService(this, this.accountService_, this.sessionHolder_);

        //Floodgate setup
        JavaPlatformAdapter javaAdapter = new JavaPlatformAdapter(this.sessionService_, this);
        javaAdapter.setLoggingLevel(Level.INFO);
        if (Bukkit.getServer().getPluginManager().getPlugin("floodgate") != null) {
            this.bedrockOn_ = true;
            BedrockPlatformAdapter bedrockAdapter = new BedrockPlatformAdapter(this.sessionService_, this);
            bedrockAdapter.setLoggingLevel(Level.INFO);
            this.platformRouter_ = new FloodgatePlatformRouter(javaAdapter, bedrockAdapter);
        } else {
            this.platformRouter_ = new JavaOnlyPlatformRouter(javaAdapter);
        }
        this.sessionService_.setRouter(this.platformRouter_);

        //Listener setup
        this.getServer().getPluginManager().registerEvents(new AccWardenListener(this), this);
        if (this.isBedrockOn() && this.CONFIG.playerNameAutoLinking()) {
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
        if (this.runical_ != null) {
            this.runical_.close();
            this.runical_ = null;
        }
        if (this.sqliteDatabase_ != null) {
            try {
                this.sqliteDatabase_.close();
            } catch (SQLException e) {
                this.getLogger().warning("Failed to close SQLite database: " + e.getMessage());
            }
            this.sqliteDatabase_ = null;
        }
    }

    //GETTERS
    public SQLiteDatabase getSQLiteDatabase() {
        return this.sqliteDatabase_;
    }
    public AccountRepository getAccountRepository() {
        return this.accountService_;
    }
    public AccountService getAccountService() {
        return this.accountService_;
    }
    public SessionHolder getSessionHolder() {
        return this.sessionHolder_;
    }
    public SessionService getSessionService() {
        return this.sessionService_;
    }
    public PlatformRouter getPlatformRouter() {
        return this.platformRouter_;
    }
    public Runical getRunical() {
        return this.runical_;
    }
    public boolean isBedrockOn() {
        return this.bedrockOn_;
    }
    public static AccWarden getInstance() {
        return AccWarden.instance_;
    }
}
