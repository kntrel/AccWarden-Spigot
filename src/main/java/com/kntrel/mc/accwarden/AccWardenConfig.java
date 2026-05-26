package com.kntrel.mc.accwarden;

import org.bukkit.configuration.ConfigurationSection;

public record AccWardenConfig(
        String defaultLanguage,
        boolean playerNameAutoLinking,
        boolean crossPlatformSessions,
        int sessionHoldTime,
        boolean failLoginAccountLock,
        int failLoginOdd,
        int failLoginWarn,
        int failLoginLock,
        int passwordMinSize,
        int passwordMaxSize
) {
    public static final AccWardenConfig DEFAULT = new AccWardenConfig(
            "en",
            false,
            false,
            300,
            false,
            3,
            0,
            0,
            4,
            12
    );

    public static AccWardenConfig load(ConfigurationSection config) {
        return new AccWardenConfig(
                config.getString("defaultLanguage", DEFAULT.defaultLanguage()),
                config.getBoolean("playerNameAutoLinking", DEFAULT.playerNameAutoLinking()),
                config.getBoolean("sessions.crossPlatform", DEFAULT.crossPlatformSessions()),
                config.getInt("sessions.holdTime", DEFAULT.sessionHoldTime()),
                config.getBoolean("failed_login_count.accountLock", DEFAULT.failLoginAccountLock()),
                config.getInt("failed_login_count.odd", DEFAULT.failLoginOdd()),
                config.getInt("failed_login_count.warn", DEFAULT.failLoginWarn()),
                config.getInt("failed_login_count.lock", DEFAULT.failLoginLock()),
                config.getInt("password_format.min_length", DEFAULT.passwordMinSize()),
                config.getInt("password_format.max_length", DEFAULT.passwordMaxSize())
        );
    }
}
