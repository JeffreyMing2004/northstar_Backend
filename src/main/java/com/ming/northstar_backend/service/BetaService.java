package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BetaApplyRequest;
import com.ming.northstar_backend.dto.BetaCheckResponse;
import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.time.format.DateTimeFormatter;

@Service
public class BetaService {

    private final UserRepository userRepo;
    private final BetaApplicationRepository betaRepo;

    public BetaService(UserRepository userRepo, BetaApplicationRepository betaRepo) {
        this.userRepo = userRepo;
        this.betaRepo = betaRepo;
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

        if ("approved".equals(user.getBetaStatus())) fillApprovedDetails(res, user.getCreatedAt());

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

        BetaApplication app = new BetaApplication();
        app.setUserId(userId);
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
        fillApprovedDetails(res, app.getCreatedAt());
        return res;
    }

    private void fillApprovedDetails(BetaCheckResponse res, java.time.LocalDateTime date) {
        res.setType("标准内测资格");
        res.setDate(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        res.setExpire("2026-12-31");
        res.setModes("全部竞技模式");
    }
}
