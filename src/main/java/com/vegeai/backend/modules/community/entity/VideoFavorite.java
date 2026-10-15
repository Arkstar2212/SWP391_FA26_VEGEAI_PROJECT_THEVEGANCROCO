package com.vegeai.backend.modules.community.entity;

import com.vegeai.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_favorites", uniqueConstraints = @UniqueConstraint(name = "uk_video_favorite_user", columnNames = {"video_id", "user_id"}),
        indexes = @Index(name = "idx_video_favorites_user_created", columnList = "user_id,created_at"))
@Getter @Setter
public class VideoFavorite {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false) private CommunityVideo video;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
