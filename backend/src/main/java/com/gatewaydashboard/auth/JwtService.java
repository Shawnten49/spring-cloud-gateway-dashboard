package com.gatewaydashboard.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {

    /**
     * application.yml 中的开发默认密钥。生产 profile 下仍使用该值将直接拒绝启动，
     * 防止公开默认值被离线伪造任意 ADMIN token（安全评审 P1-A / S-01）。
     */
    private static final String DEV_DEFAULT_SECRET = "gateway-dashboard-dev-secret-change-me-0123456789";
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey key;
    private final long expireHours;

    public JwtService(@Value("${gateway-dashboard.jwt.secret}") String secret,
                      @Value("${gateway-dashboard.jwt.expire-hours}") long expireHours,
                      Environment environment) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT 密钥长度不足 " + MIN_SECRET_LENGTH
                    + " 字节，请通过 JWT_SECRET 环境变量设置强随机密钥");
        }
        if (DEV_DEFAULT_SECRET.equals(secret) && !isDevLikeProfile(environment)) {
            throw new IllegalStateException("非开发环境禁止使用默认 JWT 密钥，请通过 JWT_SECRET 环境变量设置强随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    private boolean isDevLikeProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("local") || p.equalsIgnoreCase("test"));
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole())
                .claim("ver", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
