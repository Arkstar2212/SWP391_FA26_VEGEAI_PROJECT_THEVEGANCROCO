package com.vegeai.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.user.entity.*;
import com.vegeai.backend.modules.user.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class RoleBoundaryFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ObjectMapper mapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        boolean adminArea = within(path, "/api/admin");
        boolean personal = within(path, "/api/ai") || within(path, "/api/fridge")
                || within(path, "/api/nutrition") || within(path, "/api/meal-plans") || within(path, "/api/recipes/cookbook")
                || path.equals("/api/recipes/fridge-match");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if ((!adminArea && !personal) || auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        User user;
        try { user = users.findById(UUID.fromString(auth.getName())).orElse(null); }
        catch (IllegalArgumentException ex) { user = null; }
        ErrorCode error = user == null ? ErrorCode.UNAUTHORIZED
                : user.getStatus() == AccountStatus.PENDING_VERIFICATION ? ErrorCode.ACCOUNT_PENDING_VERIFICATION
                : user.getStatus() != AccountStatus.ACTIVE
                || (adminArea && user.getRole() != UserRole.ADMIN)
                || (personal && user.getRole() != UserRole.MEMBER) ? ErrorCode.FORBIDDEN : null;
        if (error == null) { chain.doFilter(request, response); return; }
        response.setStatus(error.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.error(error.getCode(), error.getDefaultMessage()));
    }
    private boolean within(String path, String prefix) { return path.equals(prefix) || path.startsWith(prefix + "/"); }
}
