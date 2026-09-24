package com.ming.northstar_backend.dto;

public class AdminBetaGrantRequest {
    private String email;
    private String username;
    /** 玩家 QQ 号；后台直接发放时由管理员填写，未填则回退账号上的 QQ。 */
    private String qq;
    private String mcId;
    private String reason;
    private Long planId;

    public AdminBetaGrantRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
}
