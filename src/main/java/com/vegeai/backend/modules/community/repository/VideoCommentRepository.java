package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.VideoComment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface VideoCommentRepository extends JpaRepository<VideoComment, UUID> {
    Page<VideoComment> findByVideoId(UUID videoId, Pageable pageable);
    Optional<VideoComment> findByIdAndVideoId(UUID id, UUID videoId);
    @Modifying @Query("delete from VideoComment c where c.video.id = :id")
    void deleteAllForVideo(@Param("id") UUID videoId);
}
