package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface VideoFavoriteRepository extends JpaRepository<VideoFavorite, UUID> {
    Optional<VideoFavorite> findByVideoIdAndUserUserId(UUID videoId, UUID userId);
    @Query("select f.video from VideoFavorite f where f.user.userId = :userId and f.video.status = :status order by f.createdAt desc, f.id desc")
    Page<CommunityVideo> findSaved(@Param("userId") UUID userId, @Param("status") VideoStatus status, Pageable pageable);
    @Modifying @Query("delete from VideoFavorite f where f.video.id = :id")
    void deleteAllForVideo(@Param("id") UUID videoId);
}
