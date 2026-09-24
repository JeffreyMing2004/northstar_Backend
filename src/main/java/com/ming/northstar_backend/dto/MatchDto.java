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
        int kills = valueOrZero(m.getKills());
        int deaths = valueOrZero(m.getDeaths());
        dto.id = m.getId();
        dto.username = username;
        dto.mode = m.getMode();
        dto.mapName = m.getMapName();
        dto.win = Boolean.TRUE.equals(m.getWin());
        dto.kills = kills;
        dto.deaths = deaths;
        dto.assists = valueOrZero(m.getAssists());
        dto.scoreChange = valueOrZero(m.getScoreChange());
        dto.kd = deaths == 0 ? String.valueOf(kills) : String.format("%.2f", kills * 1.0 / deaths);
        dto.playedAt = m.getPlayedAt() != null ? m.getPlayedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
        return dto;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
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
