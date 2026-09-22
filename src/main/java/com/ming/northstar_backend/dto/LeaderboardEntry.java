package com.ming.northstar_backend.dto;

public class LeaderboardEntry {
    private Long id;
    private String name;
    private String playerId;
    private Integer score;
    private Integer winRate;
    private String kd;
    private Integer games;

    public LeaderboardEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getWinRate() { return winRate; }
    public void setWinRate(Integer winRate) { this.winRate = winRate; }
    public String getKd() { return kd; }
    public void setKd(String kd) { this.kd = kd; }
    public Integer getGames() { return games; }
    public void setGames(Integer games) { this.games = games; }
}
