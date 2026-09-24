package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.MatchRecord;
import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.support.McIdFormat;
import com.ming.northstar_backend.support.OnceBinding;
import com.ming.northstar_backend.support.QqFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AdminService {

    private static final Set<String> BETA_STATUSES = Set.of("none", "pending", "approved", "denied");

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepo;
    private final BetaApplicationRepository betaRepo;
    private final RoomRepository roomRepo;
    private final MatchRecordRepository matchRepo;
    private final AdminAccessService adminAccessService;
    private final StatsService statsService;
    private final BetaService betaService;
    private final BetaPlanService betaPlanService;
    private final EmailService emailService;
    private final LiveServerStatsService serverStatsService;
    private final BetaWhitelistService betaWhitelistService;
    private final QqFormat qqFormat;

    public AdminService(UserRepository userRepo, BetaApplicationRepository betaRepo, RoomRepository roomRepo,
                        MatchRecordRepository matchRepo, AdminAccessService adminAccessService,
                        StatsService statsService, BetaService betaService, EmailService emailService,
                        BetaPlanService betaPlanService, LiveServerStatsService serverStatsService,
                        BetaWhitelistService betaWhitelistService, QqFormat qqFormat) {
        this.userRepo = userRepo;
        this.betaRepo = betaRepo;
        this.roomRepo = roomRepo;
        this.matchRepo = matchRepo;
        this.adminAccessService = adminAccessService;
        this.statsService = statsService;
        this.betaService = betaService;
        this.betaPlanService = betaPlanService;
        this.emailService = emailService;
        this.serverStatsService = serverStatsService;
        this.betaWhitelistService = betaWhitelistService;
        this.qqFormat = qqFormat;
    }

    public AdminOverview getOverview() {
        AdminOverview overview = new AdminOverview();
        long totalUsers = userRepo.count();
        overview.setTotalUsers(totalUsers);
        overview.setPendingBetaApplications(betaRepo.countByStatus("pending"));
        overview.setActiveRooms(serverStatsService.getActiveRooms());
        overview.setTotalMatches(matchRepo.count());
        overview.setOnlinePlayers(serverStatsService.getOnlinePlayers());
        overview.setAdminCount(adminAccessService.getAdminCount());
        overview.setMaxAdmins(adminAccessService.getMaxAdmins());
        return overview;
    }

    public List<UserDto> listUsers(String query, String betaStatus) {
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String status = betaStatus == null ? "" : betaStatus.trim();
        return userRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(user -> keyword.isBlank()
                || contains(user.getUsername(), keyword)
                || contains(user.getEmail(), keyword)
                || contains(user.getMcId(), keyword))
            .filter(user -> status.isBlank() || status.equals(user.getBetaStatus()))
            .map(user -> toUserDto(user))
            .collect(Collectors.toList());
    }

    @Transactional
    public UserDto updateUser(Long userId, AdminUserUpdateRequest update) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        // 侧效应（白名单同步 / 撤销）要看的是「改动前 vs 改动后」，所以先把旧值留一份
        String previousBetaStatus = user.getBetaStatus();
        String previousQq = user.getQq();
        String previousMcId = user.getMcId();

        if (update.getRank() != null) {
            String rank = update.getRank().trim();
            if (rank.isBlank() || rank.length() > 32) {
                throw new RuntimeException("段位名称无效");
            }
            user.setRank(rank);
        }
        if (update.getScore() != null) {
            if (update.getScore() < 0 || update.getScore() > 100000) {
                throw new RuntimeException("评分需在 0-100000 之间");
            }
            user.setScore(update.getScore());
        }
        if (update.getBetaStatus() != null) {
            String betaStatus = update.getBetaStatus().trim().toLowerCase(Locale.ROOT);
            if (!BETA_STATUSES.contains(betaStatus)) {
                throw new RuntimeException("内测状态无效");
            }
            user.setBetaStatus(betaStatus);
        }
        if (update.getMcId() != null) {
            String mcId = McIdFormat.require(update.getMcId(), "Minecraft ID");
            if (!mcId.isEmpty()) {
                userRepo.findByMcId(mcId)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("该 Minecraft ID 已被其他账号绑定");
                    });
            }
            // 玩家侧同样「只能绑定一次」，管理员不受限：这里是纠错通道，每次真的改动都留日志
            String before = user.getMcId();
            if (OnceBinding.forceSet(user, OnceBinding.Field.MC_ID, mcId)) {
                log.info("管理员强制改写 Minecraft ID：用户 {}({}) {} -> {}", user.getUsername(), userId,
                        before == null ? "(未绑定)" : before, mcId.isEmpty() ? "(已解绑)" : mcId);
            }
        }
        if (update.getQq() != null) {
            String qq = update.getQq().trim();
            if (!qq.isBlank() && !qqFormat.isValid(qq)) {
                throw new RuntimeException("QQ 号格式无效");
            }
            // 玩家侧 QQ 只允许绑定一次；这里是唯一的强制改写通道（纠错用），
            // 所以每次真的改动都留一条日志，方便日后追溯是谁改的、从什么改成了什么。
            String before = user.getQq();
            if (OnceBinding.forceSet(user, OnceBinding.Field.QQ, qq)) {
                log.info("管理员强制改写 QQ：用户 {}({}) {} -> {}", user.getUsername(), userId,
                        before == null ? "(未绑定)" : before, qq.isBlank() ? "(已解绑)" : qq);
            }
        }
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepo.save(user);
        statsService.invalidatePlayerCache(user.getUsername());

        applyBetaWhitelistSideEffects(user, previousBetaStatus, previousQq, previousMcId);
        return toUserDto(user);
    }

    @Transactional
    public UserDto grantAdmin(Long userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        adminAccessService.grantAdmin(user.getUsername());
        return toUserDto(user);
    }

    @Transactional
    public UserDto revokeAdmin(Long userId) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        adminAccessService.revokeAdmin(user.getUsername());
        return toUserDto(user);
    }

    public List<BetaApplicationDto> listBetaApplications(String status) {
        String value = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        List<BetaApplication> applications = value.isBlank() || "all".equals(value)
            ? betaRepo.findAllByOrderByCreatedAtDesc()
            : betaRepo.findByStatusOrderByCreatedAtDesc(value);
        return applications.stream().map(BetaApplicationDto::from).collect(Collectors.toList());
    }

    public List<BetaPlanDto> listBetaPlans() {
        return betaPlanService.listAdminPlans();
    }

    public BetaPlanDto createBetaPlan(BetaPlanRequest request) {
        return betaPlanService.createPlan(request);
    }

    public BetaPlanDto updateBetaPlan(Long planId, BetaPlanRequest request) {
        return betaPlanService.updatePlan(planId, request);
    }

    public BetaPlanDto updateBetaPlanStatus(Long planId, String status) {
        return betaPlanService.updatePlanStatus(planId, status);
    }

    @Transactional
    public BetaManagementResponse addBetaMember(AdminBetaGrantRequest request) {
        BetaApplication application = betaService.grantBetaAccess(request);
        return sendBetaApproval(application);
    }

    @Transactional
    public BetaManagementResponse decideBetaApplication(Long applicationId, AdminBetaDecisionRequest decision) {
        String status = decision.getStatus() == null
            ? ""
            : decision.getStatus().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("approved", "denied").contains(status)) {
            throw new RuntimeException("审核结果无效");
        }

        BetaApplication application = betaRepo.findById(applicationId)
            .orElseThrow(() -> new RuntimeException("申请不存在"));
        String previousApplicationStatus = application.getStatus();
        if ("approved".equals(status)) {
            betaPlanService.assertApprovalAllowed(application.getPlanId());
        }
        application.setStatus(status);
        application = betaRepo.save(application);

        String previousUserBetaStatus = null;
        if (application.getUserId() != null) {
            User user = userRepo.findById(application.getUserId()).orElse(null);
            if (user != null) {
                previousUserBetaStatus = user.getBetaStatus();
                user.setBetaStatus(status);
                user.setUpdatedAt(LocalDateTime.now());
                userRepo.save(user);
            }
        }
        if ("approved".equals(status)) {
            // 审批即授权：白名单同步失败就让审批失败，避免「后台已批准但玩家进不去」
            requireWhitelistApplicationSync(application);
            return sendBetaApproval(application);
        }
        if ("approved".equals(previousApplicationStatus) || "approved".equals(previousUserBetaStatus)) {
            // 从「已通过」被改成别的状态 = 取消内测资格：白名单必须跟着消失，
            // 否则资格取消了玩家照样能进游戏，「取消」就成了一句空话
            revokeApplicationWhitelist(application);
        }
        return new BetaManagementResponse(BetaApplicationDto.from(application), false);
    }

    /**
     * 按「账号状态」重建客户端白名单（对账 / 修复历史数据）。
     *
     * <p>把两条不变式对全部账号重新执行一遍：</p>
     * <ol>
     *   <li>已获批的账号必须有白名单条目（缺 QQ 的记入失败明细，不阻断其它账号）</li>
     *   <li>已经不在「已通过」状态、却还留着系统同步条目的，一律清掉</li>
     * </ol>
     *
     * <p>人工录入 / 导入的条目完全不参与，既不会被删也不会被改。</p>
     */
    @Transactional
    public WhitelistSyncResult syncApprovedAccounts() {
        WhitelistSyncResult result = new WhitelistSyncResult();
        Set<String> approvedQqs = new HashSet<>();
        Set<String> approvedGameIds = new HashSet<>();

        for (User user : userRepo.findAll()) {
            if (!"approved".equals(user.getBetaStatus())) {
                continue;
            }
            result.countApprovedPlayer();
            String qq = McIdFormat.normalize(user.getQq());
            String mcId = McIdFormat.normalize(user.getMcId());
            if (!qq.isEmpty()) {
                approvedQqs.add(qq.toLowerCase(Locale.ROOT));
            }
            if (!mcId.isEmpty()) {
                approvedGameIds.add(mcId.toLowerCase(Locale.ROOT));
            }

            BetaWhitelistService.SyncResult sync = betaWhitelistService.syncApprovedPlayer(
                user.getQq(), user.getMcId(), user.getUsername(),
                "内测白名单对账重建", "system:whitelist-resync");
            switch (sync.outcome()) {
                case CREATED -> result.countCreated();
                case REFRESHED -> result.countRefreshed();
                case UNCHANGED -> result.countUnchanged();
                default -> result.addProblem(user.getUsername() + "：缺少 QQ 号或格式非法，未能同步");
            }
        }

        List<BetaWhitelist> orphans = new ArrayList<>();
        for (BetaWhitelist entry : betaWhitelistService.listAccountManagedEntries()) {
            String qq = McIdFormat.normalize(entry.getQq()).toLowerCase(Locale.ROOT);
            String bound = entry.getBoundGameId();
            boolean qqApproved = !qq.isEmpty() && approvedQqs.contains(qq);
            boolean gameIdApproved = bound != null && approvedGameIds.contains(bound.toLowerCase(Locale.ROOT));
            if (!qqApproved && !gameIdApproved) {
                orphans.add(entry);
            }
        }
        result.setRemoved(betaWhitelistService.deleteAccountManagedEntries(orphans));

        log.info("[NorthStar] 白名单对账完成：已获批 {} 人，新建 {}，重建 {}，已一致 {}，清理孤儿 {}，失败 {}",
            result.getApprovedPlayers(), result.getCreated(), result.getRefreshed(),
            result.getUnchanged(), result.getRemoved(), result.getFailed());
        return result;
    }

    // ------------------------------------------------------------------
    // 内测资格 <-> 白名单：同进同退
    // ------------------------------------------------------------------

    /**
     * 资格与白名单的双向同步出口。
     *
     * <p>两条不变式都收在这一个方法里，避免「改状态 / 改 QQ / 改游戏 ID」三条路径各写一遍
     * 而漏掉某一种组合：</p>
     * <ol>
     *   <li>账号处于「已通过」→ 白名单必须有对应条目；缺 QQ 就让整个操作失败（fail loud），
     *       不能出现「后台显示已批准、玩家进游戏却被判未通过而崩溃」</li>
     *   <li>账号从「已通过」变成别的状态 → 系统同步的条目必须消失</li>
     * </ol>
     */
    private void applyBetaWhitelistSideEffects(User user, String previousBetaStatus,
                                               String previousQq, String previousMcId) {
        if ("approved".equals(user.getBetaStatus())) {
            boolean identityChanged = !Objects.equals(previousQq, user.getQq())
                || !Objects.equals(previousMcId, user.getMcId());
            if (!"approved".equals(previousBetaStatus) || identityChanged) {
                requireWhitelistSync(user.getQq(), user.getMcId(), user.getUsername(), "管理员修改账号");
            }
            return;
        }
        if ("approved".equals(previousBetaStatus)) {
            revokePlayerWhitelist(user, previousQq, previousMcId, "管理员取消内测资格");
        }
    }

    /**
     * 取消内测资格：删掉系统同步的白名单条目并留日志。
     *
     * <p>同时传「改动前」与「改动后」的身份值——管理员可能在同一笔操作里既改了 QQ / 游戏 ID
     * 又取消了资格，只按当前值删会留下用旧 QQ 建的孤儿条目，那条记录会一直在白名单里放行。</p>
     */
    private int revokePlayerWhitelist(User user, String previousQq, String previousMcId, String reason) {
        int removed = betaWhitelistService.revokeAccountEntries(
            Stream.of(previousQq, user.getQq()).filter(Objects::nonNull).collect(Collectors.toList()),
            Stream.of(previousMcId, user.getMcId()).filter(Objects::nonNull).collect(Collectors.toList()));
        if (removed > 0) {
            log.info("[NorthStar] {}：已移除玩家 {} 的 {} 条白名单授权（来源=账号同步），人工录入的条目不受影响",
                reason, user.getUsername(), removed);
        }
        return removed;
    }

    /** 取消资格时按申请记录清理白名单（申请人还没注册账号时只能按申请上的身份清）。 */
    private int revokeApplicationWhitelist(BetaApplication application) {
        if (application.getUserId() != null) {
            User user = userRepo.findById(application.getUserId()).orElse(null);
            if (user != null) {
                return revokePlayerWhitelist(user, application.getQq(), application.getMcId(), "内测申请被拒绝");
            }
        }
        int removed = betaWhitelistService.revokeAccountEntries(
            Stream.of(application.getQq()).filter(Objects::nonNull).collect(Collectors.toList()),
            Stream.of(application.getMcId()).filter(Objects::nonNull).collect(Collectors.toList()));
        if (removed > 0) {
            log.info("[NorthStar] 内测申请被拒绝：已移除 {} 的 {} 条白名单授权（来源=账号同步）",
                application.getUsername(), removed);
        }
        return removed;
    }

    /**
     * 审批通过后把「QQ + 游戏ID」同步进白名单。
     *
     * <p>QQ 与游戏ID 优先取账号上玩家自己提交的值（注册平台要求填），
     * 账号不存在或未填时回退到申请记录上的值。</p>
     */
    private void requireWhitelistApplicationSync(BetaApplication application) {
        User user = application.getUserId() == null
            ? null
            : userRepo.findById(application.getUserId()).orElse(null);
        String qq = user != null && user.getQq() != null ? user.getQq() : application.getQq();
        String mcId = user != null && user.getMcId() != null ? user.getMcId() : application.getMcId();
        String nickname = user != null ? user.getUsername() : application.getUsername();

        BetaWhitelistService.SyncResult result = betaWhitelistService.syncApprovedPlayer(
            qq, mcId, nickname, "内测审批自动同步", "system:beta-approval");
        if (result.outcome() == BetaWhitelistService.SyncOutcome.MISSING_QQ) {
            throw new RuntimeException("该申请人尚未填写 QQ 号，无法加入内测白名单；"
                + "请先让玩家在账号上补充 QQ，或到白名单页手工添加");
        }
        if (!result.succeeded()) {
            throw new RuntimeException("QQ 号或游戏ID 格式非法，无法加入内测白名单");
        }
    }

    /** 人工操作里的白名单同步：失败必须让整个操作失败。 */
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

    public List<RoomDto> listRooms() {
        return roomRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(room -> room.getServerId() != null && room.getExternalId() != null)
            .map(RoomDto::from)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<MatchDto> listRecentMatches() {
        List<MatchRecord> records = matchRepo.findTop50ByOrderByPlayedAtDesc();
        Map<Long, String> usernames = userRepo.findAll().stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));
        return records.stream()
            .map(record -> MatchDto.from(record, usernames.getOrDefault(record.getUserId(), "未知")))
            .collect(Collectors.toList());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private UserDto toUserDto(User user) {
        UserDto dto = UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
        dto.setAdminLocked(adminAccessService.isConfiguredAdmin(user.getUsername()));
        return dto;
    }

    private BetaManagementResponse sendBetaApproval(BetaApplication application) {
        try {
            String email = application.getEmail();
            if ((email == null || email.isBlank()) && application.getUserId() != null) {
                email = userRepo.findById(application.getUserId()).map(User::getEmail).orElse("");
            }
            String displayName = application.getUsername();
            if (displayName == null || displayName.isBlank()) displayName = application.getMcId();
            if (displayName == null || displayName.isBlank()) displayName = email;
            emailService.sendBetaApprovalEmail(email, displayName);
            return new BetaManagementResponse(BetaApplicationDto.from(application), true);
        } catch (RuntimeException e) {
            return new BetaManagementResponse(BetaApplicationDto.from(application), false);
        }
    }
}
