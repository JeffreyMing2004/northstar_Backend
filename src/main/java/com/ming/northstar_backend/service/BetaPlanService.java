package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BetaPlanDto;
import com.ming.northstar_backend.dto.BetaPlanRequest;
import com.ming.northstar_backend.entity.BetaPlan;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.BetaPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BetaPlanService {
    private static final Set<String> STATUSES = Set.of("draft", "active", "paused", "completed");

    private final BetaPlanRepository planRepo;
    private final BetaApplicationRepository applicationRepo;

    public BetaPlanService(BetaPlanRepository planRepo, BetaApplicationRepository applicationRepo) {
        this.planRepo = planRepo;
        this.applicationRepo = applicationRepo;
    }

    public List<BetaPlanDto> listAdminPlans() {
        return planRepo.findAllByOrderByStartsOnAsc().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public List<BetaPlanDto> listPublicPlans() {
        return planRepo.findByStatusInOrderByStartsOnAsc(List.of("active", "paused", "completed")).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public BetaPlanDto createPlan(BetaPlanRequest request) {
        BetaPlan plan = new BetaPlan();
        apply(plan, request);
        plan.setStatus("draft");
        return toDto(planRepo.save(plan));
    }

    @Transactional
    public BetaPlanDto updatePlan(Long planId, BetaPlanRequest request) {
        BetaPlan plan = requirePlan(planId);
        apply(plan, request);
        if (applicationRepo.countByPlanIdAndStatus(plan.getId(), "approved") > plan.getCapacity()) {
            throw new RuntimeException("计划名额不能低于已通过人数");
        }
        plan.setUpdatedAt(LocalDateTime.now());
        return toDto(planRepo.save(plan));
    }

    @Transactional
    public BetaPlanDto updatePlanStatus(Long planId, String status) {
        String value = normalize(status);
        if (!STATUSES.contains(value)) {
            throw new RuntimeException("内测计划状态无效");
        }
        BetaPlan plan = requirePlan(planId);
        plan.setStatus(value);
        plan.setUpdatedAt(LocalDateTime.now());
        return toDto(planRepo.save(plan));
    }

    public BetaPlan requirePlan(Long planId) {
        if (planId == null) return null;
        BetaPlan plan = findPlan(planId);
        if (plan == null) {
            throw new RuntimeException("内测计划不存在");
        }
        return plan;
    }

    /**
     * 查计划，查不到返回 {@code null}。
     *
     * <p>专供「展示历史申请」这类场景：计划的 id 是历史快照，运营把计划删掉之后
     * 玩家的申请记录还在，这时不能因为计划找不到就把整个查询接口炸成 500。</p>
     */
    public BetaPlan findPlan(Long planId) {
        return planId == null ? null : planRepo.findById(planId).orElse(null);
    }

    public BetaPlan requireOpenPlan(Long planId) {
        BetaPlan plan = requirePlan(planId);
        if (plan == null || !"active".equals(plan.getStatus())) {
            throw new RuntimeException("该内测计划当前未开放申请");
        }
        LocalDate today = LocalDate.now();
        if (plan.getStartsOn() != null && today.isBefore(plan.getStartsOn())) {
            throw new RuntimeException("该内测计划尚未开始");
        }
        if (plan.getEndsOn() != null && today.isAfter(plan.getEndsOn())) {
            throw new RuntimeException("该内测计划已结束");
        }
        assertCapacity(plan);
        return plan;
    }

    public BetaPlan requireAssignablePlan(Long planId) {
        BetaPlan plan = requirePlan(planId);
        if (plan == null || "completed".equals(plan.getStatus())) {
            throw new RuntimeException("请选择有效的内测计划");
        }
        assertCapacity(plan);
        return plan;
    }

    public void assertApprovalAllowed(Long planId) {
        BetaPlan plan = requirePlan(planId);
        if (plan == null) return;
        if (!"active".equals(plan.getStatus())) {
            throw new RuntimeException("该内测计划当前未开放审核");
        }
        assertCapacity(plan);
    }

    private void assertCapacity(BetaPlan plan) {
        long approved = applicationRepo.countByPlanIdAndStatus(plan.getId(), "approved");
        if (approved >= plan.getCapacity()) {
            throw new RuntimeException("该内测计划名额已满");
        }
    }

    private void apply(BetaPlan plan, BetaPlanRequest request) {
        String name = trim(request == null ? null : request.getName());
        String phase = trim(request == null ? null : request.getPhase());
        String description = request == null || request.getDescription() == null ? "" : request.getDescription().trim();
        String modes = trim(request == null ? null : request.getAllowedModes());
        if (name.isBlank() || name.length() > 64) {
            throw new RuntimeException("计划名称需为1-64个字符");
        }
        if (phase.isBlank() || phase.length() > 16) {
            throw new RuntimeException("计划阶段需为1-16个字符");
        }
        if (description.length() > 500) {
            throw new RuntimeException("计划说明不能超过500个字符");
        }
        if (modes.length() > 128) {
            throw new RuntimeException("可体验模式不能超过128个字符");
        }
        if (request.getCapacity() == null || request.getCapacity() < 1 || request.getCapacity() > 100000) {
            throw new RuntimeException("计划名额需在1-100000之间");
        }
        if (request.getStartsOn() != null && request.getEndsOn() != null
            && request.getEndsOn().isBefore(request.getStartsOn())) {
            throw new RuntimeException("结束日期不能早于开始日期");
        }
        plan.setName(name);
        plan.setPhase(phase.toUpperCase(Locale.ROOT));
        plan.setDescription(description);
        plan.setStartsOn(request.getStartsOn());
        plan.setEndsOn(request.getEndsOn());
        plan.setCapacity(request.getCapacity());
        plan.setAllowedModes(modes);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    private BetaPlanDto toDto(BetaPlan plan) {
        return BetaPlanDto.from(plan, applicationRepo.countByPlanIdAndStatus(plan.getId(), "approved"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
