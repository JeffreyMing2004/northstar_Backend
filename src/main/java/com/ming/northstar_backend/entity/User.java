package com.ming.northstar_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, length = 128)
    private String email;

    /**
     * 玩家 QQ 号。内测白名单以「QQ + 游戏ID」为键，注册时由玩家提交，
     * 审批通过后由系统同步成白名单条目。
     *
     * <p>该字段一个账号只允许绑定一次，绑定后玩家不能自行修改
     * （判定见 {@link com.ming.northstar_backend.support.OnceBinding}）。
     * 只有管理员出于纠错目的才能强制改写。</p>
     */
    @Column(length = 16)
    private String qq;

    /** QQ 首次绑定的时间；为 null 表示尚未绑定。 */
    private LocalDateTime qqBoundAt;

    /**
     * 玩家提交的离线服游戏 ID（Minecraft ID）。与 QQ 同为内测资格凭据。
     *
     * <p>注册时即完成绑定，之后玩家不能自行更改（判定见
     * {@link com.ming.northstar_backend.support.OnceBinding}），只有管理员能强制改写。
     * 原因与 QQ 相同：白名单以「QQ + 游戏ID」为键，允许改绑等于允许把内测资格转手。</p>
     */
    @Column(length = 64)
    private String mcId;

    /** Minecraft ID 首次绑定的时间；为 null 表示尚未绑定。 */
    private LocalDateTime mcIdBoundAt;

    @Column(length = 32)
    private String rank = "青铜I";

    private Integer score = 1000;
    private Integer wins = 0;
    private Integer losses = 0;
    private Integer totalKills = 0;
    private Integer totalDeaths = 0;

    @Column(length = 16)
    private String betaStatus = "none";

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
