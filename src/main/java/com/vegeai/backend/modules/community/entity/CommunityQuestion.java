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
@Table(name = "community_questions", indexes = {
        @Index(name = "idx_questions_status_created", columnList = "status,created_at"),
        @Index(name = "idx_questions_author_created", columnList = "author_id,created_at")})
@Getter @Setter
public class CommunityQuestion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
    @Column(nullable = false, length = 5000)
    private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.PENDING;
    @Column(length = 10000)
    private String answer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "answered_by")
    private User answeredBy;
    private LocalDateTime answeredAt;
    @Column(length = 1000)
    private String moderationReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by")
    private User reviewedBy;
    private LocalDateTime reviewedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
    @Version private long version;
}
