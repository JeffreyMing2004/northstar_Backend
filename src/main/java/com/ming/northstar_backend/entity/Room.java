package com.ming.northstar_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rooms_server_external", columnNames = {"server_id", "external_id"})
})
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String serverId;

    @Column(length = 64)
    private String externalId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 32)
    private String mode = "团队死斗";

    @Column(length = 32)
    private String modeKey = "tdm";

    @Column(length = 32)
    private String map = "沙漠要塞";

    @Column(length = 32)
    private String mapKey = "desert";

    @Column(length = 32)
    private String host;

    private Integer maxPlayers = 16;
    private Integer currentPlayers = 0;
    private Integer ping = 20;

    @Column(length = 16)
    private String status = "waiting";

    private Long hostUserId;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Room() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getModeKey() { return modeKey; }
    public void setModeKey(String modeKey) { this.modeKey = modeKey; }
    public String getMap() { return map; }
    public void setMap(String map) { this.map = map; }
    public String getMapKey() { return mapKey; }
    public void setMapKey(String mapKey) { this.mapKey = mapKey; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
    public Integer getCurrentPlayers() { return currentPlayers; }
    public void setCurrentPlayers(Integer currentPlayers) { this.currentPlayers = currentPlayers; }
    public Integer getPing() { return ping; }
    public void setPing(Integer ping) { this.ping = ping; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHostUserId() { return hostUserId; }
    public void setHostUserId(Long hostUserId) { this.hostUserId = hostUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
