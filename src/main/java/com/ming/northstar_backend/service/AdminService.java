package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.MatchRecord;
import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Set<String> BETA_STATUSES = Set.of("none", "pending", "approved", "denied");
    private static final Set<String> ROOM_STATUSES = Set.of("waiting", "playing", "closed");

    private final UserRepository userRepo;
    private final BetaApplicationRepository betaRepo;
    private final RoomRepository roomRepo;
    private final MatchRecordRepository matchRepo;
    private final AdminAccessService adminAccessService;
    private final StatsService statsService;
    private final BetaService betaService;
    private final EmailService emailService;

    public AdminService(UserRepository userRepo, BetaApplicationRepository betaRepo, RoomRepository roomRepo,
                        MatchRecordRepository matchRepo, AdminAccessService adminAccessService,
                        StatsService statsService, BetaService betaService, EmailService emailService) {
        this.userRepo = userRepo;
        this.betaRepo = betaRepo;
        this.roomRepo = roomRepo;
        this.matchRepo = matchRepo;
        this.adminAccessService = adminAccessService;
        this.statsService = statsService;
        this.betaService = betaService;
        this.emailService = emailService;
    }

    public AdminOverview getOverview() {
        AdminOverview overview = new AdminOverview();
        long totalUsers = userRepo.count();
        overview.setTotalUsers(totalUsers);
        overview.setPendingBetaApplications(betaRepo.countByStatus("pending"));
        overview.setActiveRooms(roomRepo.countByStatusIn(List.of("waiting", "playing")));
        overview.setTotalMatches(matchRepo.count());
        overview.setOnlinePlayers(Math.max(1, totalUsers * 3 / 10));
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
            .map(user -> UserDto.from(user, adminAccessService.roleFor(user.getUsername())))
            .collect(Collectors.toList());
    }

    @Transactional
    public UserDto updateUser(Long userId, AdminUserUpdateRequest update) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

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
            String mcId = update.getMcId().trim();
            if (!mcId.isBlank() && !mcId.matches("^[a-zA-Z0-9_]{3,16}$")) {
                throw new RuntimeException("Minecraft ID 格式无效");
            }
            if (!mcId.isBlank()) {
                userRepo.findByMcId(mcId)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("该 Minecraft ID 已被其他账号绑定");
                    });
            }
            user.setMcId(mcId.isBlank() ? null : mcId);
        }
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepo.save(user);
        statsService.invalidatePlayerCache(user.getUsername());
        return UserDto.from(user, adminAccessService.roleFor(user.getUsername()));
    }

    public List<BetaApplicationDto> listBetaApplications(String status) {
        String value = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        List<BetaApplication> applications = value.isBlank() || "all".equals(value)
            ? betaRepo.findAllByOrderByCreatedAtDesc()
            : betaRepo.findByStatusOrderByCreatedAtDesc(value);
        return applications.stream().map(BetaApplicationDto::from).collect(Collectors.toList());
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
        application.setStatus(status);
        application = betaRepo.save(application);

        if (application.getUserId() != null) {
            userRepo.findById(application.getUserId()).ifPresent(user -> {
                user.setBetaStatus(status);
                user.setUpdatedAt(LocalDateTime.now());
                userRepo.save(user);
            });
        }
        return "approved".equals(status)
            ? sendBetaApproval(application)
            : new BetaManagementResponse(BetaApplicationDto.from(application), false);
    }

    public List<RoomDto> listRooms() {
        return roomRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(RoomDto::from)
            .collect(Collectors.toList());
    }

    @Transactional
    public RoomDto updateRoomStatus(Long roomId, AdminRoomStatusRequest update) {
        String status = update.getStatus() == null
            ? ""
            : update.getStatus().trim().toLowerCase(Locale.ROOT);
        if (!ROOM_STATUSES.contains(status)) {
            throw new RuntimeException("房间状态无效");
        }
        Room room = roomRepo.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        room.setStatus(status);
        if ("closed".equals(status)) {
            room.setCurrentPlayers(0);
        }
        return RoomDto.from(roomRepo.save(room));
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomRepo.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        roomRepo.delete(room);
    }

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
