package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.BlogPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID>, JpaSpecificationExecutor<BlogPost> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BlogPost p where p.id = :id")
    Optional<BlogPost> findForUpdate(@Param("id") UUID id);
}
