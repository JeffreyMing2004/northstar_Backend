package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.security.JwtUtil;
import com.ming.northstar_backend.support.QqFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final BetaService betaService;
    private final AdminAccessService adminAccessService;
    private final QqFormat qqFormat;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil, EmailService emailService,
                       BetaService betaService, AdminAccessService adminAccessService, QqFormat qqFormat) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.betaService = betaService;
        this.adminAccessService = adminAccessService;
        this.qqFormat = qqFormat;
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.getUsername())
            .or(() -> userRepo.findByEmail(req.getUsername()))
            .orElse(null);

        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, UserDto.from(user, adminAccessService.roleFor(user.getUsername())));
    }

    public AuthResponse register(RegisterRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String email = req.getEmail() == null ? "" : req.getEmail().trim();
        String mcId = req.getMcId() == null || req.getMcId().isBlank() ? null : req.getMcId().trim();
        // QQ 号是内测资格校验的键之一，格式非法直接拒绝，避免脏数据流到白名单
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

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(req.getPassword()));
        user.setEmail(email);
        user.setQq(qq.isEmpty() ? null : qq);
        user.setMcId(mcId);
        user = userRepo.save(user);
        user = betaService.claimApprovedEligibility(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, UserDto.from(user, adminAccessService.roleFor(user.getUsername())));
    }

    public UserDto getProfile(Long userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        return UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
    }

    public UserDto updateProfile(Long userId, UserDto update) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (update.getEmail() != null) user.setEmail(update.getEmail());
        if (update.getQq() != null) {
            String qq = qqFormat.require(update.getQq(), "QQ 号");
            user.setQq(qq.isEmpty() ? null : qq);
        }
        if (update.getMcId() != null) user.setMcId(update.getMcId());
        user = userRepo.save(user);
        user = betaService.claimApprovedEligibility(user);
        return UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
    }

    public UserDto bindGameAccount(Long userId, String mcId) {
        if (mcId == null || mcId.isBlank()) {
            throw new RuntimeException("Minecraft ID 不能为空");
        }
        mcId = mcId.trim();
        if (mcId.length() < 3 || mcId.length() > 16) {
            throw new RuntimeException("Minecraft ID 长度需为 3-16 个字符");
        }
        if (!mcId.matches("^[a-zA-Z0-9_]+$")) {
            throw new RuntimeException("Minecraft ID 仅允许字母、数字和下划线");
        }

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (mcId.equals(user.getMcId())) {
            return UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
        }

        if (userRepo.existsByMcId(mcId)) {
            throw new RuntimeException("该 Minecraft ID 已被其他账号绑定");
        }

        user.setMcId(mcId);
        user = userRepo.save(user);
        user = betaService.claimApprovedEligibility(user);
        return UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
    }
}
