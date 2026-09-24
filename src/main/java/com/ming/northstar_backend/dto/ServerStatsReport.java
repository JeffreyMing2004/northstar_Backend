package com.ming.northstar_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;

public class ServerStatsReport {
    @NotBlank
    @Size(max = 64)
    private String serverId;

    @Min(0)
    private int onlinePlayers;

    @Min(0)
    private int activeRooms;

    @Min(0)
    private int maxPlayers;

    @Valid
    @NotNull
    @Size(max = 200)
    private List<BridgeRoomReport> rooms = List.of();

    public ServerStatsReport() {
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    public void setOnlinePlayers(int onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    public int getActiveRooms() {
        return activeRooms;
    }

    public void setActiveRooms(int activeRooms) {
        this.activeRooms = activeRooms;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public List<BridgeRoomReport> getRooms() {
        return rooms;
    }

    public void setRooms(List<BridgeRoomReport> rooms) {
        this.rooms = rooms;
    }
}
