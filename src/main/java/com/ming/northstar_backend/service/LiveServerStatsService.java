package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.ServerStatsReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveServerStatsService {
    private final Map<String, ServerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final long maxAgeMillis;
    private final Clock clock;

    public LiveServerStatsService(
            @Value("${northstar.bridge.stats-max-age-seconds:90}") long maxAgeSeconds,
            Clock clock) {
        this.maxAgeMillis = Math.max(1, maxAgeSeconds) * 1000L;
        this.clock = clock;
    }

    public void update(ServerStatsReport report) {
        snapshots.put(report.getServerId().trim(), new ServerSnapshot(
                report.getOnlinePlayers(),
                report.getActiveRooms(),
                report.getMaxPlayers(),
                Instant.now(clock).toEpochMilli()
        ));
    }

    public long getOnlinePlayers() {
        return freshSnapshots().stream().mapToLong(ServerSnapshot::onlinePlayers).sum();
    }

    public long getActiveRooms() {
        return freshSnapshots().stream().mapToLong(ServerSnapshot::activeRooms).sum();
    }

    public int getConnectedServers() {
        return freshSnapshots().size();
    }

    private java.util.List<ServerSnapshot> freshSnapshots() {
        long oldestAccepted = Instant.now(clock).toEpochMilli() - maxAgeMillis;
        snapshots.entrySet().removeIf(entry -> entry.getValue().reportedAt() < oldestAccepted);
        return snapshots.values().stream().toList();
    }

    private record ServerSnapshot(int onlinePlayers, int activeRooms, int maxPlayers, long reportedAt) {
    }
}
