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

    @Column(length = 64)
    private String mcId;

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
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
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
