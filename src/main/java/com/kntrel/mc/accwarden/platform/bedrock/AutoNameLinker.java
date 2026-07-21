package com.kntrel.mc.accwarden.platform.bedrock;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.runical.bukkit.Translator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.geysermc.event.subscribe.OwnedSubscriber;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.link.PlayerLink;
import org.geysermc.floodgate.util.LinkedPlayer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

public final class AutoNameLinker implements AutoCloseable {

    private static final long LINK_TIMEOUT_SECONDS = 10L;
    private static final String DISCONNECT_FALLBACK = "We couldn't link your Bedrock account to its Java identity. Please try again.";

    private final AccWarden plugin_;
    private final Translator translator_;
    private final FloodgateApi floodgateApi_;
    private final PlayerLink playerLink_;
    private final EventRegistrar geyserEventRegistrar_;
    private final OwnedSubscriber<EventRegistrar, SessionLoginEvent> sessionLoginSubscriber_;

    public AutoNameLinker(AccWarden plugin, Translator translator) {
        this(plugin, translator, FloodgateApi.getInstance());
    }

    AutoNameLinker(AccWarden plugin, Translator translator, FloodgateApi floodgateApi) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.translator_ = Objects.requireNonNull(translator, "translator");
        this.floodgateApi_ = Objects.requireNonNull(floodgateApi, "floodgateApi");
        this.playerLink_ = this.validateFloodgateRequirements_();
        this.geyserEventRegistrar_ = EventRegistrar.of(this.plugin_);
        this.sessionLoginSubscriber_ = GeyserApi.api()
                .eventBus()
                .subscribe(this.geyserEventRegistrar_, SessionLoginEvent.class, this::onSessionLogin_);
        this.plugin_.getLogger().info("Bedrock AutoNameLinking is enabled. Bedrock logins will be linked before joining.");
    }

    @Override
    public void close() {
        GeyserApi.api().eventBus().unregisterAll(this.geyserEventRegistrar_);
    }

    private void onSessionLogin_(SessionLoginEvent event) {
        String javaUsername = this.javaUsername_(event);
        UUID bedrockUuid = this.bedrockUuid_(event);
        UUID javaUuid = this.javaUuidFor_(javaUsername);

        try {
            this.ensureLinked_(bedrockUuid, javaUuid, javaUsername);
        } catch (TimeoutException ex) {
            this.plugin_.getLogger().log(
                    Level.SEVERE,
                    "Timed out AutoNameLinking Bedrock player " + event.connection().bedrockUsername() + " to Java account " + javaUsername + ".",
                    ex
            );
            event.setCancelled(true, this.disconnectReason_(event));
        } catch (Exception ex) {
            this.plugin_.getLogger().log(
                    Level.SEVERE,
                    "Failed to AutoNameLink Bedrock player " + event.connection().bedrockUsername() + " to Java account " + javaUsername + ".",
                    ex
            );
            event.setCancelled(true, this.disconnectReason_(event));
        }
    }

    private String disconnectReason_(SessionLoginEvent event) {
        return this.translator_
                .translate(event.connection().languageCode(), "auto_name_link_failed")
                .orDefault(DISCONNECT_FALLBACK)
                .message();
    }

    private void linkPlayer_(UUID bedrockUuid, UUID javaUuid, String javaUsername) throws Exception {
        this.playerLink_.linkPlayer(bedrockUuid, javaUuid, javaUsername)
                .get(LINK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void ensureLinked_(UUID bedrockUuid, UUID javaUuid, String javaUsername) throws Exception {
        LinkedPlayer linkedPlayer = this.playerLink_.getLinkedPlayer(bedrockUuid)
                .get(LINK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (linkedPlayer == null) {
            this.linkPlayer_(bedrockUuid, javaUuid, javaUsername);
            this.plugin_.getLogger().fine("AutoNameLinked Bedrock UUID " + bedrockUuid + " to Java account " + javaUsername + ".");
            return;
        }
        if (javaUuid.equals(linkedPlayer.getJavaUniqueId())) {
            this.plugin_.getLogger().fine("AutoNameLink skipped for Bedrock UUID " + bedrockUuid + ": already linked to Java account " + javaUsername + ".");
            return;
        }
        this.plugin_.getLogger().warning(
                "Bedrock UUID " + bedrockUuid
                        + " is already linked to Java UUID " + linkedPlayer.getJavaUniqueId()
                        + ", but AutoNameLink expected Java UUID " + javaUuid
                        + ". The stale link will be replaced."
        );
        this.playerLink_.unlinkPlayer(linkedPlayer.getJavaUniqueId())
                .get(LINK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        this.linkPlayer_(bedrockUuid, javaUuid, javaUsername);
        this.plugin_.getLogger().fine("AutoNameLinked Bedrock UUID " + bedrockUuid + " to Java account " + javaUsername + " after replacing a stale link.");
    }

    private PlayerLink validateFloodgateRequirements_() {
        this.validateFloodgateConfig_();

        PlayerLink playerLink = this.floodgateApi_.getPlayerLink();
        if (playerLink == null) {
            throw new IllegalStateException("Floodgate player linking API is unavailable.");
        }
        if (!playerLink.isEnabled()) {
            throw new IllegalStateException("Floodgate player linking must be enabled.");
        }
        if (playerLink.isAllowLinking()) {
            throw new IllegalStateException("Floodgate linking commands must be disabled.");
        }
        if (playerLink.getName() == null || playerLink.getName().isBlank()) {
            throw new IllegalStateException("Floodgate owned/local linking must be configured, and global-only linking must be disabled.");
        }
        if (!this.floodgateApi_.getPlayerPrefix().isEmpty()) {
            this.plugin_.getLogger().warning(
                    "Bedrock AutoNameLinking works best when Floodgate's username-prefix is empty, but it is currently '"
                            + this.floodgateApi_.getPlayerPrefix()
                            + "'."
            );
        }
        return playerLink;
    }

    private void validateFloodgateConfig_() {
        Plugin floodgate = this.plugin_.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null) {
            throw new IllegalStateException("Floodgate plugin is not available.");
        }

        File configFile = new File(floodgate.getDataFolder(), "config.yml");
        if (!configFile.isFile()) {
            throw new IllegalStateException("Floodgate config.yml was not found.");
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.getBoolean("player-link.enabled", false)) {
            throw new IllegalStateException("Floodgate player linking must be enabled.");
        }
        if (!config.getBoolean("player-link.enable-own-linking", false)) {
            throw new IllegalStateException("Floodgate owned/local linking must be enabled.");
        }
        if (config.getBoolean("player-link.enable-global-linking", true)) {
            throw new IllegalStateException("Floodgate global linking must be disabled.");
        }
        if (config.getBoolean("player-link.allowed", true)) {
            throw new IllegalStateException("Floodgate linking commands must be disabled.");
        }
    }

    private UUID bedrockUuid_(SessionLoginEvent event) {
        String xuid = event.connection().xuid();
        try {
            return this.floodgateApi_.createJavaPlayerId(Long.parseUnsignedLong(xuid));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Bedrock XUID is not a valid unsigned long: " + xuid, ex);
        }
    }

    private String javaUsername_(SessionLoginEvent event) {
        String javaUsername = event.connection().javaUsername();
        if (javaUsername != null && !javaUsername.isBlank()) {
            return javaUsername;
        }
        return event.connection().bedrockUsername();
    }

    private UUID javaUuidFor_(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
