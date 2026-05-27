package com.kntrel.mc.accwarden.platform.bedrock;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.form.FormRenderer;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformViews;
import com.kntrel.mc.runical.bukkit.Translator;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Objects;
import java.util.UUID;

public final class BedrockPlatform implements Platform {

    private final FormRenderer formRenderer_;
    private final PlatformViews views_;
    private final FloodgateApi floodgateApi_;

    public BedrockPlatform(AccWarden plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.floodgateApi_ = FloodgateApi.getInstance();
        this.formRenderer_ = new BedrockFormRenderer(plugin);
        Translator translator = plugin.getRunical();
        this.views_ = new BedrockViews(
                translator.getChild("registration_form").getChild(Platform.BEDROCK_KEY),
                translator.getChild("authentication_form").getChild(Platform.BEDROCK_KEY)
        );
    }

    @Override
    public String displayName() {
        return "Bedrock";
    }

    @Override
    public String key() {
        return Platform.BEDROCK_KEY;
    }

    @Override
    public FormRenderer formRenderer() {
        return this.formRenderer_;
    }

    @Override
    public PlatformViews views() {
        return this.views_;
    }

    @Override
    public UUID accountUuid(Player player) {
        return BedrockPlayerIds.accountUuid(this.floodgateApi_, player);
    }

    @Override
    public boolean isJava() {
        return false;
    }

    @Override
    public boolean isBedrock() {
        return true;
    }
}
