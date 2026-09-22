package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.MatchRecord;
import java.time.format.DateTimeFormatter;

public class MatchDto {
    private Long id;
    private String username;
    private String mode;
    private String mapName;
    private Boolean win;
    private Integer kills;
    private Integer deaths;
    private Integer assists;
    private String kd;
    private Integer scoreChange;
    private String playedAt;

    public MatchDto() {}

    public static MatchDto from(MatchRecord m, String username) {
        MatchDto dto = new MatchDto();
        dto.id = m.getId();
        dto.username = username;
        dto.mode = m.getMode();
        dto.mapName = m.getMapName();
        dto.win = m.getWin();
        dto.kills = m.getKills();
        dto.deaths = m.getDeaths();
        dto.assists = m.getAssists();
        dto.scoreChange = m.getScoreChange();
        dto.kd = m.getDeaths() == 0 ? String.valueOf(m.getKills()) : String.format("%.2f", m.getKills() * 1.0 / m.getDeaths());
        dto.playedAt = m.getPlayedAt() != null ? m.getPlayedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }
    public Boolean getWin() { return win; }
    public void setWin(Boolean win) { this.win = win; }
    public Integer getKills() { return kills; }
    public void setKills(Integer kills) { this.kills = kills; }
    public Integer getDeaths() { return deaths; }
    public void setDeaths(Integer deaths) { this.deaths = deaths; }
    public Integer getAssists() { return assists; }
    public void setAssists(Integer assists) { this.assists = assists; }
    public String getKd() { return kd; }
    public void setKd(String kd) { this.kd = kd; }
    public Integer getScoreChange() { return scoreChange; }
    public void setScoreChange(Integer scoreChange) { this.scoreChange = scoreChange; }
    public String getPlayedAt() { return playedAt; }
    public void setPlayedAt(String playedAt) { this.playedAt = playedAt; }
}