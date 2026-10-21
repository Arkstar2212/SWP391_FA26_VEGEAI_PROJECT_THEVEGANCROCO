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

/** One current 1-5-star rating per member and content item. */
@Entity
@Table(name="content_ratings",uniqueConstraints=@UniqueConstraint(name="uk_content_rating_member",columnNames={"target_type","target_id","user_id"}),
 indexes=@Index(name="idx_content_rating_target",columnList="target_type,target_id"))
@Getter @Setter
public class ContentRating {
 public ContentRating() {}
 @Id @Column(nullable=false,updatable=false) private UUID id=UUID.randomUUID();
 @Enumerated(EnumType.STRING) @Column(name="target_type",nullable=false,length=16) private InteractionTarget targetType;
 @Column(name="target_id",nullable=false) private UUID targetId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false) private int stars;
 @CreationTimestamp @Column(name="created_at",updatable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at") private LocalDateTime updatedAt;
}
