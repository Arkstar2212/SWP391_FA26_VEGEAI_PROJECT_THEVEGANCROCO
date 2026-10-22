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
import java.time.LocalDate;

/**
 * Automatically initializes and enriches seed user accounts with authentic profiles
 * (ADMIN, MEMBER, CHEF, NUTRITIONIST, DOCTOR, STUDENT).
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
        log.info("[USER-INIT] Checking and enriching seed user accounts with realistic profiles...");

        // 1. System Administrator
        seedUser("admin", "admin@vegeai.com", "Admin@123", "Quản Trị Viên Hệ Thống VEGEAI", UserRole.ADMIN, AccountStatus.ACTIVE,
                "0909000111", LocalDate.of(1992, 10, 10), "MALE", "Trụ sở VEGEAI, TP. Hồ Chí Minh",
                175.0, 68.0, 1800, "VEGAN", "NONE");

        // 2. Standard Member (Trần Mai Anh)
        seedUser("member", "member@vegeai.com", "Member@123", "Trần Mai Anh (Thành viên Thân thiết)", UserRole.MEMBER, AccountStatus.ACTIVE,
                "0908123456", LocalDate.of(1998, 5, 20), "FEMALE", "184 Phan Xích Long, Phường 2, Quận Phú Nhuận, TP.HCM",
                165.0, 54.0, 1550, "VEGAN", "NONE");

        // 3. Unverified User
        seedUser("unverified", "unverified@vegeai.com", "User@123", "Thành viên Chưa Xác Thực", UserRole.MEMBER, AccountStatus.PENDING_VERIFICATION,
                null, null, null, null, null, null, null, "VEGETARIAN", null);

        // 4. Chef An Nhiên (Culinary Creator)
        seedUser("annhien", "annhien@vegeai.com", "AnNhien@123", "Bếp Chay An Nhiên (Trần Hoàng An)", UserRole.MEMBER, AccountStatus.ACTIVE,
                "0903124567", LocalDate.of(1994, 6, 15), "FEMALE", "124/8 Huỳnh Văn Bánh, Phường 11, Quận Phú Nhuận, TP.HCM",
                165.0, 54.0, 1550, "VEGAN", "NONE");

        // 5. Fitness Coach Minh Khoa (High Protein Vegan)
        seedUser("minhkhoa", "minhkhoa@vegeai.com", "MinhKhoa@123", "Lê Minh Khoa (HLV Dinh Dưỡng & Thể Hình Thực Vật)", UserRole.MEMBER, AccountStatus.ACTIVE,
                "0918765432", LocalDate.of(1996, 9, 22), "MALE", "88 Nguyễn Du, Phường Bến Nghé, Quận 1, TP.HCM",
                178.0, 73.0, 2200, "VEGAN", "NONE");

        // 6. Clinical Nutritionist Dr. Thao Linh (Medical Doctor)
        seedUser("thaolinh", "thaolinh@vegeai.com", "ThaoLinh@123", "BS. Nguyễn Thảo Linh (Dinh Dưỡng Lâm Sàng)", UserRole.MEMBER, AccountStatus.ACTIVE,
                "0934567890", LocalDate.of(1990, 3, 8), "FEMALE", "45 Cô Giang, Phường 2, Quận Phú Nhuận, TP.HCM",
                160.0, 49.0, 1400, "VEGETARIAN", "PEANUTS");

        // 7. Student Tuan Khang (Budget GenZ Vegan)
        seedUser("tuankhang", "tuankhang@vegeai.com", "TuanKhang@123", "Đặng Tuấn Khang (GenZ Yêu Bếp Chay Tiết Kiệm)", UserRole.MEMBER, AccountStatus.ACTIVE,
                "0977889900", LocalDate.of(2003, 11, 12), "MALE", "12 Hoàng Hoa Thám, Phường 7, Quận Bình Thạnh, TP.HCM",
                172.0, 63.0, 1750, "FLEXITARIAN", "NONE");
    }

    private void seedUser(String username, String email, String rawPassword, String fullName, UserRole role, AccountStatus status,
                          String phone, LocalDate dob, String gender, String address, Double height, Double weight, Integer calorieTarget,
                          String dietary, String allergies) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .or(() -> userRepository.findByUsernameIgnoreCase(username))
                .orElse(null);

        String sha256Hex = computeSha256(rawPassword);
        String encodedHash = passwordEncoder.encode(sha256Hex);

        if (user == null) {
            Double bmi = (height != null && weight != null && height > 0)
                    ? Math.round((weight / Math.pow(height / 100.0, 2)) * 10.0) / 10.0
                    : null;

            user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(encodedHash)
                    .fullName(fullName)
                    .role(role)
                    .status(status)
                    .phoneNumber(phone)
                    .dateOfBirth(dob)
                    .gender(gender)
                    .address(address)
                    .heightCm(height)
                    .weightKg(weight)
                    .bmi(bmi)
                    .dailyCalorieTarget(calorieTarget)
                    .dietaryPreferences(dietary)
                    .allergies(allergies)
                    .build();

            userRepository.save(user);
            log.info("[USER-INIT] Successfully seeded account: {} ({}) with password: {}", email, role, rawPassword);
        } else {
            // Synchronize credentials and enrich profile fields
            user.setPasswordHash(encodedHash);
            boolean changed = true;
            if (user.getFullName() == null || user.getFullName().equals("Standard Member") || user.getFullName().equals("System Administrator")) {
                user.setFullName(fullName);
                changed = true;
            }
            if (user.getHeightCm() == null && height != null) {
                user.setHeightCm(height);
                user.setWeightKg(weight);
                user.setBmi(Math.round((weight / Math.pow(height / 100.0, 2)) * 10.0) / 10.0);
                user.setDailyCalorieTarget(calorieTarget);
                changed = true;
            }
            if (user.getPhoneNumber() == null && phone != null) {
                user.setPhoneNumber(phone);
                user.setDateOfBirth(dob);
                user.setGender(gender);
                user.setAddress(address);
                changed = true;
            }
            if (user.getDietaryPreferences() == null && dietary != null) {
                user.setDietaryPreferences(dietary);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                log.info("[USER-INIT] Enriched profile for existing account: {}", email);
            }
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
