package com.ming.northstar_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null);
    }

    /**
     * 签发令牌。
     *
     * @param issuedAt {@code null} 表示用当前时间；重置密码后需要显式推后 1 秒，
     *                 否则新令牌的 {@code iat} 会落在刚写入的吊销时间戳之内而被
     *                 {@code TokenRevocationService} 判为「已作废」。
     */
    public String generateToken(Long userId, String username, Date issuedAt) {
        Date issued = issuedAt == null ? new Date() : issuedAt;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(issued)
                .expiration(new Date(issued.getTime() + expiration))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 解析失败（签名不符、过期、格式非法）时返回空，由调用方决定如何响应。 */
    public Optional<Claims> tryParse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseToken(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean validateToken(String token) {
        return tryParse(token).isPresent();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }
}
