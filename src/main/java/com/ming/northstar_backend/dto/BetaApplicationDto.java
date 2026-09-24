package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BetaApplication;

import java.time.format.DateTimeFormatter;

public class BetaApplicationDto {
    private Long id;
    private Long userId;
    private Long planId;
    private String username;
    private String email;
    private String mcId;
    private String reason;
    private String status;
    private String createdAt;

    public BetaApplicationDto() {}

    public static BetaApplicationDto from(BetaApplication application) {
        BetaApplicationDto dto = new BetaApplicationDto();
        dto.id = application.getId();
        dto.userId = application.getUserId();
        dto.planId = application.getPlanId();
        dto.username = application.getUsername();
        dto.email = application.getEmail();
        dto.mcId = application.getMcId();
        dto.reason = application.getReason();
        dto.status = application.getStatus();
        dto.createdAt = application.getCreatedAt() == null
            ? ""
            : application.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
