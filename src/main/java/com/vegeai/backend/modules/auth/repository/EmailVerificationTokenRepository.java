package com.vegeai.backend.modules.auth.repository;

import com.vegeai.backend.modules.auth.entity.EmailVerificationToken;
import com.vegeai.backend.modules.auth.entity.TokenPurpose;
import com.vegeai.backend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Fetch the latest unused OTP for a user/purpose combo (for verification checks).
     */
    @Query("""
            SELECT t FROM EmailVerificationToken t
            WHERE t.user = :user AND t.purpose = :purpose AND t.used = false
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerificationToken> findLatestUnused(
            @Param("user") User user,
            @Param("purpose") TokenPurpose purpose);

    /**
     * Invalidate all previous unused OTPs before issuing a new one.
     */
    @Modifying
    @Query("""
            UPDATE EmailVerificationToken t
            SET t.used = true
            WHERE t.user = :user AND t.purpose = :purpose AND t.used = false
            """)
    void invalidatePreviousTokens(
            @Param("user") User user,
            @Param("purpose") TokenPurpose purpose);
}
