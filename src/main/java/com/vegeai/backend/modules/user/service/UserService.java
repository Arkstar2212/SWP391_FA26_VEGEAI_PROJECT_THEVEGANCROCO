package com.vegeai.backend.modules.user.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Get the profile of the authenticated user (identified by userId from JWT).
     */
    public UserProfileResponse getMyProfile(String userIdStr) {
        UUID userId = parseUuid(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user);
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid user ID format.");
        }
    }
}
