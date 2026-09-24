package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.security.JwtUtil;
import com.ming.northstar_backend.support.McIdFormat;
import com.ming.northstar_backend.support.OnceBinding;
import com.ming.northstar_backend.support.QqFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final BetaService betaService;
    private final AdminAccessService adminAccessService;
    private final QqFormat qqFormat;
    private final BetaWhitelistService betaWhitelistService;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil, EmailService emailService,
                       BetaService betaService, AdminAccessService adminAccessService, QqFormat qqFormat,
                       BetaWhitelistService betaWhitelistService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.betaService = betaService;
        this.adminAccessService = adminAccessService;
        this.qqFormat = qqFormat;
        this.betaWhitelistService = betaWhitelistService;
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.getUsername())
            .or(() -> userRepo.findByEmail(req.getUsername()))
            .orElse(null);

        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
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
        if (email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (!emailService.verifyCode(email, req.getEmailCode())) {
            throw new RuntimeException("验证码错误或已过期");
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
