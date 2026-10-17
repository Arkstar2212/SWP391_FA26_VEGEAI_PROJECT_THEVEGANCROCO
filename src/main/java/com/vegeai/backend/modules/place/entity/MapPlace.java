package com.vegeai.backend.modules.place.entity;

import com.vegeai.backend.modules.place.dto.PlaceCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "map_places", indexes = {
        @Index(name = "idx_map_places_category", columnList = "category"),
        @Index(name = "idx_map_places_place_id", columnList = "place_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "place_id", nullable = false, unique = true, length = 120)
    private String placeId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlaceCategory category;

    @Column(nullable = false, length = 400)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Builder.Default
    @Column(nullable = false)
    private Double rating = 4.8;

    @Builder.Default
    @Column(name = "user_ratings_total", nullable = false)
    private Integer userRatingsTotal = 120;

    @Builder.Default
    @Column(name = "is_open_now", nullable = false)
    private Boolean isOpenNow = true;

    @Column(name = "photo_url", length = 1000)
    private String photoUrl;

    @Column(name = "phone_number", length = 60)
    private String phoneNumber;

    @Builder.Default
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = true;

    @Builder.Default
    @Column(name = "created_by_admin", nullable = false)
    private boolean createdByAdmin = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
