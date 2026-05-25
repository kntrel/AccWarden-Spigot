package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.form.FormRenderer;

public interface Platform {

    String displayName();

    String key();

    FormRenderer formRenderer();



}
