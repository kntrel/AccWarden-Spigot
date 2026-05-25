package com.kntrel.mc.accwarden.form;

@FunctionalInterface
public interface FormCallback {

    void onResponse(FormResponse response);
}
