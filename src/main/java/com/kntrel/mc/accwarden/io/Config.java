package com.kntrel.mc.accwarden.io;

import com.jkantrell.yamlizer.yaml.AbstractYamlConfig;
import com.jkantrell.yamlizer.yaml.ConfigField;

public class Config extends AbstractYamlConfig {
    public Config(String filePath) {
        super(filePath);
    }

    @ConfigField
    public String defaultLanguage = "en";

    @ConfigField
    public boolean playerNameAutoLinking = false;

    @ConfigField (path = "sessions.crossPlatform")
    public boolean crossPlatformSessions = false;

    @ConfigField (path = "sessions.holdTime")
    public int sessionHoldTime = 300;

    @ConfigField(path = "failed_login_count.accountLock")
    public boolean failLoginAccountLock = false;

    @ConfigField(path = "failed_login_count.odd")
    public int failLoginOdd = 3;

    @ConfigField(path = "failed_login_count.warn")
    public int failLoginWarn = 0;

    @ConfigField(path = "failed_login_count.lock")
    public int failLoginLock = 0;

    @ConfigField(path = "password_format.min_length")
    public int passwordMinSize = 4;

    @ConfigField(path = "password_format.max_length")
    public int passwordMaxSize = 12;
}
