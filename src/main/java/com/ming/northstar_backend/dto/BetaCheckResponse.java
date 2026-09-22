package com.ming.northstar_backend.dto;

public class BetaCheckResponse {
    private String username;
    private String betaStatus;
    private String type;
    private String date;
    private String expire;
    private String modes;

    public BetaCheckResponse() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getBetaStatus() { return betaStatus; }
    public void setBetaStatus(String betaStatus) { this.betaStatus = betaStatus; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getExpire() { return expire; }
    public void setExpire(String expire) { this.expire = expire; }
    public String getModes() { return modes; }
    public void setModes(String modes) { this.modes = modes; }
}
