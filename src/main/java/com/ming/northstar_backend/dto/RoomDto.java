package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.Room;

public class RoomDto {
    private Long id;
    private String name;
    private String mode;
    private String modeKey;
    private String map;
    private String mapKey;
    private String host;
    private Integer maxPlayers;
    private Integer players;
    private Integer ping;
    private String status;

    public RoomDto() {}

    public static RoomDto from(Room r) {
        RoomDto dto = new RoomDto();
        dto.id = r.getId();
        dto.name = r.getName();
        dto.mode = r.getMode();
        dto.modeKey = r.getModeKey();
        dto.map = r.getMap();
        dto.mapKey = r.getMapKey();
        dto.host = r.getHost();
        dto.maxPlayers = r.getMaxPlayers();
        dto.players = r.getCurrentPlayers();
        dto.ping = r.getPing();
        dto.status = r.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Integer getPlayers() { return players; }
    public void setPlayers(Integer players) { this.players = players; }
    public Integer getPing() { return ping; }
    public void setPing(Integer ping) { this.ping = ping; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
