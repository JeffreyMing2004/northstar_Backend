package com.ming.northstar_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.dto.MinecraftAvatarDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class MinecraftAvatarService {

    private static final String CACHE_PREFIX = "northstar:minecraft-avatar:";
    private static final String PROFILE_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String AVATAR_CDN = "https://mc-heads.net/avatar/";
    private static final long CACHE_TTL_HOURS = 24;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public MinecraftAvatarService(StringRedisTemplate redis) {
        this.redis = redis;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    public MinecraftAvatarDto resolve(String playerId) {
        String value = playerId == null ? "" : playerId.trim();
        String cacheKey = CACHE_PREFIX + value.toLowerCase(Locale.ROOT);

        String cached = null;
        try {
            cached = redis.opsForValue().get(cacheKey);
        } catch (Exception ignored) {}
        if (cached != null) {
            try {
                return mapper.readValue(cached, MinecraftAvatarDto.class);
            } catch (Exception ignored) {}
        }

        String premiumUuid = lookupPremiumUuid(value);
        boolean premium = premiumUuid != null;
        String fallback = selectFallback(value);
        String avatarId = premium ? premiumUuid : fallback;
        MinecraftAvatarDto avatar = new MinecraftAvatarDto(
            value,
            premium,
            buildAvatarUrl(avatarId),
            fallback
        );

        try {
            redis.opsForValue().set(cacheKey, mapper.writeValueAsString(avatar), CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception ignored) {}

        return avatar;
    }

    private String lookupPremiumUuid(String playerId) {
        if (playerId.isBlank()) return null;

        String encoded = URLEncoder.encode(playerId, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_API + encoded))
            .timeout(Duration.ofSeconds(3))
            .header("Accept", "application/json")
            .header("User-Agent", "NorthStar/1.0")
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = mapper.readTree(response.body());
            String uuid = root.path("id").asText(null);
            return uuid == null || uuid.isBlank() ? null : uuid.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String selectFallback(String playerId) {
        int codePointSum = playerId.toLowerCase(Locale.ROOT).codePoints().sum();
        return codePointSum % 2 == 0 ? "Steve" : "Alex";
    }

    private String buildAvatarUrl(String avatarId) {
        return AVATAR_CDN + URLEncoder.encode(avatarId, StandardCharsets.UTF_8) + "/128";
    }
}
