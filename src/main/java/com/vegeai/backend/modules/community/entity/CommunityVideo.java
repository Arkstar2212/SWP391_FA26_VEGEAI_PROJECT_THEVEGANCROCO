package com.vegeai.backend.modules.community.entity;

import com.vegeai.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "community_videos", indexes = {
        @Index(name = "idx_videos_status_created", columnList = "status,created_at"),
        @Index(name = "idx_videos_category", columnList = "category")})
@Getter @Setter
public class CommunityVideo {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 10000) private String description;
    @Column(nullable = false, length = 2000) private String videoUrl;
    @Column(length = 2000) private String thumbnailUrl;
    @Column(length = 80) private String category;
    private Integer durationSeconds;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private VideoStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
    @Version private long version;
}
