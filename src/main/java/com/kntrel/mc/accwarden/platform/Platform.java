package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.form.FormRenderer;

public interface Platform {

    String JAVA_KEY = "java";
    String BEDROCK_KEY = "bedrock";

    String displayName();

    String key();

    FormRenderer formRenderer();

    PlatformViews views();

    default boolean isJava() {
        return this.key().equals(JAVA_KEY);
    }

    default boolean isBedrock() {
        return this.key().equals(BEDROCK_KEY);
    }
}
