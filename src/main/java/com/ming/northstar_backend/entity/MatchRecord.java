package com.ming.northstar_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_records")
public class MatchRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long roomId;

    @Column(length = 32)
    private String mode;

    @Column(length = 32)
    private String mapName;

    private Boolean win = false;
    private Integer kills = 0;
    private Integer deaths = 0;
    private Integer assists = 0;
    private Integer scoreChange = 0;

    private LocalDateTime playedAt = LocalDateTime.now();

    public MatchRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
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
    public Integer getScoreChange() { return scoreChange; }
    public void setScoreChange(Integer scoreChange) { this.scoreChange = scoreChange; }
    public LocalDateTime getPlayedAt() { return playedAt; }
    public void setPlayedAt(LocalDateTime playedAt) { this.playedAt = playedAt; }
}
