package com.kntrel.mc.accwarden.form;

@FunctionalInterface
public interface FormResponseHandler {

    void onResponse(FormResponse response);
}
