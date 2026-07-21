package com.kntrel.mc.accwarden.platform;

public enum PlatformKey {
    JAVA("java"),
    BEDROCK("bedrock");

    private final String value_;

    PlatformKey(String value) {
        this.value_ = value;
    }

    public String value() {
        return this.value_;
    }
}
