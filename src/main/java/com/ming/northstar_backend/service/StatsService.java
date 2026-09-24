package com.ming.northstar_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.MatchRecord;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final UserRepository userRepo;
    private final MatchRecordRepository matchRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String LB_KEY = "northstar:leaderboard";
    private static final String STATS_PREFIX = "northstar:stats:";
    private static final long CACHE_TTL = 60;

    public StatsService(UserRepository userRepo, MatchRecordRepository matchRepo, StringRedisTemplate redis) {
        this.userRepo = userRepo;
        this.matchRepo = matchRepo;
        this.redis = redis;
    }

    public PlayerStats getPlayerStats(String username) {
        String cached = redis.opsForValue().get(STATS_PREFIX + username);
        if (cached != null) {
            try { return mapper.readValue(cached, PlayerStats.class); } catch (Exception ignored) {}
        }

        User user = userRepo.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("玩家不存在"));

        int totalGames = user.getWins() + user.getLosses();
        int winRate = totalGames > 0 ? (int)(user.getWins() * 100.0 / totalGames) : 0;
        String kd = user.getTotalDeaths() > 0
            ? String.format("%.2f", user.getTotalKills() * 1.0 / user.getTotalDeaths())
            : String.valueOf(user.getTotalKills());

        List<MatchRecord> matches = matchRepo.findTop8ByUserIdOrderByPlayedAtDesc(user.getId());

        PlayerStats stats = new PlayerStats();
        stats.setId(user.getId());
        stats.setName(user.getUsername());
        stats.setMcId(user.getMcId());
        stats.setScore(user.getScore());
        stats.setRank(user.getRank());
        stats.setSeason("S12");
        stats.setTotalKills(user.getTotalKills());
        stats.setTotalDeaths(user.getTotalDeaths());
        stats.setKd(kd);
        stats.setWinRate(winRate);
        stats.setTotalGames(totalGames);
        stats.setWins(user.getWins());
        stats.setLosses(user.getLosses());
        stats.setHighestKills(matchRepo.maxKillsByUserId(user.getId()));
        stats.setRecentMatches(matches.stream()
                .map(m -> MatchDto.from(m, user.getUsername()))
                .collect(Collectors.toList()));

        try {
            redis.opsForValue().set(STATS_PREFIX + username, mapper.writeValueAsString(stats), Duration.ofSeconds(CACHE_TTL));
        } catch (Exception ignored) {}

        return stats;
    }

    public PlayerStats getPublicProfile(String playerId) {
        User user = resolvePlayer(playerId)
            .orElseThrow(() -> new RuntimeException("玩家不存在"));
        return getPlayerStats(user.getUsername());
    }

    public void invalidatePlayerCache(String username) {
        try {
            redis.delete(List.of(LB_KEY, STATS_PREFIX + username));
        } catch (Exception ignored) {}
    }

    private java.util.Optional<User> resolvePlayer(String playerId) {
        String value = playerId == null ? "" : playerId.trim();
        java.util.Optional<User> byUsername = userRepo.findByUsername(value);
        if (byUsername.isPresent()) return byUsername;

        java.util.Optional<User> byMcId = userRepo.findByMcId(value);
        if (byMcId.isPresent()) return byMcId;

        if (value.matches("\\d+")) {
            try {
                return userRepo.findById(Long.parseLong(value));
            } catch (NumberFormatException ignored) {}
        }

        return java.util.Optional.empty();
    }

    public List<LeaderboardEntry> getLeaderboard() {
        String cached = redis.opsForValue().get(LB_KEY);
        if (cached != null) {
            try { return mapper.readValue(cached, new TypeReference<List<LeaderboardEntry>>() {}); } catch (Exception ignored) {}
        }

        List<User> users = userRepo.findByOrderByScoreDesc();
        List<LeaderboardEntry> entries = users.stream().map(u -> {
            LeaderboardEntry e = new LeaderboardEntry();
            e.setId(u.getId());
            e.setName(u.getUsername());
            e.setPlayerId(u.getMcId() == null || u.getMcId().isBlank() ? u.getUsername() : u.getMcId());
            e.setScore(u.getScore());
            int total = u.getWins() + u.getLosses();
            e.setWinRate(total > 0 ? (int)(u.getWins() * 100.0 / total) : 0);
            e.setKd(u.getTotalDeaths() > 0
                ? String.format("%.2f", u.getTotalKills() * 1.0 / u.getTotalDeaths())
                : String.valueOf(u.getTotalKills()));
            e.setGames(total);
            return e;
        }).collect(Collectors.toList());

        try {
            redis.opsForValue().set(LB_KEY, mapper.writeValueAsString(entries), Duration.ofSeconds(CACHE_TTL));
        } catch (Exception ignored) {}

        return entries;
    }
}
