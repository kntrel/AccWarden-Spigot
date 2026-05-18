package com.kntrel.mc.accwarden.listener;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.account.Platform;
import com.jkantrell.accwarden.session.*;
import com.kntrel.mc.accwarden.session.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import java.util.logging.Level;

public final class AccWardenListener implements Listener {

    //FIELDS
    private final AccWarden plugin_;
    private final AccountRepository accountRepository_;
    private final SessionHolder sessionHolder_;
    private final JavaSessionHandler javaHandler_;
    private final BedrockSessionHandler bedrockHandler_;

    //CONSTRUCTORS
    public AccWardenListener(AccWarden plugin) {
        this.plugin_ = plugin;
        this.accountRepository_ = this.plugin_.getAccountRepository();
        this.sessionHolder_ = this.plugin_.getSessionHolder();;
        this.sessionHolder_.setLoggingLevel(Level.INFO);
        this.javaHandler_ = new JavaSessionHandler(this.accountRepository_, this.sessionHolder_, this.plugin_);
        this.javaHandler_.setLoggingLevel(Level.INFO);

        if (plugin.isBedrockOn()) {
            this.bedrockHandler_ = new BedrockSessionHandler(this.accountRepository_, this.sessionHolder_, this.plugin_);
            this.bedrockHandler_.setLoggingLevel(Level.INFO);
        } else {
            this.bedrockHandler_ = null;
        }
    }

    //EVENT HANDLERS
    @EventHandler
    void OnPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        SessionHandler handler = (this.plugin_.isBedrockOn() && BedrockSessionHandler.isBedrock(player)) ?
                this.bedrockHandler_ : this.javaHandler_;
        handler.handle(player);
    }

    @EventHandler
    void OnPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (!LoginManager.isLogged(player)) { return; }
        if (!this.accountRepository_.exists(player)) { return; }
        Account account = this.accountRepository_.retrieve(player);
        Platform platform = (this.plugin_.isBedrockOn() && BedrockSessionHandler.isBedrock(player)) ? Platform.BEDROCK : Platform.JAVA;
        this.sessionHolder_.openNew(account, player, platform);
    }

    @EventHandler
    void OnPlayerMove(PlayerMoveEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerOpenInventory(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) { return; }
        if (LoginManager.isLogged(player)) { return; }
        player.closeInventory();
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteract(PlayerInteractEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void OnPlayerUseChat(AsyncPlayerChatEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getLangProvider().getEntry(e.getPlayer(),"error.not_allowed.send_message"));
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerIssueCommand(PlayerCommandPreprocessEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getLangProvider().getEntry(e.getPlayer(),"error.not_allowed.issue_command"));
        e.setCancelled(true);
    }

    @EventHandler
    void onPlayerDropItem(PlayerDropItemEvent e) {
        if (LoginManager.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }
}
