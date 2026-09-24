package com.ming.northstar_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class BridgeRoomReport {
    @NotBlank
    @Size(max = 64)
    private String externalId;

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 32)
    private String mode;

    @Size(max = 32)
    private String modeKey;

    @Size(max = 32)
    private String map;

    @Size(max = 32)
    private String mapKey;

    @Size(max = 32)
    private String host;

    @Min(1)
    @Max(1000)
    private int maxPlayers;

    @Min(0)
    private int currentPlayers;

    @Min(0)
    @Max(10000)
    private int ping;

    @NotBlank
    @Pattern(regexp = "waiting|playing|closed")
    private String status;

    public BridgeRoomReport() {
    }

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
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public int getCurrentPlayers() { return currentPlayers; }
    public void setCurrentPlayers(int currentPlayers) { this.currentPlayers = currentPlayers; }
    public int getPing() { return ping; }
    public void setPing(int ping) { this.ping = ping; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
