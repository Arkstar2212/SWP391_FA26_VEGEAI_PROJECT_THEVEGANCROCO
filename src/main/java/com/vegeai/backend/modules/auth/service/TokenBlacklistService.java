package com.vegeai.backend.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Distributed JWT Token Blacklist backed by Spring Data Redis.
 * Revoked access tokens are stored in Redis with their remaining TTL.
 * Redis automatically evicts expired keys.
 * A fallback in-memory cache is maintained in case Redis is temporarily unreachable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    private final StringRedisTemplate redisTemplate;

    // Fallback in-memory map in case Redis is unreachable (e.g., isolated test environments)
    private final Map<String, Long> localFallbackBlacklist = new ConcurrentHashMap<>();

    /**
     * Add a JWT access token to the Redis blacklist with a specified TTL.
     *
     * @param token               the raw JWT string
     * @param remainingTtlSeconds remaining lifetime of the token in seconds
     */
    public void blacklist(String token, long remainingTtlSeconds) {
        if (token == null || token.isBlank() || remainingTtlSeconds <= 0) {
            return;
        }

        String key = BLACKLIST_KEY_PREFIX + token;
        try {
            redisTemplate.opsForValue().set(key, "revoked", remainingTtlSeconds, TimeUnit.SECONDS);
            log.info("[REDIS-BLACKLIST] Token blacklisted in Redis with TTL: {}s", remainingTtlSeconds);
        } catch (Exception ex) {
            log.warn("[REDIS-BLACKLIST] Redis unavailable, using local fallback blacklist: {}", ex.getMessage());
            localFallbackBlacklist.put(token, System.currentTimeMillis() + (remainingTtlSeconds * 1000));
        }
    }

    /**
     * Returns true if the token is present in the Redis blacklist.
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String key = BLACKLIST_KEY_PREFIX + token;
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(hasKey)) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("[REDIS-BLACKLIST] Redis error checking blacklist, consulting fallback: {}", ex.getMessage());
        }

        // Consult local fallback
        Long expiryTime = localFallbackBlacklist.get(token);
        if (expiryTime != null) {
            if (System.currentTimeMillis() < expiryTime) {
                return true;
            }
            localFallbackBlacklist.remove(token);
        }
        return false;
    }
}
