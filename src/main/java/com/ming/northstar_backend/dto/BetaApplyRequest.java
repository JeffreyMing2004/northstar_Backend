package com.ming.northstar_backend.dto;

public class BetaApplyRequest {
    private String query;
    private String email;
    private String reason;

    public BetaApplyRequest() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
