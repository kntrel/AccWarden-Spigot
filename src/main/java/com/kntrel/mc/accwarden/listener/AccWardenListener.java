package com.kntrel.mc.accwarden.listener;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.session.SessionService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

public final class AccWardenListener implements Listener {

    //FIELDS
    private final AccWarden plugin_;
    private final SessionService sessionService_;

    //CONSTRUCTORS
    public AccWardenListener(AccWarden plugin) {
        this.plugin_ = plugin;
        this.sessionService_ = this.plugin_.getSessionService();
    }

    //EVENT HANDLERS
    @EventHandler
    void OnPlayerJoin(PlayerJoinEvent e) {
        this.sessionService_.giveSession(e.getPlayer());
    }

    @EventHandler
    void OnPlayerQuit(PlayerQuitEvent e) {
        this.sessionService_.rememberSession(e.getPlayer());
    }

    @EventHandler
    void OnPlayerMove(PlayerMoveEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerOpenInventory(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) { return; }
        if (this.sessionService_.isLogged(player)) { return; }
        player.closeInventory();
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteract(PlayerInteractEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void OnPlayerUseChat(AsyncPlayerChatEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.send_message")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerIssueCommand(PlayerCommandPreprocessEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.issue_command")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void onPlayerDropItem(PlayerDropItemEvent e) {
        if (this.sessionService_.isLogged(e.getPlayer())) { return; }
        e.setCancelled(true);
    }
}
