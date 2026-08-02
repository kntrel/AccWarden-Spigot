package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.AccWarden;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import javax.annotation.Nullable;
import java.util.Objects;

public final class HolderImpl implements Holder, Listener {

    private final AccWarden plugin_;
    private final @Nullable World holdWorld_;
    private final NamespacedKey notLoggedKey_;
    private final NamespacedKey gameModeKey_;
    private final NamespacedKey locationKey_;

    public HolderImpl(AccWarden plugin) {
        this(plugin, null);
    }

    public HolderImpl(AccWarden plugin, @Nullable World holdWorld) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.holdWorld_ = holdWorld;
        this.notLoggedKey_ = new NamespacedKey(this.plugin_, "notLogged");
        this.gameModeKey_ = new NamespacedKey(this.plugin_, "loggedGameMode");
        this.locationKey_ = new NamespacedKey(this.plugin_, "loggedLoc");
    }

    @Override
    public boolean isHeld(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!container.has(this.notLoggedKey_, PersistentDataType.BYTE)) {
            return false;
        }

        Byte value = container.get(this.notLoggedKey_, PersistentDataType.BYTE);
        return value == null || value > 0;
    }

    @Override
    public void hold(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(this.notLoggedKey_, PersistentDataType.BYTE, (byte) 1);
        if (!container.has(this.gameModeKey_, PersistentDataType.STRING)) {
            container.set(this.gameModeKey_, PersistentDataType.STRING, player.getGameMode().toString());
        }
        player.setGameMode(GameMode.SPECTATOR);

        if (this.holdWorld_ == null) {
            return;
        }
        if (!container.has(this.locationKey_, PersistentDataType.BYTE_ARRAY)) {
            Location location = player.getLocation();
            container.set(
                    this.locationKey_,
                    PersistentDataType.BYTE_ARRAY,
                    new LocationKey(
                            location.getWorld().getUID(),
                            location.getX(),
                            location.getY(),
                            location.getZ(),
                            location.getYaw(),
                            location.getPitch()
                    ).toBytes()
            );
        }
        player.teleport(this.holdWorld_.getSpawnLocation());
    }

    @Override
    public void unhold(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.remove(this.notLoggedKey_);
        this.restoreLocation_(player, container);
        this.restoreGameMode_(player, container);
    }

    private void restoreLocation_(Player player, PersistentDataContainer container) {
        byte[] serializedLocation = container.get(this.locationKey_, PersistentDataType.BYTE_ARRAY);
        container.remove(this.locationKey_);
        if (serializedLocation == null) { return; }

        try {
            LocationKey location = LocationKey.fromBytes(serializedLocation);
            World world = this.plugin_.getServer().getWorld(location.worldId());
            if (world != null) {
                player.teleport(new Location(
                        world,
                        location.x(),
                        location.y(),
                        location.z(),
                        location.yaw(),
                        location.pitch()
                ));
            }
        } catch (IllegalArgumentException _) {}
    }

    private void restoreGameMode_(Player player, PersistentDataContainer container) {
        String gameMode = container.get(this.gameModeKey_, PersistentDataType.STRING);
        container.remove(this.gameModeKey_);
        if (gameMode == null) { return; }

        try {
            player.setGameMode(GameMode.valueOf(gameMode));
        } catch (IllegalArgumentException _) {}
    }

    @EventHandler
    void OnPlayerMove(PlayerMoveEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerOpenInventory(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) { return; }
        if (!this.isHeld(player)) { return; }
        player.closeInventory();
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteract(PlayerInteractEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void OnPlayerUseChat(AsyncPlayerChatEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.RED + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.send_message")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void OnPlayerIssueCommand(PlayerCommandPreprocessEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.getPlayer().sendMessage(ChatColor.DARK_GRAY + this.plugin_.getRunical()
                .translate(e.getPlayer(), "error.not_allowed.issue_command")
                .orDefault("")
                .message());
        e.setCancelled(true);
    }

    @EventHandler
    void onPlayerDropItem(PlayerDropItemEvent e) {
        if (!this.isHeld(e.getPlayer())) { return; }
        e.setCancelled(true);
    }
}
