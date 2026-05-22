package com.kntrel.mc.accwarden.bedrock;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.platform.Authentication;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class BedrockPlatformAdapter implements PlatformAdapter {

    final AccountService accountService_;
    final AccWarden plugin_;
    final FloodgateApi floodgateApi_ = FloodgateApi.getInstance();

    private final LinkedList<BedrockLoginForm> activeForms_ = new LinkedList<>();
    private Listener listener_;
    private Level loggingLevel_ = Level.FINEST;

    public BedrockPlatformAdapter(AccountService accountService, AccWarden plugin) {
        this.accountService_ = accountService;
        this.plugin_ = plugin;
    }

    @Override
    public Platform getPlatform() {
        return Platform.BEDROCK;
    }

    @Override
    public CompletableFuture<Authentication> authenticate(Player player) {
        CompletableFuture<Authentication> future = new CompletableFuture<>();
        if (!this.floodgateApi_.isFloodgatePlayer(player.getUniqueId())) {
            future.complete(Authentication.rejected());
            return future;
        }

        Optional<Account> account = this.accountService_.get(player, Platform.BEDROCK);
        if (account.isPresent()) {
            this.log_("Bedrock player with UUID '" + player.getUniqueId() + "' already has Bedrock access.");
            future.complete(Authentication.passed(account.get()));
            return future;
        }

        Optional<Account> linkedJavaAccount = this.findLinkedJavaAccount_(player);
        if (linkedJavaAccount.isEmpty()) {
            future.complete(Authentication.unexistent());
            return future;
        }

        this.addForm_(BedrockLoginForm.authentication(this, player, linkedJavaAccount.get(), future));
        return future;
    }

    @Override
    public CompletableFuture<Account> register(Player player) {
        CompletableFuture<Account> future = new CompletableFuture<>();
        if (!this.floodgateApi_.isFloodgatePlayer(player.getUniqueId())) {
            future.cancel(false);
            return future;
        }

        Account account = this.accountService_.create(player, Platform.BEDROCK);
        this.addForm_(BedrockLoginForm.registration(this, player, account, future));
        return future;
    }

    public void setLoggingLevel(Level loggingLevel) {
        this.loggingLevel_ = loggingLevel;
    }

    boolean hasForm(BedrockLoginForm form) {
        return this.activeForms_.contains(form);
    }

    void removeForm(BedrockLoginForm form) {
        this.activeForms_.remove(form);
        this.stopIfIdle_();
    }

    void log(String message) {
        this.log_(message);
    }

    private Optional<Account> findLinkedJavaAccount_(Player player) {
        return this.accountService_.getByName(player.getName())
                .stream()
                .filter(account -> account.hasJava() && !account.hasBedrock())
                .findFirst();
    }

    private void addForm_(BedrockLoginForm form) {
        this.activeForms_.add(form);
        this.startListenerIfNeeded_();
        //Bukkit.getScheduler().runTask(this.plugin_, form::sendFormIfNeeded);
        //Bukkit.getScheduler().runTaskLater(this.plugin_, form::sendFormIfNeeded, 20L);
        Bukkit.getScheduler().runTaskLater(this.plugin_, form::sendFormIfNeeded, 60L);
    }

    private void startListenerIfNeeded_() {
        if (this.listener_ != null) {
            return;
        }
        this.listener_ = new Listener(this);
        this.plugin_.getServer().getPluginManager().registerEvents(this.listener_, this.plugin_);
    }

    private void stopIfIdle_() {
        if (!this.activeForms_.isEmpty() || this.listener_ == null) {
            return;
        }
        HandlerList.unregisterAll(this.listener_);
        this.listener_ = null;
    }

    private void log_(String message) {
        this.plugin_.getLogger().log(this.loggingLevel_, message);
    }

    private static final class Listener implements org.bukkit.event.Listener {

        private final BedrockPlatformAdapter adapter_;

        Listener(BedrockPlatformAdapter adapter) {
            this.adapter_ = adapter;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerMove(PlayerMoveEvent event) {
            Iterator<BedrockLoginForm> iterator = this.adapter_.activeForms_.iterator();
            while (iterator.hasNext()) {
                BedrockLoginForm form = iterator.next();
                if (form.isFor(event.getPlayer()) && !form.hasFormBeenSent()) {
                    form.sendFormIfNeeded();
                }
            }
        }

        @EventHandler
        void onPlayerQuit(PlayerQuitEvent event) {
            Iterator<BedrockLoginForm> iterator = this.adapter_.activeForms_.iterator();
            while (iterator.hasNext()) {
                BedrockLoginForm form = iterator.next();
                if (form.isFor(event.getPlayer())) {
                    form.cancel();
                    iterator.remove();
                }
            }
            this.adapter_.stopIfIdle_();
        }
    }
}
