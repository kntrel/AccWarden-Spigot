package com.kntrel.mc.accwarden.authentication;

import com.kntrel.mc.accwarden.authentication.identity.NetworkKey;
import com.kntrel.mc.accwarden.authentication.identity.NetworkKeyResolver;
import com.kntrel.mc.accwarden.authentication.policy.AuthenticationPolicyConfig;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Inet6Address;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NetworkKeyResolverTest {

    @Test
    void groupsIpv6AddressesByConfiguredPrefix() throws Exception {
        NetworkKeyResolver resolver = new NetworkKeyResolver(new AuthenticationPolicyConfig.Network(32, 64));

        NetworkKey first = resolver.resolve(InetAddress.getByName("2001:db8:abcd:12::1"));
        NetworkKey sameNetwork = resolver.resolve(InetAddress.getByName("2001:db8:abcd:12::ffff"));
        NetworkKey otherNetwork = resolver.resolve(InetAddress.getByName("2001:db8:abcd:13::1"));

        assertEquals(first, sameNetwork);
        assertEquals(first.hashCode(), sameNetwork.hashCode());
        assertNotEquals(first, otherNetwork);
        assertEquals("2001:db8:abcd:12:0:0:0:0", first.toString());
    }

    @Test
    void keepsIpv4AddressesDistinctByDefault() throws Exception {
        NetworkKeyResolver resolver = new NetworkKeyResolver(AuthenticationPolicyConfig.defaults().network());

        NetworkKey first = resolver.resolve(InetAddress.getByName("192.0.2.10"));
        NetworkKey second = resolver.resolve(InetAddress.getByName("192.0.2.11"));

        assertNotEquals(first, second);
        assertArrayEquals(mappedIpv4_(192, 0, 2, 10), first.network());
    }

    @Test
    void masksNonByteAlignedPrefixes() throws Exception {
        NetworkKeyResolver resolver = new NetworkKeyResolver(new AuthenticationPolicyConfig.Network(20, 64));

        NetworkKey key = resolver.resolve(InetAddress.getByName("192.0.18.129"));

        assertArrayEquals(mappedIpv4_(192, 0, 16, 0), key.network());
    }

    @Test
    void normalizesAlreadyMappedIpv4UsingTheIpv4Prefix() throws Exception {
        NetworkKeyResolver resolver = new NetworkKeyResolver(new AuthenticationPolicyConfig.Network(20, 128));
        InetAddress ipv4 = InetAddress.getByName("192.0.18.129");
        InetAddress mappedIpv4 = Inet6Address.getByAddress(null, mappedIpv4_(192, 0, 18, 129), -1);

        assertEquals(resolver.resolve(ipv4), resolver.resolve(mappedIpv4));
    }

    @Test
    void networkKeyDoesNotExposeItsInternalArray() {
        byte[] network = {(byte) 192, 0, 2, 0};
        NetworkKey key = new NetworkKey(network);
        network[0] = 10;
        byte[] exposed = key.network();
        exposed[1] = 99;

        assertArrayEquals(new byte[] {(byte) 192, 0, 2, 0}, key.network());
    }

    private static byte[] mappedIpv4_(int first, int second, int third, int fourth) {
        byte[] result = new byte[16];
        result[10] = (byte) 0xFF;
        result[11] = (byte) 0xFF;
        result[12] = (byte) first;
        result[13] = (byte) second;
        result[14] = (byte) third;
        result[15] = (byte) fourth;
        return result;
    }
}
