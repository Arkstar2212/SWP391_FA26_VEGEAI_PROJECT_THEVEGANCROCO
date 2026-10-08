package com.vegeai.backend.modules.auth.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Distributed login brute-force rate limiter combining Bucket4j token bucket semantics with Redis.
 *
 * Rules:
 *  - Username/Email: Max 5 failed attempts per 15 minutes. Reset upon successful authentication.
 *  - Client IP: Max 20 failed attempts per 15 minutes across any accounts.
 *  - Exceeding limit: throws AppException with HTTP 429 Too Many Requests (LOGIN_ATTEMPTS_EXCEEDED).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimitService {

    public static final int MAX_USER_FAILED_ATTEMPTS = 5;
    public static final int MAX_IP_FAILED_ATTEMPTS = 20;
    public static final long WINDOW_MINUTES = 15;

    private static final String REDIS_USER_PREFIX = "auth:ratelimit:login:user:";
    private static final String REDIS_IP_PREFIX   = "auth:ratelimit:login:ip:";

    private final StringRedisTemplate redisTemplate;

    // Bucket4j token bucket specifications
    private final Bandwidth userBandwidth = Bandwidth.builder()
            .capacity(MAX_USER_FAILED_ATTEMPTS)
            .refillGreedy(MAX_USER_FAILED_ATTEMPTS, Duration.ofMinutes(WINDOW_MINUTES))
            .build();

    private final Bandwidth ipBandwidth = Bandwidth.builder()
            .capacity(MAX_IP_FAILED_ATTEMPTS)
            .refillGreedy(MAX_IP_FAILED_ATTEMPTS, Duration.ofMinutes(WINDOW_MINUTES))
            .build();

    // Local Bucket4j fallback cache for resilient operation when Redis is unavailable
    private final Map<String, Bucket> localUserBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> localIpBuckets = new ConcurrentHashMap<>();

    /**
     * Pre-check: Verifies if the username/email or IP is already blocked BEFORE verifying credentials.
     *
     * @throws AppException if rate limit is exceeded (HTTP 429 LOGIN_ATTEMPTS_EXCEEDED)
     */
    public void checkAllowed(String identifier, String clientIp) {
        if (isBlocked(identifier, clientIp)) {
            log.warn("[RATE-LIMIT] Blocked login attempt. Identifier: {}, IP: {}", identifier, clientIp);
            throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED,
                    "Too many failed login attempts. Try again in 15 minutes.");
        }
    }

    /**
     * Checks if either the identifier or IP is currently locked out.
     */
    public boolean isBlocked(String identifier, String clientIp) {
        return isUserBlocked(identifier) || isIpBlocked(clientIp);
    }

    /** Check if identifier alone is blocked. */
    public boolean isBlocked(String identifier) {
        return isUserBlocked(identifier);
    }

    public boolean isUserBlocked(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String normId = normalize(identifier);
        try {
            String val = redisTemplate.opsForValue().get(REDIS_USER_PREFIX + normId);
            if (val != null && Integer.parseInt(val) >= MAX_USER_FAILED_ATTEMPTS) {
                return true;
            }
        } catch (Exception ex) {
            log.debug("[RATE-LIMIT] Redis check failed, checking local Bucket4j: {}", ex.getMessage());
            Bucket bucket = localUserBuckets.get(normId);
            if (bucket != null && bucket.getAvailableTokens() == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isIpBlocked(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return false;
        try {
            String val = redisTemplate.opsForValue().get(REDIS_IP_PREFIX + clientIp);
            if (val != null && Integer.parseInt(val) >= MAX_IP_FAILED_ATTEMPTS) {
                return true;
            }
        } catch (Exception ex) {
            log.debug("[RATE-LIMIT] Redis check failed for IP, checking local Bucket4j: {}", ex.getMessage());
            Bucket bucket = localIpBuckets.get(clientIp);
            if (bucket != null && bucket.getAvailableTokens() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a failed login attempt for both the identifier and the client IP.
     *
     * @return true if the attempt resulted in locking out the user or IP.
     */
    public boolean recordFailure(String identifier, String clientIp) {
        String normId = normalize(identifier);
        boolean userLocked = false;
        boolean ipLocked = false;

        // 1. Record User failure in Redis
        try {
            String userKey = REDIS_USER_PREFIX + normId;
            Long userCount = redisTemplate.opsForValue().increment(userKey);
            if (userCount != null && userCount == 1) {
                redisTemplate.expire(userKey, WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            if (userCount != null && userCount >= MAX_USER_FAILED_ATTEMPTS) {
                userLocked = true;
            }
        } catch (Exception ex) {
            log.warn("[RATE-LIMIT] Redis failure recording user failed attempt, using Bucket4j: {}", ex.getMessage());
            Bucket bucket = localUserBuckets.computeIfAbsent(normId, k -> Bucket.builder().addLimit(userBandwidth).build());
            if (!bucket.tryConsume(1)) {
                userLocked = true;
            }
        }

        // 2. Record IP failure in Redis
        if (clientIp != null && !clientIp.isBlank()) {
            try {
                String ipKey = REDIS_IP_PREFIX + clientIp;
                Long ipCount = redisTemplate.opsForValue().increment(ipKey);
                if (ipCount != null && ipCount == 1) {
                    redisTemplate.expire(ipKey, WINDOW_MINUTES, TimeUnit.MINUTES);
                }
                if (ipCount != null && ipCount >= MAX_IP_FAILED_ATTEMPTS) {
                    ipLocked = true;
                }
            } catch (Exception ex) {
                log.warn("[RATE-LIMIT] Redis failure recording IP failed attempt, using Bucket4j: {}", ex.getMessage());
                Bucket bucket = localIpBuckets.computeIfAbsent(clientIp, k -> Bucket.builder().addLimit(ipBandwidth).build());
                if (!bucket.tryConsume(1)) {
                    ipLocked = true;
                }
            }
        }

        log.debug("[RATE-LIMIT] Recorded failure for id={}, ip={}. userLocked={}, ipLocked={}", normId, clientIp, userLocked, ipLocked);
        return userLocked || ipLocked;
    }

    /** Overload for callers without IP. */
    public boolean recordFailure(String identifier) {
        return recordFailure(identifier, null);
    }

    /**
     * Resets the failure counter on successful login for the given identifier.
     */
    public void resetCounter(String identifier) {
        if (identifier == null || identifier.isBlank()) return;
        String normId = normalize(identifier);
        try {
            redisTemplate.delete(REDIS_USER_PREFIX + normId);
            log.debug("[RATE-LIMIT] Reset counter in Redis for: {}", normId);
        } catch (Exception ex) {
            log.warn("[RATE-LIMIT] Failed to reset counter in Redis: {}", ex.getMessage());
        }
        localUserBuckets.remove(normId);
    }

    private String normalize(String identifier) {
        return identifier.trim().toLowerCase();
    }
}
