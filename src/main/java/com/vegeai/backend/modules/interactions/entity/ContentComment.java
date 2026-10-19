package com.vegeai.backend.modules.interactions.entity;
import com.vegeai.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/** A member comment attached to a published blog post or discovery recipe. */
@Entity
@Table(name="content_comments", indexes={
 @Index(name="idx_content_comment_target", columnList="target_type,target_id,created_at"),
 @Index(name="idx_content_comment_author", columnList="author_id,created_at")})
@Getter @Setter
public class ContentComment {
 public ContentComment() {}
 @Id @Column(nullable=false,updatable=false) private UUID id=UUID.randomUUID();
 @Enumerated(EnumType.STRING) @Column(name="target_type",nullable=false,length=16) private InteractionTarget targetType;
 @Column(name="target_id",nullable=false) private UUID targetId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id",nullable=false) private User author;
 @Column(nullable=false,length=2000) private String content;
 @CreationTimestamp @Column(name="created_at",updatable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at") private LocalDateTime updatedAt;
 @Version private long version;
}
