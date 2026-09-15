package com.example.swp391_fa26_vegeai_project.modules.auth.service;

import com.example.swp391_fa26_vegeai_project.common.exception.AppException;
import com.example.swp391_fa26_vegeai_project.common.exception.ErrorCode;
import com.example.swp391_fa26_vegeai_project.modules.auth.dto.AuthResponse;
import com.example.swp391_fa26_vegeai_project.modules.auth.dto.LoginRequest;
import com.example.swp391_fa26_vegeai_project.modules.auth.dto.RegisterRequest;
import com.example.swp391_fa26_vegeai_project.modules.user.entity.Role;
import com.example.swp391_fa26_vegeai_project.modules.user.entity.User;
import com.example.swp391_fa26_vegeai_project.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered in VEGEAI");
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.ROLE_USER)
                .dietaryPreferences(request.getDietaryPreferences())
                .allergies(request.getAllergies())
                .build();

        User saved = userRepository.save(user);

        return AuthResponse.builder()
                .accessToken("mock-jwt-token-" + saved.getId())
                .tokenType("Bearer")
                .userId(saved.getId())
                .email(saved.getEmail())
                .fullName(saved.getFullName())
                .role(saved.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        return AuthResponse.builder()
                .accessToken("mock-jwt-token-" + user.getId())
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
