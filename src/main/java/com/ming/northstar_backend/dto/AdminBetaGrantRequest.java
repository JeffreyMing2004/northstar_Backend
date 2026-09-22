package com.ming.northstar_backend.dto;

public class AdminBetaGrantRequest {
    private String email;
    private String username;
    private String mcId;
    private String reason;

    public AdminBetaGrantRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
