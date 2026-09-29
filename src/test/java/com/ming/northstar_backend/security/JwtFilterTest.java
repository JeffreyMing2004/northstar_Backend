package com.ming.northstar_backend.security;

import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.service.AdminAccessService;
import com.ming.northstar_backend.service.TokenRevocationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NS-01 的核心回归测试。
 *
 * <p>修复前：{@code JwtFilter} 直接拿令牌里的 {@code username} 声明去查管理员名单，
 * 于是「知道 JWT 密钥」就等于「知道管理员用户名 ⇒ 拿到后台」。</p>
 *
 * <p>修复后：身份一律以数据库中的 {@code userId} 反查结果为准，
 * 令牌里写谁的名字都无法提权。</p>
 */
class JwtFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AdminAccessService adminAccessService = mock(AdminAccessService.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FilterChain chain = mock(FilterChain.class);

    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtFilter(jwtUtil, adminAccessService, revocationService, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private Claims claims(String subject, String usernameClaim) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("username", String.class)).thenReturn(usernameClaim);
        when(claims.getIssuedAt()).thenReturn(new Date());
        return claims;
    }

    private User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    void usernameClaimInForgedTokenCannotGrantAdminRole() throws Exception {
        // 伪造令牌：sub 指向一个普通玩家，username 声明伪造成管理员
        Claims forged = claims("999", "JeffreyMing");
        when(jwtUtil.tryParse("forged")).thenReturn(Optional.of(forged));
        when(userRepository.findById(999L)).thenReturn(Optional.of(user(999L, "somePlayer")));
        when(adminAccessService.isAdmin("somePlayer")).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWith("forged"), response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "令牌签名有效时仍应建立登录态");
        assertEquals(999L, auth.getPrincipal());
        assertTrue(auth.getAuthorities().isEmpty(), "令牌里的 username 不得换来管理员角色");
        verify(adminAccessService, never()).isAdmin("JeffreyMing");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void realAdminIsResolvedFromDatabase() throws Exception {
        Claims claims = claims("1", "whatever");
        when(jwtUtil.tryParse("admin-token")).thenReturn(Optional.of(claims));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "JeffreyMing")));
        when(adminAccessService.isAdmin("JeffreyMing")).thenReturn(true);

        filter.doFilter(requestWith("admin-token"), new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    /** 用户已被删除时仍建立登录态（无角色），好让 /api/auth/profile 回 401 而不是 403。 */
    @Test
    void staleUserStillAuthenticatesWithoutAuthorities() throws Exception {
        Claims stale = claims("424242", "ghost");
        when(jwtUtil.tryParse("stale")).thenReturn(Optional.of(stale));
        when(userRepository.findById(424242L)).thenReturn(Optional.empty());

        filter.doFilter(requestWith("stale"), new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty());
    }

    @Test
    void revokedTokenIsRejectedWith401AndNeverReachesBusinessCode() throws Exception {
        Claims revoked = claims("7", "JeffreyMing");
        when(jwtUtil.tryParse("revoked")).thenReturn(Optional.of(revoked));
        when(revocationService.isRevoked(any(), any())).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWith("revoked"), response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("登录状态已失效"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void invalidOrAbsentTokenLeavesRequestAnonymous() throws Exception {
        when(jwtUtil.tryParse("bad")).thenReturn(Optional.empty());

        filter.doFilter(requestWith("bad"), new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findById(any());
        verify(adminAccessService, never()).isAdmin(anyString());
    }

    @Test
    void nonNumericSubjectIsIgnored() throws Exception {
        Claims weird = claims("not-a-number", "JeffreyMing");
        when(jwtUtil.tryParse("weird")).thenReturn(Optional.of(weird));

        filter.doFilter(requestWith("weird"), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findById(any());
        verify(chain).doFilter(any(), any());
    }
}
