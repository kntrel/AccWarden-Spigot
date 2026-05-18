package com.kntrel.mc.accwarden;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.command.AccountCommand;
import com.kntrel.mc.accwarden.io.database.DataBase;
import com.kntrel.mc.accwarden.listener.AccWardenListener;
import com.kntrel.mc.accwarden.listener.AccountLinker;
import com.kntrel.mc.accwarden.session.LoginManager;
import com.kntrel.mc.accwarden.session.SessionHolder;
import com.kntrel.mc.commvoker.spigot.Commvoker;
import com.kntrel.mc.runical.bukkit.Runical;
import com.kntrel.mc.runical.core.RunicalOptions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.charset.StandardCharsets;

public final class AccWarden extends JavaPlugin {

    //STATIC FIELDS
    private static AccWarden instance_ = null;

    //FIELDS
    public AccWardenConfig CONFIG = AccWardenConfig.DEFAULT;
    private final DataBase dataBase_ = new DataBase();
    private AccountRepository accountRepository_ = null;
    private SessionHolder sessionHolder_ = null;
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
        this.dataBase_.setFilePath(pluginPath + "/database.db");
        this.dataBase_.setUp();
        try {
            this.dataBase_.executeSQL(new String(this.getResource("dbSetup.sql").readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Accounts and sessions setup
        this.dataBase_.addEntity(Account.class, new Account.Parser(), "accounts");
        this.accountRepository_ = new AccountRepository(this, this.dataBase_);
        this.accountRepository_.setSizes(this.CONFIG.passwordMinSize(), this.CONFIG.passwordMaxSize());
        this.sessionHolder_ = new SessionHolder(this);
        this.sessionHolder_.setHoldTime(this.CONFIG.sessionHoldTime());
        this.sessionHolder_.setCrossPlatformSessions(this.CONFIG.crossPlatformSessions());
        LoginManager.setUp(this);

        //Floodgate setup
        if (Bukkit.getServer().getPluginManager().getPlugin("floodgate") != null) {
            this.bedrockOn_ = true;
        }

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
