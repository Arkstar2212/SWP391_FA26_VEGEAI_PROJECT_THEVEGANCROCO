package com.vegeai.backend.modules.auth.entity;

import com.vegeai.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OTP token used for email verification (REGISTRATION) and password reset (FORGOT_PASSWORD).
 * The `purpose` field prevents cross-purpose OTP reuse attacks.
 */
@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_evt_user_purpose", columnList = "user_id, purpose")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private TokenPurpose purpose;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
