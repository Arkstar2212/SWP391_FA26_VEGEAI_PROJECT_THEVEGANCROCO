package com.vegeai.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Security Guard / Rate Limit Filter for the AI Chatbot endpoint ({@code POST /api/ai/chat}).
 *
 * <p>Rules:
 * <ul>
 *   <li>Authenticated users (valid Bearer token): unlimited, the guest counter is untouched.</li>
 *   <li>Guests: {@value #MAX_GUEST_DAILY_REQUESTS} successful turns per 24h, counted against an
 *       HttpOnly browser cookie so several guests behind one NAT/Docker gateway do not share a quota.</li>
 *   <li>A per-client-IP backstop (4× the browser quota) prevents trivial cookie clearing from
 *       turning this into an unlimited Gemini proxy.</li>
 *   <li>Only successful answers (HTTP &lt; 400) are counted: provider outages no longer burn a trial.</li>
 *   <li>Exceeding the quota returns HTTP 429 with {@code GUEST_AI_QUOTA_EXCEEDED}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuestAiChatRateLimitFilter extends OncePerRequestFilter {

    private static final String AI_CHAT_ENDPOINT = "/api/ai/chat";
    private static final String GUEST_COOKIE = "vegeai_guest_id";
    private static final Pattern GUEST_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final String REDIS_BROWSER_PREFIX = "ai:guest:quota:";
    private static final String REDIS_IP_PREFIX = "ai:guest:ip:";
    public static final int MAX_GUEST_DAILY_REQUESTS = 3;
    private static final int IP_BACKSTOP_REQUESTS = MAX_GUEST_DAILY_REQUESTS * 4;
    private static final Duration QUOTA_WINDOW = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Fallback local memory in case Redis is offline.
    private final Map<String, long[]> localCounters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!AI_CHAT_ENDPOINT.equalsIgnoreCase(request.getRequestURI())
                || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        if (isAuthenticated) {
            filterChain.doFilter(request, response);
            return;
        }

        String guestId = readGuestId(request);
        if (guestId == null) {
            guestId = UUID.randomUUID().toString();
            response.addHeader(HttpHeaders.SET_COOKIE, buildGuestCookie(guestId).toString());
        }
        String clientIp = extractClientIp(request);
        String browserKey = REDIS_BROWSER_PREFIX + guestId;
        String ipKey = REDIS_IP_PREFIX + clientIp;

        if (!consume(browserKey, MAX_GUEST_DAILY_REQUESTS)) {
            log.warn("[AI-GUARD] Guest trial exhausted for browser {} (IP {}).", guestId, clientIp);
            writeQuotaError(response);
            return;
        }
        if (!consume(ipKey, IP_BACKSTOP_REQUESTS)) {
            refund(browserKey);
            log.warn("[AI-GUARD] Guest IP backstop exhausted for IP {}.", clientIp);
            writeQuotaError(response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // A failed provider call must not cost the guest one of their three answers.
            if (response.getStatus() >= 400) {
                refund(browserKey);
                refund(ipKey);
            }
        }
    }

    private String readGuestId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (GUEST_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value != null && GUEST_ID.matcher(value).matches() ? value : null;
            }
        }
        return null;
    }

    private ResponseCookie buildGuestCookie(String guestId) {
        return ResponseCookie.from(GUEST_COOKIE, guestId)
                .httpOnly(true)
                .secure(false) // enable behind HTTPS in production
                .sameSite("Lax")
                .path("/")
                .maxAge(QUOTA_WINDOW)
                .build();
    }

    /** Counts one attempt; when the limit is already reached the counter is restored and false is returned. */
    private boolean consume(String key, int max) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) redisTemplate.expire(key, QUOTA_WINDOW);
            if (count == null) return true;
            if (count > max) {
                redisTemplate.opsForValue().decrement(key);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("[AI-GUARD] Redis unavailable, using in-memory quota: {}", ex.getMessage());
            long now = System.currentTimeMillis();
            long[] entry = localCounters.compute(key, (k, current) -> {
                if (current == null || (now - current[1]) >= QUOTA_WINDOW.toMillis()) return new long[]{1L, now};
                current[0]++;
                return current;
            });
            if (entry[0] > max) {
                entry[0]--;
                return false;
            }
            return true;
        }
    }

    private void refund(String key) {
        try {
            redisTemplate.opsForValue().decrement(key);
        } catch (Exception ex) {
            long[] entry = localCounters.get(key);
            if (entry != null && entry[0] > 0) entry[0]--;
        }
    }

    private void writeQuotaError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                ErrorCode.GUEST_AI_QUOTA_EXCEEDED.getCode(),
                "Guest trial limit reached (" + MAX_GUEST_DAILY_REQUESTS
                        + " successful answers per 24h). Please register or log in to continue using the VEGEAI Assistant."));
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
