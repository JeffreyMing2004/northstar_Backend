package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.User;

import java.time.LocalDateTime;

public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String qq;
    private LocalDateTime qqBoundAt;
    private String mcId;
    private LocalDateTime mcIdBoundAt;
    private String rank;
    private Integer score;
    private Integer wins;
    private Integer losses;
    private Integer totalKills;
    private Integer totalDeaths;
    private String betaStatus;
    private String role;
    /**
     * 客户端白名单状态：{@code active} / {@code disabled} / {@code expired} / {@code missing}。
     *
     * <p>只有「当前登录用户自己」的接口会填充（账号设置页要显示）；
     * 后台玩家列表刻意不填，避免为每个用户都去查一次白名单。</p>
     */
    private String whitelistStatus;
    /** 白名单条目的到期时间；null 表示长期有效或没有条目。 */
    private LocalDateTime whitelistExpireAt;
    private boolean adminLocked;
    private LocalDateTime createdAt;

    public UserDto() {}

    public static UserDto from(User u) {
        return from(u, "user");
    }

    public static UserDto from(User u, String role) {
        UserDto dto = new UserDto();
        dto.id = u.getId();
        dto.username = u.getUsername();
        dto.email = u.getEmail();
        dto.qq = u.getQq();
        dto.qqBoundAt = u.getQqBoundAt();
        dto.mcId = u.getMcId();
        dto.mcIdBoundAt = u.getMcIdBoundAt();
        dto.rank = u.getRank();
        dto.score = u.getScore();
        dto.wins = u.getWins();
        dto.losses = u.getLosses();
        dto.totalKills = u.getTotalKills();
        dto.totalDeaths = u.getTotalDeaths();
        dto.betaStatus = u.getBetaStatus();
        dto.role = role;
        dto.createdAt = u.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public LocalDateTime getQqBoundAt() { return qqBoundAt; }
    public void setQqBoundAt(LocalDateTime qqBoundAt) { this.qqBoundAt = qqBoundAt; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public LocalDateTime getMcIdBoundAt() { return mcIdBoundAt; }
    public void setMcIdBoundAt(LocalDateTime mcIdBoundAt) { this.mcIdBoundAt = mcIdBoundAt; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getWins() { return wins; }
    public void setWins(Integer wins) { this.wins = wins; }
    public Integer getLosses() { return losses; }
    public void setLosses(Integer losses) { this.losses = losses; }
    public Integer getTotalKills() { return totalKills; }
    public void setTotalKills(Integer totalKills) { this.totalKills = totalKills; }
    public Integer getTotalDeaths() { return totalDeaths; }
    public void setTotalDeaths(Integer totalDeaths) { this.totalDeaths = totalDeaths; }
    public String getBetaStatus() { return betaStatus; }
    public void setBetaStatus(String betaStatus) { this.betaStatus = betaStatus; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getWhitelistStatus() { return whitelistStatus; }
    public void setWhitelistStatus(String whitelistStatus) { this.whitelistStatus = whitelistStatus; }
    public LocalDateTime getWhitelistExpireAt() { return whitelistExpireAt; }
    public void setWhitelistExpireAt(LocalDateTime whitelistExpireAt) { this.whitelistExpireAt = whitelistExpireAt; }
    public boolean isAdminLocked() { return adminLocked; }
    public void setAdminLocked(boolean adminLocked) { this.adminLocked = adminLocked; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
