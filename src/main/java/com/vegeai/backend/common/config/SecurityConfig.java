package com.vegeai.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Spring Security configuration.
 *
 * Endpoint security matrix:
 *  PUBLIC (permitAll):
 *    /api/auth/**        — all auth endpoints
 *    Published recipe, blog and question browsing; nearby places.
 *    /api-docs/**        — OpenAPI spec
 *    /swagger-ui/**      — Swagger UI
 *
 *  ADMIN only:
 *    /api/admin/**       — admin management endpoints
 *
 *  Authenticated (any role):
 *    everything else
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final RoleBoundaryFilter roleBoundaryFilter;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    // ── Password encoder ─────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/verify-email",
                                "/api/auth/resend-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()
                        // AI, nutrition and video features require a signed-in account.
                        .requestMatchers("/api/ai/**", "/api/nutrition/**", "/api/videos", "/api/videos/**").authenticated()
                        // Swagger / OpenAPI
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                         "/api-docs/**", "/v3/api-docs/**").permitAll()
                        // Guest business features: recipes, Blog, Q&A and nearby places.
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/blog/posts", "/api/blog/posts/*").permitAll()
                        .requestMatchers("/api/users/*/public").authenticated()
                        // Recipes & Smart Filter (Public browsing, authenticated fridge matching, personal cookbook)
                        .requestMatchers("/api/recipes/fridge-match").authenticated()
                        .requestMatchers("/api/recipes/cookbook/**", "/api/recipes/cookbook").authenticated()
                        .requestMatchers("/api/recipes/**").permitAll()
                        // OpenStreetMap places (Public nearby search)
                        .requestMatchers("/api/places/**").permitAll()
                        // Admin only
                        .requestMatchers("/api/questions/mine", "/api/questions/mine/**", "/api/videos/favorites", "/api/videos/*/favorite").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/questions", "/api/questions/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(roleBoundaryFilter, JwtAuthenticationFilter.class)
                .build();
    }

    // ── CORS ─────────────────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",   // Vite dev server
                "http://localhost:3000"    // Alternative dev port
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // Required for HttpOnly cookie
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── Custom error responses (JSON, not HTML redirect) ──────────────────────

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                writeErrorResponse(response, HttpStatus.UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED.getCode(),
                        "Authentication required. Please provide a valid Bearer token.");
    }

    private org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeErrorResponse(response, HttpStatus.FORBIDDEN,
                        ErrorCode.FORBIDDEN.getCode(),
                        "You do not have permission to access this resource.");
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status,
                                    int code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(code, message));
    }
}
