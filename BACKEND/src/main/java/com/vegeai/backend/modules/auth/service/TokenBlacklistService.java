package com.vegeai.backend.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token blacklist using an in-process ConcurrentHashMap.
 * Revoked access tokens are stored with their expiry timestamp.
 * A scheduled cleanup task removes expired entries every 5 minutes.
 *
 * For production with multiple instances, replace this with Redis.
 * The interface is the same — only this bean needs to change.
 */
@Service
@Slf4j
public class TokenBlacklistService {

    /**
     * Map: token → expiry epoch second.
     */
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * Add a token to the blacklist until its expiry.
     *
     * @param token         the raw JWT string
     * @param expiryEpochSec unix epoch second when the token expires
     */
    public void blacklist(String token, long expiryEpochSec) {
        if (expiryEpochSec > Instant.now().getEpochSecond()) {
            blacklist.put(token, expiryEpochSec);
            log.debug("[BLACKLIST] Token added. Expires at epoch: {}", expiryEpochSec);
        }
        // If already expired, no need to blacklist
    }

    /**
     * Returns true if the token is currently blacklisted (not yet expired).
     */
    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) return false;
        if (expiry <= Instant.now().getEpochSecond()) {
            blacklist.remove(token); // lazy cleanup
            return false;
        }
        return true;
    }

    /**
     * Scheduled cleanup: purge expired entries every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long now = Instant.now().getEpochSecond();
        Iterator<Map.Entry<String, Long>> it = blacklist.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if (it.next().getValue() <= now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[BLACKLIST] Cleanup removed {} expired tokens. Remaining: {}", removed, blacklist.size());
        }
    }
}
