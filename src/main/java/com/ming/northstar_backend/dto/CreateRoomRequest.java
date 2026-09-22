package com.ming.northstar_backend.dto;

public class CreateRoomRequest {
    private String name;
    private String mode;
    private String modeKey;
    private String map;
    private String mapKey;
    private Integer maxPlayers = 16;
    public CreateRoomRequest() {}
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
    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
}
