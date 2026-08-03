package com.kntrel.mc.accwarden.platform.bedrock;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.form.FormRenderer;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformKey;
import com.kntrel.mc.accwarden.platform.PlatformViews;
import com.kntrel.mc.runical.bukkit.Translator;

import java.util.Objects;

public final class BedrockPlatform implements Platform {

    private final FormRenderer formRenderer_;
    private final PlatformViews views_;

    public BedrockPlatform(AccWarden plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.formRenderer_ = new BedrockFormRenderer(plugin);
        Translator translator = plugin.getRunical();
        AccWardenConfig.Gateway.ClientLoginLimit attemptLimit = plugin
                .getAccWardenConfig()
                .securityPolicies()
                .clientLoginLimit();
        this.views_ = new BedrockViews(
                translator.getChild("registration_form").getChild(PlatformKey.BEDROCK.value()),
                translator.getChild("authentication_form").getChild(PlatformKey.BEDROCK.value()),
                attemptLimit.enabled(),
                attemptLimit.odd(),
                attemptLimit.warn()
        );
    }

    @Override
    public String displayName() {
        return "Bedrock";
    }

    @Override
    public PlatformKey key() {
        return PlatformKey.BEDROCK;
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
    public boolean isJava() {
        return false;
    }

    @Override
    public boolean isBedrock() {
        return true;
    }
}
