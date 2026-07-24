package com.kntrel.mc.accwarden.gateway;

import java.time.Instant;

public record Penalty(NetworkKey client, Instant until) {}
