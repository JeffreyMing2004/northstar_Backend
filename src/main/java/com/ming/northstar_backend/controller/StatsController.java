package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.MatchRecord;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService statsService;
    private final MatchRecordRepository matchRepo;
    private final UserRepository userRepo;

    public StatsController(StatsService statsService, MatchRecordRepository matchRepo, UserRepository userRepo) {
        this.statsService = statsService;
        this.matchRepo = matchRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/stats/{username}")
    public ResponseEntity<ApiResponse<PlayerStats>> getPlayerStats(@PathVariable String username) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(statsService.getPlayerStats(username)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/profile/{playerId}")
    public ResponseEntity<ApiResponse<PlayerStats>> getPublicProfile(@PathVariable String playerId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(statsService.getPublicProfile(playerId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/matches/recent")
    public ResponseEntity<ApiResponse<List<MatchDto>>> getRecentMatches() {
        List<MatchRecord> records = matchRepo.findTop10ByOrderByPlayedAtDesc();
        Map<Long, String> usernameMap = userRepo.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        List<MatchDto> dtos = records.stream()
                .map(m -> MatchDto.from(m, usernameMap.getOrDefault(m.getUserId(), "未知")))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<List<LeaderboardEntry>>> getLeaderboard() {
        return ResponseEntity.ok(ApiResponse.ok(statsService.getLeaderboard()));
    }
}
