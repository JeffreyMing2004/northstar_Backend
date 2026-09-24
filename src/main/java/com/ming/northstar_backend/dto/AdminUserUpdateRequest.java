package com.ming.northstar_backend.dto;

public class AdminUserUpdateRequest {
    private String qq;
    private String mcId;
    private String rank;
    private Integer score;
    private String betaStatus;

    public AdminUserUpdateRequest() {}

    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getBetaStatus() { return betaStatus; }
    public void setBetaStatus(String betaStatus) { this.betaStatus = betaStatus; }
}
