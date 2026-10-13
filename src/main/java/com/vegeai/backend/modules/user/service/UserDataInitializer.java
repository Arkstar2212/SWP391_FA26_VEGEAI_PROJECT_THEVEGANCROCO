package com.vegeai.backend.modules.user.service;

import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Automatically initializes seed user accounts (ADMIN, MEMBER, UNVERIFIED)
 * upon application startup if they do not exist.
 *
 * Ensures consistent development and testing credentials across all environments
 * (local Docker, Supabase, Cloud VPS) without depending solely on db-init.sql execution.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[USER-INIT] Checking default user accounts...");
        seedUserIfNotExists("admin", "admin@vegeai.com", "Admin@123", "System Administrator", UserRole.ADMIN, AccountStatus.ACTIVE);
        seedUserIfNotExists("member", "member@vegeai.com", "Member@123", "Standard Member", UserRole.MEMBER, AccountStatus.ACTIVE);
        seedUserIfNotExists("unverified", "unverified@vegeai.com", "User@123", "Unverified Member", UserRole.MEMBER, AccountStatus.PENDING_VERIFICATION);
    }

    private void seedUserIfNotExists(String username, String email, String rawPassword, String fullName, UserRole role, AccountStatus status) {
        if (!userRepository.existsByEmailIgnoreCase(email) && !userRepository.existsByUsernameIgnoreCase(username)) {
            String sha256Hex = computeSha256(rawPassword);
            String encodedHash = passwordEncoder.encode(sha256Hex);

            User user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(encodedHash)
                    .fullName(fullName)
                    .role(role)
                    .status(status)
                    .dietaryPreferences(role == UserRole.ADMIN ? "VEGAN" : "VEGETARIAN")
                    .build();

            userRepository.save(user);
            log.info("[USER-INIT] Successfully seeded account: {} ({}) with raw password: {}", email, role, rawPassword);
        } else {
            log.debug("[USER-INIT] Account {} already exists, skipping seed.", email);
        }
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
