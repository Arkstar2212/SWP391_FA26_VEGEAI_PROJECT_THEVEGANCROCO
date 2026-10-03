package com.vegeai.backend.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process login rate limiter using ConcurrentHashMap.
 * Tracks failed login attempts per identifier (username/email).
 *
 * Spec: max 5 failures in 15 minutes → return LOGIN_ATTEMPTS_EXCEEDED.
 * Successful login resets the failure counter.
 *
 * For multi-instance deployments, replace with Redis-based implementation.
 */
@Service
@Slf4j
public class LoginRateLimitService {

    @Value("${app.rate-limit.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.rate-limit.login.window-minutes:15}")
    private long windowMinutes;

    /** Maps normalized-identifier → (failureCount, windowStartEpochSec) */
    private final Map<String, long[]> attempts = new ConcurrentHashMap<>();

    /**
     * Records a failed login attempt.
     *
     * @return true if the user is now blocked (threshold exceeded)
     */
    public boolean recordFailure(String identifier) {
        String key = normalize(identifier);
        long now = Instant.now().getEpochSecond();
        long windowSecs = windowMinutes * 60;

        long[] entry = attempts.compute(key, (k, existing) -> {
            if (existing == null || (now - existing[1]) >= windowSecs) {
                return new long[]{1, now}; // [count, windowStart]
            }
            existing[0]++;
            return existing;
        });

        int count = (int) entry[0];
        log.debug("[RATE-LIMIT] Failed login #{} for: {}", count, key);
        return count >= maxAttempts;
    }

    /** Checks if identifier is currently blocked without incrementing. */
    public boolean isBlocked(String identifier) {
        String key = normalize(identifier);
        long[] entry = attempts.get(key);
        if (entry == null) return false;

        long now = Instant.now().getEpochSecond();
        long windowSecs = windowMinutes * 60;
        if ((now - entry[1]) >= windowSecs) {
            attempts.remove(key); // window expired
            return false;
        }
        return entry[0] >= maxAttempts;
    }

    /** Resets the counter on successful login. */
    public void resetCounter(String identifier) {
        attempts.remove(normalize(identifier));
    }

    /** Returns remaining attempts before lockout (0 if already blocked). */
    public int remainingAttempts(String identifier) {
        long[] entry = attempts.get(normalize(identifier));
        if (entry == null) return maxAttempts;
        return Math.max(0, maxAttempts - (int) entry[0]);
    }

    /** Scheduled cleanup: remove expired windows every 10 minutes. */
    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        long now = Instant.now().getEpochSecond();
        long windowSecs = windowMinutes * 60;
        Iterator<Map.Entry<String, long[]>> it = attempts.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if ((now - it.next().getValue()[1]) >= windowSecs) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[RATE-LIMIT] Cleanup removed {} expired entries.", removed);
        }
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.toLowerCase().trim();
    }
}
