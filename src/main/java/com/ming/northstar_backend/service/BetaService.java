package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BetaApplyRequest;
import com.ming.northstar_backend.dto.BetaCheckResponse;
import com.ming.northstar_backend.dto.AdminBetaGrantRequest;
import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.BetaPlan;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.format.DateTimeFormatter;

@Service
public class BetaService {

    private final UserRepository userRepo;
    private final BetaApplicationRepository betaRepo;
    private final BetaPlanService betaPlanService;

    public BetaService(UserRepository userRepo, BetaApplicationRepository betaRepo,
                       BetaPlanService betaPlanService) {
        this.userRepo = userRepo;
        this.betaRepo = betaRepo;
        this.betaPlanService = betaPlanService;
    }

    public BetaCheckResponse checkBeta(String query) {
        String value = query == null ? "" : query.trim();
        User user = userRepo.findByUsername(value)
            .or(() -> userRepo.findByEmail(value))
            .or(() -> userRepo.findByMcId(value))
            .orElse(null);

        if (user == null) {
            BetaApplication pendingClaim = findApprovedCandidate(value);
            return pendingClaim == null ? null : toApprovedResponse(pendingClaim);
        }

        BetaCheckResponse res = new BetaCheckResponse();
        res.setUsername(user.getUsername());
        res.setBetaStatus(user.getBetaStatus());

        if ("approved".equals(user.getBetaStatus())) {
            BetaApplication application = betaRepo
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "approved");
            fillApprovedDetails(res, user.getCreatedAt(), application);
        }

