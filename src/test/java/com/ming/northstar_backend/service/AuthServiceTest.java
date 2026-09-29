package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.AuthResponse;
import com.ming.northstar_backend.dto.ForgotIdentityDto;
import com.ming.northstar_backend.dto.ResetPasswordRequest;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.security.JwtUtil;
import com.ming.northstar_backend.support.PasswordPolicy;
import com.ming.northstar_backend.support.QqFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final EmailService emailService = mock(EmailService.class);
    private final BetaService betaService = mock(BetaService.class);
    private final AdminAccessService adminAccessService = mock(AdminAccessService.class);
    private final QqFormat qqFormat = mock(QqFormat.class);
    private final BetaWhitelistService betaWhitelistService = mock(BetaWhitelistService.class);
    private final AuthGuard authGuard = mock(AuthGuard.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepo, encoder, jwtUtil, emailService, betaService,
                adminAccessService, qqFormat, betaWhitelistService, new PasswordPolicy(8),
                authGuard, revocationService);
        when(adminAccessService.roleFor(anyString())).thenReturn("user");
        when(betaWhitelistService.describeQualification(any(), any()))
                .thenReturn(new BetaWhitelistService.Qualification(
                        BetaWhitelistService.QualificationStatus.MISSING, null));
    }

    private User user() {
        User user = new User();
        user.setId(11L);
        user.setUsername("JeffreyMing");
        user.setEmail("1640053235@qq.com");
        user.setMcId("JeffreyMing");
        user.setPassword("old-hash");
        return user;
    }

    private ResetPasswordRequest resetRequest(String password, String code) {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("1640053235@qq.com");
        req.setEmailCode(code);
        req.setNewPassword(password);
        return req;
    }

    @Test
    void lookupReturnsUsernameAndMcIdForRegisteredEmail() {
        when(userRepo.findByEmail("1640053235@qq.com")).thenReturn(Optional.of(user()));

        ForgotIdentityDto dto = service.lookupForgotIdentity(" 1640053235@qq.com ", "1.2.3.4");

        assertEquals("JeffreyMing", dto.getUsername());
        assertEquals("JeffreyMing", dto.getMcId());
        assertEquals("1640053235@qq.com", dto.getEmail());
    }

    @Test
    void lookupRejectsUnknownEmailAndBlankInput() {
        when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertEquals("该邮箱尚未注册 NorthStar 账号",
                assertThrows(RuntimeException.class,
                        () -> service.lookupForgotIdentity("nobody@example.com", "1.2.3.4")).getMessage());
        assertEquals("请输入邮箱",
                assertThrows(RuntimeException.class,
                        () -> service.lookupForgotIdentity("  ", "1.2.3.4")).getMessage());
    }

    /** NS-03：找回身份查询必须先过限流闸门，命中即拒绝。 */
    @Test
    void lookupIsBlockedWhenRateLimited() {
        org.mockito.Mockito.doThrow(new com.ming.northstar_backend.support.RateLimitExceededException("查询过于频繁，请稍后再试"))
                .when(authGuard).assertForgotLookupAllowed("1.2.3.4");

        assertThrows(com.ming.northstar_backend.support.RateLimitExceededException.class,
                () -> service.lookupForgotIdentity("1640053235@qq.com", "1.2.3.4"));
    }

    /** NS-10：注册与重置共用同一套强度策略，弱口令在触库之前就要被拒。 */
    @Test
    void resetPasswordRejectsWeakPasswordBeforeTouchingUser() {
        assertEquals("新密码长度不能少于8位",
                assertThrows(RuntimeException.class,
                        () -> service.resetPassword(resetRequest("123", "123456"), "1.2.3.4")).getMessage());
        assertEquals("新密码需同时包含字母和数字",
                assertThrows(RuntimeException.class,
                        () -> service.resetPassword(resetRequest("onlyletters", "123456"), "1.2.3.4")).getMessage());
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsWrongCodeWithoutChangingPassword() {
        User user = user();
        when(userRepo.findByEmail("1640053235@qq.com")).thenReturn(Optional.of(user));
        when(emailService.verifyCode("1640053235@qq.com", "000000", "reset")).thenReturn(false);
        when(emailService.remainingAttempts("1640053235@qq.com", "reset")).thenReturn(3);

        assertEquals("验证码错误或已过期（剩余 3 次）",
                assertThrows(RuntimeException.class,
                        () -> service.resetPassword(resetRequest("newpass123", "000000"), "1.2.3.4")).getMessage());
        assertEquals("old-hash", user.getPassword());
        verify(userRepo, never()).save(any(User.class));
    }

    /** 错误次数用尽后，文案要引导玩家重新发码，而不是继续猜。 */
    @Test
    void resetPasswordReportsExhaustedAttempts() {
        when(userRepo.findByEmail("1640053235@qq.com")).thenReturn(Optional.of(user()));
        when(emailService.verifyCode("1640053235@qq.com", "000000", "reset")).thenReturn(false);
        when(emailService.remainingAttempts("1640053235@qq.com", "reset")).thenReturn(0);

        assertEquals("验证码错误次数过多，请重新获取验证码",
                assertThrows(RuntimeException.class,
                        () -> service.resetPassword(resetRequest("newpass123", "000000"), "1.2.3.4")).getMessage());
    }

    @Test
    void resetPasswordSavesHashedPasswordAndIssuesToken() {
        User user = user();
        when(userRepo.findByEmail("1640053235@qq.com")).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);
        when(emailService.verifyCode("1640053235@qq.com", "123456", "reset")).thenReturn(true);
        when(encoder.encode("newpass123")).thenReturn("new-hash");
        when(jwtUtil.generateToken(any(), anyString(), any(Date.class))).thenReturn("token-1");

        AuthResponse res = service.resetPassword(resetRequest("newpass123", "123456"), "1.2.3.4");

        assertEquals("new-hash", user.getPassword());
        assertEquals("token-1", res.getToken());
        assertEquals("JeffreyMing", res.getUser().getUsername());
        verify(userRepo).save(user);
        // 重置密码必须吊销该账号此前的所有令牌，否则旧令牌仍可继续使用
        verify(revocationService).revokeAllForUser(11L);
    }
}
