package com.vegeai.backend.modules.notification.repository;

import com.vegeai.backend.modules.notification.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
    Page<UserNotification> findByRecipient_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<UserNotification> findByIdAndRecipient_UserId(UUID id, UUID userId);

    long countByRecipient_UserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("update UserNotification n set n.readAt = :readAt where n.recipient.userId = :userId and n.readAt is null")
    int markAllReadByRecipient(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);
}
