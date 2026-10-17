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
@Table(name = "blog_posts", indexes = {
        @Index(name = "idx_blog_status_created", columnList = "status,created_at"),
        @Index(name = "idx_blog_author_created", columnList = "author_id,created_at"),
        @Index(name = "idx_blog_category", columnList = "category")})
@Getter @Setter
public class BlogPost {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "post_id") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false) private User author;
    @Column(nullable = false, length = 150) private String title;
    @Column(length = 400) private String excerpt;
    @Column(nullable = false, length = 50000) private String content;
    @Column(length = 1000) private String coverImage;
    @Column(name = "video_url", length = 1000) private String videoUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private BlogCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private BlogStatus status;
    @Column(length = 2000) private String moderationReason;
    @Column(name = "ai_moderation_status", length = 40) private String aiModerationStatus;
    @Column(name = "ai_safety_score") private Integer aiSafetyScore;
    @Column(name = "ai_moderation_notes", length = 2000) private String aiModerationNotes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewer_id") private User reviewer;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime publishedAt;
    @Version private long version;
}
