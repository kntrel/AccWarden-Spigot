package com.kntrel.mc.accwarden.listener;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.Platform;
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
    private final AccountService accountService_;
    private final SessionHolder sessionHolder_;
    private final JavaSessionHandler javaHandler_;
    private final BedrockSessionHandler bedrockHandler_;

    //CONSTRUCTORS
    public AccWardenListener(AccWarden plugin) {
        this.plugin_ = plugin;
        this.accountService_ = this.plugin_.getAccountService();
        this.sessionHolder_ = this.plugin_.getSessionHolder();;
        this.sessionHolder_.setLoggingLevel(Level.INFO);
        this.javaHandler_ = new JavaSessionHandler(this.accountService_, this.sessionHolder_, this.plugin_);
        this.javaHandler_.setLoggingLevel(Level.INFO);

        if (plugin.isBedrockOn()) {
            this.bedrockHandler_ = new BedrockSessionHandler(this.accountService_, this.sessionHolder_, this.plugin_);
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
        if (!this.accountService_.isLogged(player)) { return; }
        Platform platform = (this.plugin_.isBedrockOn() && BedrockSessionHandler.isBedrock(player)) ? Platform.BEDROCK : Platform.JAVA;
        if (!this.accountService_.exists(player, platform)) { return; }
        Account account = this.accountService_.get(player, platform).orElseThrow();
        this.sessionHolder_.openNew(account, player, platform);
    }

    @EventHandler
    void OnPlayerMove(PlayerMoveEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerOpenInventory(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) { return; }
        if (this.accountService_.isLogged(player)) { return; }
        player.closeInventory();
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteract(PlayerInteractEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void OnPlayerUseChat(AsyncPlayerChatEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.send_message")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerIssueCommand(PlayerCommandPreprocessEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.issue_command")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void onPlayerDropItem(PlayerDropItemEvent e) {
        if (this.accountService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }
}
