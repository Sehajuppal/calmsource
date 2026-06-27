package com.example.calmsource.core.network

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class NetworkClientTest {

    @Test
    fun testIsPrivateOrLocalAddress() {
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("10.0.0.1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("192.168.1.1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("172.16.0.1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("172.31.255.255")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("::1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("fe80::1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("fc00::1")))
        assertTrue(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("fd00::1")))
        
        assertFalse(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("1.1.1.1")))
        assertFalse(NetworkClient.isPrivateOrLocalAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }

    @Test
    fun testIsLocalHostOrPrivateIpStatic() {
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("localhost"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("localhost.localdomain"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("myhost.local"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("127.0.0.1"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("10.0.0.1"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("192.168.0.1"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("172.16.5.5"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("172.31.10.10"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("0.0.0.0"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("::1"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("fe80::1"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("[fe80::1]"))
        assertTrue(NetworkClient.isLocalHostOrPrivateIpStatic("fc00::1"))
        
        assertFalse(NetworkClient.isLocalHostOrPrivateIpStatic("google.com"))
        assertFalse(NetworkClient.isLocalHostOrPrivateIpStatic("8.8.8.8"))
        assertFalse(NetworkClient.isLocalHostOrPrivateIpStatic("172.15.255.255"))
        assertFalse(NetworkClient.isLocalHostOrPrivateIpStatic("172.32.0.0"))
    }
}
