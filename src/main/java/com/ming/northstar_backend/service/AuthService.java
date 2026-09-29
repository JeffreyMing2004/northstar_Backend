package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.security.JwtUtil;
import com.ming.northstar_backend.support.McIdFormat;
import com.ming.northstar_backend.support.OnceBinding;
import com.ming.northstar_backend.support.PasswordPolicy;
import com.ming.northstar_backend.support.QqFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Locale;

@Service
public class AuthService {

    /** 用户不存在时也要跑一次 BCrypt，抹平「账号存在与否」的响应时间差（防枚举）。 */
    private static final String TIMING_EQUALIZER_PASSWORD = "northstar-timing-equalizer";

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final BetaService betaService;
    private final AdminAccessService adminAccessService;
    private final QqFormat qqFormat;
    private final BetaWhitelistService betaWhitelistService;
    private final PasswordPolicy passwordPolicy;
    private final AuthGuard authGuard;
    private final TokenRevocationService revocationService;
    private final String timingEqualizerHash;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil, EmailService emailService,
                       BetaService betaService, AdminAccessService adminAccessService, QqFormat qqFormat,
                       BetaWhitelistService betaWhitelistService, PasswordPolicy passwordPolicy,
                       AuthGuard authGuard, TokenRevocationService revocationService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.betaService = betaService;
        this.adminAccessService = adminAccessService;
        this.qqFormat = qqFormat;
        this.betaWhitelistService = betaWhitelistService;
        this.passwordPolicy = passwordPolicy;
        this.authGuard = authGuard;
        this.revocationService = revocationService;
        this.timingEqualizerHash = encoder.encode(TIMING_EQUALIZER_PASSWORD);
    }

    /** 兼容无 IP 的调用方；限流维度退化为账号。 */
    public AuthResponse login(LoginRequest req) {
        return login(req, null);
    }

    /**
     * 登录。
     *
     * <p>改动点（NS-08）：按「来源 IP」与「账号」双向限流，失败会计数并加渐进延迟，
     * 成功即清零账号计数。失败文案保持统一，不暴露账号是否存在。</p>
     */
    public AuthResponse login(LoginRequest req, String clientIp) {
        String account = req.getUsername() == null ? "" : req.getUsername().trim();
        String password = req.getPassword() == null ? "" : req.getPassword();
        authGuard.assertLoginAllowed(clientIp, account);

        User user = userRepo.findByUsername(account)
            .or(() -> userRepo.findByEmail(account))
            .orElse(null);

        if (user == null) {
            // 不存在的账号也走一次 BCrypt，避免时间差被用来枚举用户
            encoder.matches(password, timingEqualizerHash);
            authGuard.recordLoginFailure(clientIp, account);
            throw new RuntimeException("用户名或密码错误");
        }
        if (!encoder.matches(password, user.getPassword())) {
            authGuard.recordLoginFailure(clientIp, account);
            throw new RuntimeException("用户名或密码错误");
        }

        authGuard.clearLoginFailures(account);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, toDto(user));
    }

    /** 忘记密码第一步：按注册邮箱检索账号身份，供用户确认后再发验证码。 */
    public ForgotIdentityDto lookupForgotIdentity(String email, String clientIp) {
        // 该接口会回显用户名与游戏 ID，属于敏感查询，先过 IP 限流（NS-03 / NS-11）
        authGuard.assertForgotLookupAllowed(clientIp);
        String value = email == null ? "" : email.trim();
        if (value.isBlank()) {
            throw new RuntimeException("请输入邮箱");
        }
        User user = userRepo.findByEmail(value)
            .orElseThrow(() -> new RuntimeException("该邮箱尚未注册 NorthStar 账号"));
        ForgotIdentityDto dto = new ForgotIdentityDto();
        dto.setUsername(user.getUsername());
        dto.setMcId(user.getMcId());
        dto.setEmail(user.getEmail());
        return dto;
    }

    /**
     * 忘记密码最后一步：校验邮箱验证码后写入新密码。
     *
     * <p>验证码校验成功即视为邮箱所有权确认，直接签发新 token 让前端免二次登录。
     * 同时作废该账号此前签发的所有令牌——重置密码通常意味着「账号可能已被他人掌握」，
     * 不吊销存量令牌等于给攻击者留了 24 小时的后门（NS-01 / NS-03）。</p>
     */
    public AuthResponse resetPassword(ResetPasswordRequest req, String clientIp) {
        authGuard.assertResetAllowed(clientIp);

        String email = req.getEmail() == null ? "" : req.getEmail().trim();
        String code = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
        String password = req.getNewPassword();

        if (email.isBlank()) {
            throw new RuntimeException("请输入邮箱");
        }
        if (code.isBlank()) {
            throw new RuntimeException("请输入邮箱验证码");
        }
        // 与注册统一走同一套强度策略（NS-10）
        passwordPolicy.validate(password, "新密码");

        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("该邮箱尚未注册 NorthStar 账号"));
        if (!emailService.verifyCode(email, code, "reset")) {
            int left = emailService.remainingAttempts(email, "reset");
            throw new RuntimeException(left > 0
                    ? "验证码错误或已过期（剩余 " + left + " 次）"
                    : "验证码错误次数过多，请重新获取验证码");
        }

        user.setPassword(encoder.encode(password));
        user = userRepo.save(user);
        revocationService.revokeAllForUser(user.getId());
        // iat 推后 1 秒：否则新令牌会落在刚写入的吊销时间戳之内而被自己判为失效
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(),
                new Date(System.currentTimeMillis() + 1000L));
        return new AuthResponse(token, toDto(user));
    }

    public AuthResponse register(RegisterRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String email = req.getEmail() == null ? "" : req.getEmail().trim();
        // QQ 与 Minecraft ID 是内测资格校验的两个键，格式非法直接拒绝，避免脏数据流到白名单
        String mcId = McIdFormat.require(req.getMcId(), "Minecraft ID");
        String qq = qqFormat.require(req.getQq(), "QQ 号");

        if (username.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (username.length() > 32) {
            throw new RuntimeException("用户名长度不能超过32个字符");
        }
        if (email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (email.length() > 128 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new RuntimeException("邮箱格式无效");
        }
        // 注册与重置使用同一套密码强度策略（NS-10）
        passwordPolicy.validate(req.getPassword(), "密码");
        if (!emailService.verifyCode(email, req.getEmailCode(), "register")) {
            int left = emailService.remainingAttempts(email, "register");
            throw new RuntimeException(left > 0
                    ? "验证码错误或已过期（剩余 " + left + " 次）"
                    : "验证码错误次数过多，请重新获取验证码");
        }
        if (userRepo.existsByUsername(username)) {
            throw new RuntimeException("用户名已被注册");
        }
        if (userRepo.existsByEmail(email)) {
            throw new RuntimeException("邮箱已被注册");
        }
        if (!mcId.isEmpty() && userRepo.existsByMcId(mcId)) {
            throw new RuntimeException("该 Minecraft ID 已被其他账号绑定");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(req.getPassword()));
        user.setEmail(email);
        // 注册即首次绑定：走 OnceBinding 而不是裸 setter，这样绑定时间会被一并记录，
        // 之后该账号在任何入口都不能再改（管理员纠错除外）
        OnceBinding.bind(user, OnceBinding.Field.QQ, qq);
        OnceBinding.bind(user, OnceBinding.Field.MC_ID, mcId);
        user = userRepo.save(user);
        user = betaService.claimApprovedEligibility(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, toDto(user));
    }

    public UserDto getProfile(Long userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toDto(user);
    }

    public UserDto updateProfile(Long userId, UserDto update) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (update.getEmail() != null) user.setEmail(update.getEmail());
        if (update.getQq() != null) {
            // QQ 一个账号只允许绑定一次：未绑定时这次提交会写入，已绑定后再改直接抛错。
            // 留空不算解绑（bind 内部对空值直接返回 false），避免误传空串把绑定抹掉。
            OnceBinding.bind(user, OnceBinding.Field.QQ, qqFormat.require(update.getQq(), "QQ 号"));
        }
        if (update.getMcId() != null) {
            // Minecraft ID 与 QQ 同规则：一个账号只允许绑定一次，绑定后不可自行更改。
            String mcId = McIdFormat.require(update.getMcId(), "Minecraft ID");
            if (!mcId.isEmpty() && !mcId.equals(user.getMcId()) && userRepo.existsByMcId(mcId)) {
                throw new RuntimeException("该 Minecraft ID 已被其他账号绑定");
            }
            OnceBinding.bind(user, OnceBinding.Field.MC_ID, mcId);
        }
        user = userRepo.save(user);
        user = betaService.claimApprovedEligibility(user);
        return toDto(user);
    }

    public UserDto bindGameAccount(Long userId, String mcId) {
        String next = McIdFormat.requirePresent(mcId, "Minecraft ID");

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!next.equals(user.getMcId()) && userRepo.existsByMcId(next)) {
            throw new RuntimeException("该 Minecraft ID 已被其他账号绑定");
        }
        // Minecraft ID 与 QQ 同为内测资格凭据，因此同样只允许绑定一次：
        // 未绑定时写入，已绑定后再提交不同的值直接抛错（管理员可在后台强制改写）。
        OnceBinding.bind(user, OnceBinding.Field.MC_ID, next);

        user = userRepo.save(user);
        user = betaService.claimApprovedEligibility(user);
        return toDto(user);
    }

    /**
     * 统一的出参构造：把「当前登录用户自己的」白名单状态一起带上，
     * 账号设置页据此显示资格是否真的生效（而不是只看 betaStatus）。
     */
    private UserDto toDto(User user) {
        UserDto dto = UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
        BetaWhitelistService.Qualification qualification =
                betaWhitelistService.describeQualification(user.getQq(), user.getMcId());
        dto.setWhitelistStatus(qualification.status().name().toLowerCase(Locale.ROOT));
        if (qualification.entry() != null) {
            dto.setWhitelistExpireAt(qualification.entry().getExpireAt());
        }
        return dto;
    }
}
