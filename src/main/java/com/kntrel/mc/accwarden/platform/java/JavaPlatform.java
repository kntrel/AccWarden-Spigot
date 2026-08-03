package com.kntrel.mc.accwarden.platform.java;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.AccWardenConfig;
import com.kntrel.mc.accwarden.form.FormRenderer;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformKey;
import com.kntrel.mc.accwarden.platform.PlatformViews;
import com.kntrel.mc.runical.bukkit.Translator;

import java.util.Objects;

public final class JavaPlatform implements Platform {

    private final FormRenderer formRenderer_;
    private final PlatformViews views_;

    public JavaPlatform(AccWarden plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.formRenderer_ = new JavaFormRenderer(plugin);
        Translator translator = plugin.getRunical();
        AccWardenConfig.Gateway.ClientLoginLimit attemptLimit = plugin
                .getAccWardenConfig()
                .securityPolicies()
                .clientLoginLimit();
        this.views_ = new JavaViews(
                translator.getChild("registration_form").getChild(PlatformKey.JAVA.value()),
                translator.getChild("authentication_form").getChild(PlatformKey.JAVA.value()),
                attemptLimit.enabled(),
                attemptLimit.odd(),
                attemptLimit.warn()
        );
    }

    @Override
    public String displayName() {
        return "Java";
    }

    @Override
    public PlatformKey key() {
        return PlatformKey.JAVA;
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
        return true;
    }

    @Override
    public boolean isBedrock() {
        return false;
    }
}
