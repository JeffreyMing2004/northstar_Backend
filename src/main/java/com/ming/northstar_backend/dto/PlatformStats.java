package com.ming.northstar_backend.dto;

public class PlatformStats {
    private long totalPlayers;
    private long onlinePlayers;
    private long activeRooms;
    private long totalMatches;

    public PlatformStats() {}

    public long getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(long totalPlayers) { this.totalPlayers = totalPlayers; }
    public long getOnlinePlayers() { return onlinePlayers; }
    public void setOnlinePlayers(long onlinePlayers) { this.onlinePlayers = onlinePlayers; }
    public long getActiveRooms() { return activeRooms; }
    public void setActiveRooms(long activeRooms) { this.activeRooms = activeRooms; }
    public long getTotalMatches() { return totalMatches; }
    public void setTotalMatches(long totalMatches) { this.totalMatches = totalMatches; }
}
