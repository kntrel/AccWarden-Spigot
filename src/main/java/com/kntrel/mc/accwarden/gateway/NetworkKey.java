package com.kntrel.mc.accwarden.gateway;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

public record NetworkKey(byte[] network) {

    //CONSTANTS
    private static final NetworkKey UNRESOLVED = new NetworkKey(new byte[0]);
    public static NetworkKey unresolved() {
        return UNRESOLVED;
    }


    //CONSTRUCTORS
    public NetworkKey {
        network = Objects.requireNonNull(network, "network").clone();
    }


    //CONTRACT
    public boolean isUnresolved() {
        return this.network.length == 0;
    }


    //IMPLEMENTATION
    @Override
    public byte[] network() {
        return this.network.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof NetworkKey key
                && Arrays.equals(this.network, key.network);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.network);
    }

    @Override
    public String toString() {
        if (this.network.length == 0) {
            return "unresolved";
        }
        try {
            return InetAddress.getByAddress(this.network).getHostAddress();
        } catch (UnknownHostException ignored) {
            return Arrays.toString(this.network);
        }
    }
}
