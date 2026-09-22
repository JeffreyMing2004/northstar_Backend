package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.PlatformStats;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
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

    public PlatformController(UserRepository userRepo, RoomRepository roomRepo, MatchRecordRepository matchRepo) {
        this.userRepo = userRepo;
        this.roomRepo = roomRepo;
        this.matchRepo = matchRepo;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStats>> getPlatformStats() {
        PlatformStats stats = new PlatformStats();
        stats.setTotalPlayers(userRepo.count());
        stats.setActiveRooms(roomRepo.count());
        stats.setTotalMatches(matchRepo.count());
        // Simulate online players as ~30% of total, at least 1
        stats.setOnlinePlayers(Math.max(1, (long)(userRepo.count() * 0.3)));
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