        return res;
    }

    public void applyForBeta(Long userId, BetaApplyRequest req) {
        if (betaRepo.existsByUserIdAndStatus(userId, "pending")) {
            throw new RuntimeException("你已提交过申请，请等待审核");
        }

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        if ("approved".equals(user.getBetaStatus())) {
            throw new RuntimeException("你已拥有内测资格");
        }

        BetaPlan plan = betaPlanService.requireOpenPlan(req.getPlanId());
        BetaApplication app = new BetaApplication();
        app.setUserId(userId);
        app.setPlanId(plan.getId());
        app.setEmail(req.getEmail());
        app.setUsername(user.getUsername());
        app.setMcId(user.getMcId());
        app.setReason(req.getReason());
        betaRepo.save(app);
    }

    @Transactional
    public User claimApprovedEligibility(User user) {
        if ("approved".equals(user.getBetaStatus())) return user;

        BetaApplication candidate = findApprovedCandidate(user);
        if (candidate == null) return user;

        candidate.setUserId(user.getId());
        candidate.setEmail(user.getEmail());
        candidate.setUsername(user.getUsername());
        candidate.setMcId(user.getMcId());
        betaRepo.save(candidate);

        user.setBetaStatus("approved");
        return userRepo.save(user);
    }

    @Transactional
    public BetaApplication grantBetaAccess(AdminBetaGrantRequest request) {
        String email = normalize(request == null ? null : request.getEmail());
        String username = normalize(request == null ? null : request.getUsername());
        String mcId = normalize(request == null ? null : request.getMcId());
        String requestedReason = normalize(request == null ? null : request.getReason());
        String reason = (requestedReason.isBlank() ? "manual" : requestedReason).toLowerCase(Locale.ROOT);

        validateEmail(email);
        if (!Set.of("manual", "veteran", "competitive", "content", "tester", "other").contains(reason)) {
            throw new RuntimeException("内测添加理由无效");
        }
        if (!username.isBlank() && username.length() > 32) {
            throw new RuntimeException("用户名长度不能超过32个字符");
        }
        if (!mcId.isBlank() && !mcId.matches("^[a-zA-Z0-9_]{3,16}$")) {
            throw new RuntimeException("Minecraft ID 格式无效");
        }
        BetaPlan plan = betaPlanService.requireAssignablePlan(request.getPlanId());

        User user = resolveUser(email, username, mcId);
        if (user != null) {
            if ("approved".equals(user.getBetaStatus())) {
                throw new RuntimeException("该玩家已拥有内测资格");
            }
            BetaApplication application = betaRepo
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "pending");
            if (application == null) application = new BetaApplication();
            application.setUserId(user.getId());
            application.setPlanId(plan.getId());
            application.setEmail(email);
            application.setUsername(user.getUsername());
            application.setMcId(user.getMcId());
            application.setReason(reason);
            application.setStatus("approved");
            application = betaRepo.save(application);

            user.setBetaStatus("approved");
            user.setUpdatedAt(java.time.LocalDateTime.now());
            userRepo.save(user);
            return application;
        }

        for (BetaApplication candidate : betaRepo.findByStatusAndUserIdIsNull("approved")) {
            if (equalsIgnoreCase(candidate.getEmail(), email)
                || equalsIgnoreCase(candidate.getUsername(), username)
                || equalsIgnoreCase(candidate.getMcId(), mcId)) {
                throw new RuntimeException("该玩家已有内测资格");
            }
        }

        BetaApplication application = new BetaApplication();
        application.setUserId(null);
        application.setPlanId(plan.getId());
        application.setEmail(email);
        application.setUsername(username);
        application.setMcId(mcId);
        application.setReason(reason);
        application.setStatus("approved");
        return betaRepo.save(application);
    }

    private User resolveUser(String email, String username, String mcId) {
        List<User> matches = new ArrayList<>();
        addIfAbsent(matches, userRepo.findByEmail(email).orElse(null));
        if (!username.isBlank()) addIfAbsent(matches, userRepo.findByUsername(username).orElse(null));
        if (!mcId.isBlank()) addIfAbsent(matches, userRepo.findByMcId(mcId).orElse(null));

        if (matches.size() > 1) {
            throw new RuntimeException("邮箱、用户名和 Minecraft ID 对应不同的玩家");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void addIfAbsent(List<User> users, User user) {
        if (user != null && users.stream().noneMatch(existing -> Objects.equals(existing.getId(), user.getId()))) {
            users.add(user);
        }
    }

    private void validateEmail(String email) {
        if (email.length() > 128) {
            throw new RuntimeException("邮箱长度不能超过128个字符");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new RuntimeException("邮箱格式无效");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private BetaApplication findApprovedCandidate(String query) {
        String value = query == null ? "" : query.trim();
        BetaApplication best = null;
        int bestScore = 99;
        for (BetaApplication app : betaRepo.findByStatusAndUserIdIsNull("approved")) {
            if (!matches(app, value)) continue;
            int score = matchStrength(app, value);
            if (score < bestScore || (score == bestScore && isEarlier(app, best))) {
                best = app;
                bestScore = score;
            }
        }
        return best;
    }

    private BetaApplication findApprovedCandidate(User user) {
        BetaApplication best = null;
        int bestScore = 99;
        for (BetaApplication app : betaRepo.findByStatusAndUserIdIsNull("approved")) {
            if (!matches(app, user)) continue;
            int score = matchStrength(app, user);
            if (score < bestScore || (score == bestScore && isEarlier(app, best))) {
                best = app;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isEarlier(BetaApplication candidate, BetaApplication current) {
        return current == null || (candidate.getId() != null && current.getId() != null
            && candidate.getId() < current.getId());
    }

    private boolean matches(BetaApplication app, String query) {
        return equalsIgnoreCase(app.getEmail(), query)
            || equalsIgnoreCase(app.getMcId(), query)
            || equalsIgnoreCase(app.getUsername(), query);
    }

    private boolean matches(BetaApplication app, User user) {
        return equalsIgnoreCase(app.getEmail(), user.getEmail())
            || equalsIgnoreCase(app.getMcId(), user.getMcId())
            || equalsIgnoreCase(app.getUsername(), user.getUsername());
    }

    private int matchStrength(BetaApplication app, String query) {
        if (equalsIgnoreCase(app.getEmail(), query)) return 0;
        if (equalsIgnoreCase(app.getMcId(), query)) return 1;
        return equalsIgnoreCase(app.getUsername(), query) ? 2 : 99;
    }

    private int matchStrength(BetaApplication app, User user) {
        if (equalsIgnoreCase(app.getEmail(), user.getEmail())) return 0;
        if (equalsIgnoreCase(app.getMcId(), user.getMcId())) return 1;
        return equalsIgnoreCase(app.getUsername(), user.getUsername()) ? 2 : 99;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return !Objects.toString(left, "").isBlank()
            && !Objects.toString(right, "").isBlank()
            && left.equalsIgnoreCase(right);
    }

    private BetaCheckResponse toApprovedResponse(BetaApplication app) {
        BetaCheckResponse res = new BetaCheckResponse();
        res.setUsername(!Objects.toString(app.getUsername(), "").isBlank() ? app.getUsername() : app.getEmail());
        res.setBetaStatus("approved");
        fillApprovedDetails(res, app.getCreatedAt(), app);
        return res;
    }

    private void fillApprovedDetails(BetaCheckResponse res, java.time.LocalDateTime date,
                                     BetaApplication application) {
        BetaPlan plan = application == null ? null : betaPlanService.requirePlan(application.getPlanId());
        res.setType(plan == null ? "标准内测资格" : plan.getName());
        res.setDate(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        res.setExpire(plan == null || plan.getEndsOn() == null ? "长期有效" : plan.getEndsOn().toString());
        res.setModes(plan == null || plan.getAllowedModes() == null || plan.getAllowedModes().isBlank()
            ? "全部竞技模式"
            : plan.getAllowedModes());
    }
}
