package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.BetaPlan;

import java.time.format.DateTimeFormatter;

/**
 * 「我提交的内测申请」当前状态。
 *
 * <p>为什么需要单独一个接口：内测申请必须用平台账号登录后提交，申请记录直接挂在
 * {@code users.id} 上。前端如果不查一次，就不知道当前登录的人是不是已经申请过，
 * 只能把表单一直摆在那里——玩家填完提交，拿到的是一句「你已提交过申请，请等待审核」，
 * 看起来像是提交失败。有了这个接口，前端可以把表单换成一张状态卡。</p>
 */
public class MyBetaApplicationDto {

    /** 是否已提交过申请；为 {@code false} 时其余字段无意义。 */
    private boolean hasApplication;

    private Long id;
    /** pending / approved / denied。 */
    private String status;
    private Long planId;
    /** 计划名称与阶段；计划已被运营删除时为 null。 */
    private String planName;
    private String planPhase;
    private String email;
    /** 申请理由代码，前端负责翻译成中文文案。 */
    private String reason;
    private String createdAt;

    /**
     * 该账号当前的内测资格（none / pending / approved / denied）。
     *
     * <p>与 {@link #status} 刻意分开：申请状态是某一轮的历史，资格状态是账号当下的结果。
     * 管理员可以在没有申请记录的情况下直接把资格发给某个玩家。</p>
     */
    private String betaStatus;

    public MyBetaApplicationDto() {}

    public static MyBetaApplicationDto none(String betaStatus) {
        MyBetaApplicationDto dto = new MyBetaApplicationDto();
        dto.hasApplication = false;
        dto.betaStatus = betaStatus;
        return dto;
    }

    public static MyBetaApplicationDto from(BetaApplication application, BetaPlan plan, String betaStatus) {
        MyBetaApplicationDto dto = new MyBetaApplicationDto();
        dto.hasApplication = true;
        dto.id = application.getId();
        dto.status = application.getStatus();
        dto.planId = application.getPlanId();
        dto.planName = plan == null ? null : plan.getName();
        dto.planPhase = plan == null ? null : plan.getPhase();
        dto.email = application.getEmail();
        dto.reason = application.getReason();
        dto.createdAt = application.getCreatedAt() == null
            ? ""
            : application.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        dto.betaStatus = betaStatus;
        return dto;
    }

    public boolean isHasApplication() { return hasApplication; }
    public void setHasApplication(boolean hasApplication) { this.hasApplication = hasApplication; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getPlanPhase() { return planPhase; }
    public void setPlanPhase(String planPhase) { this.planPhase = planPhase; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getBetaStatus() { return betaStatus; }
    public void setBetaStatus(String betaStatus) { this.betaStatus = betaStatus; }
}
