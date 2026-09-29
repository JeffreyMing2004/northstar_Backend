package com.ming.northstar_backend.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NS-09：真实 IP 的解析必须优先采信反代已经还原过的 remoteAddr，
 * 绝不能直接取 X-Forwarded-For 的首段——那是攻击者可写的字段。
 */
class ClientIpTest {

    @Test
    void prefersRemoteAddrOverSpoofableForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "1.1.1.1");

        assertEquals("203.0.113.7", ClientIp.of(request));
    }

    @Test
    void fallsBackToForwardedHeaderOnlyWhenRemoteIsLoopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 10.0.0.1");

        assertEquals("198.51.100.9", ClientIp.of(request));
    }

    @Test
    void ignoresNonIpGarbageInForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip, 198.51.100.9");

        assertEquals("198.51.100.9", ClientIp.of(request));
    }

    @Test
    void stripsPortFromForwardedEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5:51234");

        assertEquals("203.0.113.5", ClientIp.of(request));
    }

    @Test
    void returnsUnknownWhenNothingUsableIsAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");

        assertEquals("unknown", ClientIp.of(request));
    }
}
