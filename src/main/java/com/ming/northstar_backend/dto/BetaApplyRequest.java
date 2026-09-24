package com.ming.northstar_backend.dto;

public class BetaApplyRequest {
    /**
     * 旧版前端会带上查询用的账号标识。
     *
     * <p><b>服务端已忽略此字段</b>：申请身份一律取登录账号（见
     * {@code BetaService#applyForBeta}），保留只是为了兼容仍在使用的旧前端包，
     * 新调用方不必再传。</p>
     */
    @Deprecated
    private String query;
    private String email;
    private String reason;
    private Long planId;

    public BetaApplyRequest() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
}
