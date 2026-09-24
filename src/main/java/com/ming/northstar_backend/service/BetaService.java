package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BetaApplyRequest;
import com.ming.northstar_backend.dto.BetaCheckResponse;
import com.ming.northstar_backend.dto.AdminBetaGrantRequest;
import com.ming.northstar_backend.dto.MyBetaApplicationDto;
import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.BetaPlan;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.support.OnceBinding;
import com.ming.northstar_backend.support.QqFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BetaService.class);

    private final UserRepository userRepo;
    private final BetaApplicationRepository betaRepo;
    private final BetaPlanService betaPlanService;
    private final QqFormat qqFormat;
    private final BetaWhitelistService betaWhitelistService;

    public BetaService(UserRepository userRepo, BetaApplicationRepository betaRepo,
                       BetaPlanService betaPlanService, QqFormat qqFormat,
                       BetaWhitelistService betaWhitelistService) {
        this.userRepo = userRepo;
        this.betaRepo = betaRepo;
        this.betaPlanService = betaPlanService;
        this.qqFormat = qqFormat;
        this.betaWhitelistService = betaWhitelistService;
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

    /**
     * 提交内测申请。
     *
     * <p><b>身份一律取自登录账号</b>：{@code userId} 是 JWT 里的用户 id，申请记录里的
     * 用户名 / QQ / 游戏 ID 全部从 {@code users} 表现读，请求体里带什么都不作数。
     * 所以这个接口必须登录才能调用（{@code SecurityConfig} 里 <b>没有</b>把
     * {@code /api/beta/apply} 放进 {@code permitAll}，未登录会被拦在 403），
     * 也就不会出现「用别人的 QQ 帮别人申请」这种代填。</p>
     *
     * <p>审批通过后要按「QQ + 游戏 ID」同步客户端白名单，所以这两项缺失虽然不拦申请，
     * 但前端应当提示玩家先去账号设置补齐，否则资格发下来也进不去游戏。</p>
     */
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
        app.setQq(user.getQq());
        app.setMcId(user.getMcId());
        app.setReason(req.getReason());
        betaRepo.save(app);
    }

    /**
     * 当前登录账号最近一次的内测申请状态。
     *
     * <p>没有任何申请记录时返回 {@code hasApplication=false}（而不是报错）：玩家第一次
     * 打开内测页时就是这个状态，前端据此把表单显示出来。</p>
     */
    public MyBetaApplicationDto getMyApplication(Long userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        List<BetaApplication> applications = betaRepo.findByUserIdOrderByCreatedAtDesc(userId);
        if (applications.isEmpty()) {
            return MyBetaApplicationDto.none(user.getBetaStatus());
        }

        // 计划可能已被运营删除，这里用不抛异常的查找，避免整条查询挂掉
        BetaApplication latest = applications.get(0);
        return MyBetaApplicationDto.from(
            latest, betaPlanService.findPlan(latest.getPlanId()), user.getBetaStatus());
    }

    @Transactional
    public User claimApprovedEligibility(User user) {
        if ("approved".equals(user.getBetaStatus())) return user;

        BetaApplication candidate = findApprovedCandidate(user);
        if (candidate == null) return user;

        candidate.setUserId(user.getId());
        candidate.setEmail(user.getEmail());
        candidate.setUsername(user.getUsername());
        candidate.setQq(user.getQq());
        candidate.setMcId(user.getMcId());
        betaRepo.save(candidate);

        user.setBetaStatus("approved");
        User saved = userRepo.save(user);
        // 认领资格是玩家触发的自动流程：缺 QQ 只告警，不能因此把他的注册/改绑打断
        syncWhitelistQuietly(saved, "认领内测资格");
        return saved;
    }

    @Transactional
    public BetaApplication grantBetaAccess(AdminBetaGrantRequest request) {
        String email = normalize(request == null ? null : request.getEmail());
        String username = normalize(request == null ? null : request.getUsername());
        String mcId = normalize(request == null ? null : request.getMcId());
        String qq = qqFormat.require(request == null ? null : request.getQq(), "QQ 号");
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
            application.setQq(qq.isBlank() ? user.getQq() : qq);
            application.setMcId(user.getMcId());
            application.setReason(reason);
            application.setStatus("approved");
            application = betaRepo.save(application);

            // 后台发放时以管理员填的 QQ 为准（留空则沿用玩家自己的）。
            // 管理员不受「只能绑定一次」限制，所以走 forceSet，并把改动记进日志。
            String qqBefore = user.getQq();
            if (OnceBinding.forceSet(user, OnceBinding.Field.QQ, application.getQq())) {
                log.info("后台发放内测资格时改写 QQ：用户 {}({}) {} -> {}", user.getUsername(), user.getId(),
                        qqBefore == null ? "(未绑定)" : qqBefore,
                        application.getQq() == null || application.getQq().isBlank() ? "(已解绑)" : application.getQq());
            }
            user.setBetaStatus("approved");
            user.setUpdatedAt(java.time.LocalDateTime.now());
            userRepo.save(user);
            requireWhitelistSync(application.getQq(), user.getMcId(), user.getUsername(), "后台发放");
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
        application.setQq(qq.isBlank() ? null : qq);
        application.setMcId(mcId);
        application.setReason(reason);
        application.setStatus("approved");
        application = betaRepo.save(application);
        // 该玩家还没注册账号，只能按管理员填的 QQ + 游戏ID 先发资格
        requireWhitelistSync(application.getQq(), mcId, username, "后台发放");
        return application;
    }

    /**
     * 自动流程里的白名单同步：失败只记录日志，不影响主流程。
     *
     * <p>没有 QQ 时同步必然失败，这里不阻断玩家注册/改绑，但要留下明确的告警，
     * 否则会出现「后台显示已批准、玩家进游戏却被判未通过而崩溃」这种难查的问题。</p>
     */
    private boolean syncWhitelistQuietly(User user, String reason) {
        BetaWhitelistService.SyncResult result = betaWhitelistService.syncApprovedPlayer(
            user.getQq(), user.getMcId(), user.getUsername(),
            "内测审批自动同步（" + reason + "）", "system:beta-approval");
        if (!result.succeeded()) {
            log.warn("[NorthStar] 玩家 {} 已获批但白名单未同步（{}）：需补填 QQ 号，否则进游戏会被判未通过",
                user.getUsername(), result.outcome());
        }
        return result.succeeded();
    }

    /**
     * 人工操作（后台发放 / 审核通过）里的白名单同步：失败必须让整个操作失败。
     *
     * <p>宁可审批报错，也不能出现「后台显示已批准、玩家进游戏却被判未通过而崩溃」。</p>
     */
    private void requireWhitelistSync(String qq, String mcId, String nickname, String reason) {
        BetaWhitelistService.SyncResult result = betaWhitelistService.syncApprovedPlayer(
            qq, mcId, nickname, "内测审批自动同步（" + reason + "）", "system:beta-approval");
        if (result.outcome() == BetaWhitelistService.SyncOutcome.MISSING_QQ) {
            throw new RuntimeException("该玩家尚未填写 QQ 号，无法加入内测白名单；请先在账号上补充 QQ");
        }
        if (!result.succeeded()) {
            throw new RuntimeException("QQ 号或游戏ID 格式非法，无法加入内测白名单");
        }
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
