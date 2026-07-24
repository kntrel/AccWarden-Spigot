package com.kntrel.mc.accwarden.gateway;

import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;

public final class NetworkKeyResolver {

    //CONSTANTS
    private static final int
            IPV4_ADDRESS_BITS = 32,
            IPV6_ADDRESS_BITS = 128,
            IPV4_ADDRESS_BYTES = IPV4_ADDRESS_BITS / Byte.SIZE,
            IPV6_ADDRESS_BYTES = IPV6_ADDRESS_BITS / Byte.SIZE,
            IPV4_MAPPED_PREFIX_BITS = IPV6_ADDRESS_BITS - IPV4_ADDRESS_BITS,
            IPV4_MAPPED_PREFIX_BYTES = IPV4_MAPPED_PREFIX_BITS / Byte.SIZE;

    //FIELDS
    private final int ipv4PrefixLength_, ipv6PrefixLength_;


    //CONSTRUCTORS
    public NetworkKeyResolver(int ipv4PrefixLength, int ipv6PrefixLength) {
        validatePrefixLength_(ipv4PrefixLength, IPV4_ADDRESS_BITS, "IPv4");
        validatePrefixLength_(ipv6PrefixLength, IPV6_ADDRESS_BITS, "IPv6");
        this.ipv4PrefixLength_ = ipv4PrefixLength;
        this.ipv6PrefixLength_ = ipv6PrefixLength;
    }


    //CONTRACT
    public NetworkKey resolve(Player player) {
        InetSocketAddress socketAddress = Objects.requireNonNull(player, "player").getAddress();
        return socketAddress == null
                ? NetworkKey.unresolved()
                : this.resolve(socketAddress.getAddress());
    }

    public NetworkKey resolve(InetAddress address) {
        if (address == null) {
            return NetworkKey.unresolved();
        }

        byte[] addressBytes = address.getAddress();
        byte[] network = new byte[IPV6_ADDRESS_BYTES];
        int prefixLength;

        if (addressBytes.length == IPV4_ADDRESS_BYTES) {
            mapIpv4_(addressBytes, network);
            prefixLength = IPV4_MAPPED_PREFIX_BITS + this.ipv4PrefixLength_;
        } else if (addressBytes.length == IPV6_ADDRESS_BYTES) {
            System.arraycopy(addressBytes, 0, network, 0, network.length);
            prefixLength = isIpv4Mapped_(network)
                    ? IPV4_MAPPED_PREFIX_BITS + this.ipv4PrefixLength_
                    : this.ipv6PrefixLength_;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported IP address length: " + addressBytes.length + " bytes"
            );
        }

        mask_(network, prefixLength);
        return new NetworkKey(network);
    }


    //HELPERS
    private static void validatePrefixLength_(int prefixLength, int maximum, String protocol) {
        if (prefixLength < 0 || prefixLength > maximum) {
            throw new IllegalArgumentException(
                    protocol + " prefix length must be between 0 and " + maximum
            );
        }
    }

    private static void mapIpv4_(byte[] ipv4, byte[] mapped) {
        mapped[IPV4_MAPPED_PREFIX_BYTES - 2] = (byte) 0xFF;
        mapped[IPV4_MAPPED_PREFIX_BYTES - 1] = (byte) 0xFF;
        System.arraycopy(ipv4, 0, mapped, IPV4_MAPPED_PREFIX_BYTES, ipv4.length);
    }

    private static boolean isIpv4Mapped_(byte[] address) {
        for (int index = 0; index < IPV4_MAPPED_PREFIX_BYTES - 2; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return address[IPV4_MAPPED_PREFIX_BYTES - 2] == (byte) 0xFF
                && address[IPV4_MAPPED_PREFIX_BYTES - 1] == (byte) 0xFF;
    }

    private static void mask_(byte[] address, int prefixLength) {
        int firstInsignificantByte = prefixLength / Byte.SIZE;
        int significantBitsInBoundaryByte = prefixLength % Byte.SIZE;

        if (significantBitsInBoundaryByte != 0) {
            int mask = 0xFF << (Byte.SIZE - significantBitsInBoundaryByte);
            address[firstInsignificantByte] &= (byte) mask;
            firstInsignificantByte++;
        }

        Arrays.fill(address, firstInsignificantByte, address.length, (byte) 0);
    }
}
