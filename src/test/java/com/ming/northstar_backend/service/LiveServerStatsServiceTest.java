package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.ServerStatsReport;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveServerStatsServiceTest {

    @Test
    void aggregatesFreshReportsAcrossServers() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
        LiveServerStatsService service = new LiveServerStatsService(90, clock);

        service.update(report("lobby", 3, 1));
        clock.advanceSeconds(30);
        service.update(report("arena", 5, 2));

        assertEquals(8, service.getOnlinePlayers());
        assertEquals(3, service.getActiveRooms());
        assertEquals(2, service.getConnectedServers());
    }

    @Test
    void excludesReportsOlderThanMaxAge() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
        LiveServerStatsService service = new LiveServerStatsService(90, clock);

        service.update(report("lobby", 3, 1));
        clock.advanceSeconds(91);

        assertEquals(0, service.getOnlinePlayers());
        assertEquals(0, service.getActiveRooms());
        assertEquals(0, service.getConnectedServers());
    }

    private ServerStatsReport report(String serverId, int onlinePlayers, int activeRooms) {
        ServerStatsReport report = new ServerStatsReport();
        report.setServerId(serverId);
        report.setOnlinePlayers(onlinePlayers);
        report.setActiveRooms(activeRooms);
        report.setMaxPlayers(20);
        return report;
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        private void advanceSeconds(long seconds) {
            currentInstant = currentInstant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
