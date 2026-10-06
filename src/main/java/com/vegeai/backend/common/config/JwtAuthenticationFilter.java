package com.vegeai.backend.common.config;

import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.auth.service.TokenBlacklistService;
import com.vegeai.backend.modules.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT authentication filter.
 * Reads Bearer token from Authorization header, validates it,
 * checks the blacklist, then sets the SecurityContext.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        // Blacklist check (logout invalidation)
        if (tokenBlacklistService.isBlacklisted(token)) {
            log.debug("[JWT] Blacklisted token rejected: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.validateAndGetClaims(token);
            String userId = jwtService.extractUserId(claims);
            String role   = jwtService.extractRole(claims);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String springRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(springRole));

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception ex) {
            // Token invalid or expired — let request proceed unauthenticated (403 handled by Spring Security)
            log.debug("[JWT] Token validation failed ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
