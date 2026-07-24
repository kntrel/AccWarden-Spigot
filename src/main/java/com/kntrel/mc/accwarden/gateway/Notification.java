package com.kntrel.mc.accwarden.gateway;

import java.util.logging.Level;

public record Notification(String messageKey, Level severity) {}
