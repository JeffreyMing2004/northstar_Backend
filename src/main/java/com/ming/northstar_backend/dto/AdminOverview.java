package com.ming.northstar_backend.dto;

public class AdminOverview {
    private long totalUsers;
    private long pendingBetaApplications;
    private long activeRooms;
    private long totalMatches;
    private long onlinePlayers;
    private long adminCount;
    private int maxAdmins;

    public AdminOverview() {}

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
    public long getPendingBetaApplications() { return pendingBetaApplications; }
    public void setPendingBetaApplications(long pendingBetaApplications) { this.pendingBetaApplications = pendingBetaApplications; }
    public long getActiveRooms() { return activeRooms; }
    public void setActiveRooms(long activeRooms) { this.activeRooms = activeRooms; }
    public long getTotalMatches() { return totalMatches; }
    public void setTotalMatches(long totalMatches) { this.totalMatches = totalMatches; }
    public long getOnlinePlayers() { return onlinePlayers; }
    public void setOnlinePlayers(long onlinePlayers) { this.onlinePlayers = onlinePlayers; }
    public long getAdminCount() { return adminCount; }
    public void setAdminCount(long adminCount) { this.adminCount = adminCount; }
    public int getMaxAdmins() { return maxAdmins; }
    public void setMaxAdmins(int maxAdmins) { this.maxAdmins = maxAdmins; }
}
