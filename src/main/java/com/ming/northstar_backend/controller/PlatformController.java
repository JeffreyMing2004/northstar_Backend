package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.PlatformStats;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.service.LiveServerStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final UserRepository userRepo;
    private final RoomRepository roomRepo;
    private final MatchRecordRepository matchRepo;
    private final LiveServerStatsService serverStatsService;

    public PlatformController(UserRepository userRepo, RoomRepository roomRepo, MatchRecordRepository matchRepo,
                              LiveServerStatsService serverStatsService) {
        this.userRepo = userRepo;
        this.roomRepo = roomRepo;
        this.matchRepo = matchRepo;
        this.serverStatsService = serverStatsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStats>> getPlatformStats() {
        PlatformStats stats = new PlatformStats();
        stats.setTotalPlayers(userRepo.count());
        stats.setActiveRooms(serverStatsService.getActiveRooms());
        stats.setTotalMatches(matchRepo.count());
        stats.setOnlinePlayers(serverStatsService.getOnlinePlayers());
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
