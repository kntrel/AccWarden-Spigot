package com.kntrel.mc.accwarden;

import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.command.AccountArgumentBindings;
import com.kntrel.mc.accwarden.command.AccountCommand;
import com.kntrel.mc.accwarden.gateway.AccWardenGate;
import com.kntrel.mc.accwarden.gateway.AccwarderGatekeeper;
import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLiteDatabase;
import com.kntrel.mc.accwarden.persistence.sqlite.SQLiteDatabaseInitializer;
import com.kntrel.mc.accwarden.platform.JavaOnlyPlatformRouter;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformRouter;
import com.kntrel.mc.accwarden.platform.bedrock.AutoNameLinker;
import com.kntrel.mc.accwarden.platform.bedrock.BedrockPlatform;
import com.kntrel.mc.accwarden.platform.bedrock.JavaAndBedrockPlatformRouter;
import com.kntrel.mc.accwarden.platform.java.JavaPlatform;
import com.kntrel.mc.accwarden.session.SessionHolder;
import com.kntrel.mc.accwarden.session.SessionService;
import com.kntrel.mc.commvoker.spigot.Commvoker;
import com.kntrel.mc.runical.bukkit.Runical;
import com.kntrel.mc.runical.core.RunicalOptions;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Duration;
import java.util.logging.Level;

public final class AccWarden extends JavaPlugin {

    private static final String TRANSLATIONS_DIRECTORY = "translations";
    private static final Duration GATEKEEPING_WINDOW = Duration.ofMinutes(1);
    private static final int GATEKEEPING_CAPACITY = 10_000;

    //FIELDS
    public AccWardenConfig CONFIG = AccWardenConfig.DEFAULT;
    private SQLiteDatabase sqliteDatabase_;
    private Runical runical_;
    private AccountService accountService_;
    private SessionHolder sessionHolder_;
    private SessionService sessionService_;
    private PlatformRouter platformRouter_;
    private AutoNameLinker autoNameLinker_;


    //PLUGIN LOGIC
    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.CONFIG = AccWardenConfig.load(this.getConfig());
        this.sqliteDatabase_ = SQLiteDatabaseInitializer.openDatabase(this, this.getDataFolder().toPath().resolve("accounts.db"));
        this.runical_ = new Runical(
                this,
                TRANSLATIONS_DIRECTORY,
                RunicalOptions.builder()
                        .defaultLocale(this.CONFIG.defaultLanguage())
                        .build()
        );

        this.accountService_ = AccountService.create(this);
        this.sessionHolder_ = new SessionHolder(this);
        this.sessionHolder_.setHoldTime(this.CONFIG.sessionHoldTime());

        this.sessionService_ = new SessionService(this, this.accountService_, this.sessionHolder_);
        this.platformRouter_ = this.createPlatformRouter_();
        this.sessionService_.setRouter(this.platformRouter_);

        Commvoker commvoker = new Commvoker(this);
        AccountArgumentBindings.register(commvoker, this);
        commvoker.register(new AccountCommand(this));

        this.getServer().getPluginManager().registerEvents(
                new AccWardenGate(this, this.createGatekeeper_()),
                this
        );
    }

    @Override
    public void onDisable() {
        if (this.runical_ != null) {
            this.runical_.close();
        }
        if (this.autoNameLinker_ != null) {
            this.autoNameLinker_.close();
            this.autoNameLinker_ = null;
        }
        if (this.sqliteDatabase_ != null) {
            try {
                this.sqliteDatabase_.close();
            } catch (SQLException ex) {
                this.getLogger().log(Level.WARNING, "Failed to close SQLite database.", ex);
            }
        }
    }

    public AccWardenConfig getAccWardenConfig() {
        return this.CONFIG;
    }

    public SQLiteDatabase getSQLiteDatabase() {
        return this.require_(this.sqliteDatabase_, "SQLite database has not been initialized.");
    }

    public Runical getRunical() {
        return this.require_(this.runical_, "Runical has not been initialized.");
    }

    public AccountService getAccountService() {
        return this.require_(this.accountService_, "Account service has not been initialized.");
    }

    public SessionService getSessionService() {
        return this.require_(this.sessionService_, "Session service has not been initialized.");
    }

    public PlatformRouter getPlatformRouter() {
        return this.require_(this.platformRouter_, "Platform router has not been initialized.");
    }

    private PlatformRouter createPlatformRouter_() {
        Platform javaPlatform = new JavaPlatform(this);

        //DANGER ZONE ----------------------------------------
        //Entry point for Geyser & Floodgate API's, only if Floodgate is present
        if (   this.getServer().getPluginManager().isPluginEnabled("floodgate")
            && this.getServer().getPluginManager().isPluginEnabled("Geyser-Spigot")
        ) try {
            Platform bedrockPlatform = new BedrockPlatform(this);
            if (this.CONFIG.playerNameAutoLinking()) {
                this.startAutoNameLinker_();
            }
            this.getLogger().info("The server uses Geyser and Floodgate. Switching to Bedrock compatibility mode.");
            return new JavaAndBedrockPlatformRouter(javaPlatform, bedrockPlatform);
        } catch (LinkageError | RuntimeException ex) {
            this.getLogger().log(Level.SEVERE, "Failed to initialize Bedrock platform support. Bedrock enhancements and smart authentication are disabled.", ex);
        }
        //----------------------------------------------------

        return new JavaOnlyPlatformRouter(javaPlatform);
    }

    private AccwarderGatekeeper createGatekeeper_() {
        return new AccwarderGatekeeper(
                new AccountPolicy(GATEKEEPING_WINDOW, GATEKEEPING_CAPACITY, 5),
                new ClientPolicy(GATEKEEPING_WINDOW, GATEKEEPING_CAPACITY, 10, 1_000),
                new LoginPolicy(GATEKEEPING_WINDOW, GATEKEEPING_CAPACITY, 5, 5)
        );
    }

    private void startAutoNameLinker_() {
        try {
            this.autoNameLinker_ = new AutoNameLinker(this, this.runical_.getChild("error").getChild("kicked"));
        } catch (IllegalStateException ex) {
            this.getLogger().log(
                    Level.SEVERE,
                    "Bedrock AutoNameLinking is enabled in AccWarden, but Floodgate does not meet the requirements. AutoNameLinking will not be subscribed.",
                    ex
            );
        }
    }

    private <T> T require_(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
