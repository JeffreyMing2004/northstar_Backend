package com.ming.northstar_backend.dto;

import java.util.List;

public class PlayerStats {
    private Long id;
    private String name;
    private String mcId;
    private Integer score;
    private String rank;
    private String season;
    private Integer totalKills;
    private Integer totalDeaths;
    private String kd;
    private Integer winRate;
    private Integer totalGames;
    private Integer wins;
    private Integer losses;
    private Integer highestKills;
    private List<MatchDto> recentMatches;

    public PlayerStats() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public Integer getTotalKills() { return totalKills; }
    public void setTotalKills(Integer totalKills) { this.totalKills = totalKills; }
    public Integer getTotalDeaths() { return totalDeaths; }
    public void setTotalDeaths(Integer totalDeaths) { this.totalDeaths = totalDeaths; }
    public String getKd() { return kd; }
    public void setKd(String kd) { this.kd = kd; }
    public Integer getWinRate() { return winRate; }
    public void setWinRate(Integer winRate) { this.winRate = winRate; }
    public Integer getTotalGames() { return totalGames; }
    public void setTotalGames(Integer totalGames) { this.totalGames = totalGames; }
    public Integer getWins() { return wins; }
    public void setWins(Integer wins) { this.wins = wins; }
    public Integer getLosses() { return losses; }
    public void setLosses(Integer losses) { this.losses = losses; }
    public Integer getHighestKills() { return highestKills; }
    public void setHighestKills(Integer highestKills) { this.highestKills = highestKills; }
    public List<MatchDto> getRecentMatches() { return recentMatches; }
    public void setRecentMatches(List<MatchDto> recentMatches) { this.recentMatches = recentMatches; }
}
