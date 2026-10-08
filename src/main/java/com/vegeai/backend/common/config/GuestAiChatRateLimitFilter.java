package com.vegeai.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security Guard / Rate Limit Filter for AI Chatbot endpoint (/api/ai/chat).
 *
 * Rules:
 *  - Authenticated Users (Bearer token present & valid): Unlimited access.
 *  - Guests (Unauthenticated): Limited to 3 requests per 24 hours per Client-IP via Redis.
 *  - Exceeding limit: Returns HTTP 429 Too Many Requests with GUEST_AI_QUOTA_EXCEEDED error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuestAiChatRateLimitFilter extends OncePerRequestFilter {

    private static final String AI_CHAT_ENDPOINT = "/api/ai/chat";
    private static final String REDIS_GUEST_QUOTA_PREFIX = "ai:guest:quota:";
    public static final int MAX_GUEST_DAILY_REQUESTS = 3;
    private static final Duration QUOTA_WINDOW = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Fallback local memory in case Redis is offline
    private final Map<String, long[]> localFallbackGuestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only guard /api/ai/chat POST requests
        if (!request.getRequestURI().equalsIgnoreCase(AI_CHAT_ENDPOINT) || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if user is authenticated (JwtAuthenticationFilter executes prior to this filter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

        if (isAuthenticated) {
            // Member or Admin with valid JWT: exempt from guest quota
            log.debug("[AI-GUARD] Authenticated request by '{}' to AI Chatbot allowed without guest quota limit.", auth.getName());
            filterChain.doFilter(request, response);
            return;
        }

        // Guest user: enforce 3 requests/24h per Client-IP
        String clientIp = extractClientIp(request);
        log.debug("[AI-GUARD] Guest request from IP: {}", clientIp);

        boolean allowed = checkAndConsumeGuestQuota(clientIp);
        if (!allowed) {
            log.warn("[AI-GUARD] Guest quota exceeded for IP: {}. Max: {} requests/24h.", clientIp, MAX_GUEST_DAILY_REQUESTS);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<Void> errorEnvelope = ApiResponse.error(
                    ErrorCode.GUEST_AI_QUOTA_EXCEEDED.getCode(),
                    "Guest trial limit reached (" + MAX_GUEST_DAILY_REQUESTS + " requests per 24h). Please register or log in to continue using the VEGEAI Assistant."
            );
            objectMapper.writeValue(response.getWriter(), errorEnvelope);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkAndConsumeGuestQuota(String clientIp) {
        String key = REDIS_GUEST_QUOTA_PREFIX + clientIp;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, QUOTA_WINDOW);
            }
            return count != null && count <= MAX_GUEST_DAILY_REQUESTS;
        } catch (Exception ex) {
            log.warn("[AI-GUARD] Redis error checking guest quota, using local fallback: {}", ex.getMessage());
            long now = System.currentTimeMillis();
            long[] entry = localFallbackGuestCounts.compute(clientIp, (k, current) -> {
                if (current == null || (now - current[1]) >= QUOTA_WINDOW.toMillis()) {
                    return new long[]{1L, now};
                }
                current[0]++;
                return current;
            });
            return entry[0] <= MAX_GUEST_DAILY_REQUESTS;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
