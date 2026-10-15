package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.user.entity.*;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class CommunityAccess {
    private final UserRepository users;

    public User user(String principal) {
        UUID id;
        try { id = UUID.fromString(principal); }
        catch (IllegalArgumentException | NullPointerException ex) { throw new AppException(ErrorCode.UNAUTHORIZED); }
        User user = users.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        if (user.getStatus() != AccountStatus.ACTIVE) throw new AppException(ErrorCode.FORBIDDEN);
        return user;
    }

    public User admin(String principal) {
        User user = user(principal);
        if (user.getRole() != UserRole.ADMIN) throw new AppException(ErrorCode.FORBIDDEN);
        return user;
    }

    public PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new AppException(ErrorCode.VALIDATION_ERROR, "page must be >= 0; size must be between 1 and 50.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    public String keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        if (keyword.length() > 200) throw new AppException(ErrorCode.VALIDATION_ERROR, "keyword must not exceed 200 characters.");
        return "%" + keyword.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public void revision(long actual, Long expected) {
        if (expected == null || expected < 0) throw new AppException(ErrorCode.VALIDATION_ERROR, "A nonnegative version is required.");
        if (actual != expected) throw new AppException(ErrorCode.COMMUNITY_CONFLICT);
    }

    public String name(User user) { return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName(); }
}
