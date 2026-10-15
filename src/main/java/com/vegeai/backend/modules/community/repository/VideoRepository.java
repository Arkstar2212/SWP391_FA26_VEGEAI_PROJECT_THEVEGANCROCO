package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.CommunityVideo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<CommunityVideo, UUID>, JpaSpecificationExecutor<CommunityVideo> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CommunityVideo v where v.id = :id")
    Optional<CommunityVideo> findForUpdate(@Param("id") UUID id);
}
