package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID>, JpaSpecificationExecutor<BlogPost> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BlogPost p where p.id = :id")
    Optional<BlogPost> findForUpdate(@Param("id") UUID id);

    @Query("select count(p) from BlogPost p where p.aiModerationStatus in :statuses and p.aiModerationAgreedWithAdmin is not null")
    long countAiModerationReviewed(@Param("statuses") Collection<AiModerationStatus> statuses);

    @Query("select count(p) from BlogPost p where p.aiModerationStatus in :statuses and p.aiModerationAgreedWithAdmin = true")
    long countAiModerationAgreements(@Param("statuses") Collection<AiModerationStatus> statuses);

    @Query("""
            select p from BlogPost p
            where p.status = :status and p.aiModerationStatus = :aiStatus
              and (p.aiModerationClaimedAt is null or p.aiModerationClaimedAt <= :expiredBefore)
            order by p.submittedAt asc
            """)
    List<BlogPost> findAvailableModerationJobs(@Param("status") BlogStatus status,
                                               @Param("aiStatus") AiModerationStatus aiStatus,
                                               @Param("expiredBefore") LocalDateTime expiredBefore,
                                               Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update BlogPost p
            set p.aiModerationClaimToken = :claimToken, p.aiModerationClaimedAt = :claimedAt
            where p.id = :id and p.version = :expectedVersion
              and p.status = :status and p.aiModerationStatus = :aiStatus
              and (p.aiModerationClaimedAt is null or p.aiModerationClaimedAt <= :expiredBefore)
            """)
    int claimModerationJob(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                           @Param("status") BlogStatus status, @Param("aiStatus") AiModerationStatus aiStatus,
                           @Param("claimToken") UUID claimToken, @Param("claimedAt") LocalDateTime claimedAt,
                           @Param("expiredBefore") LocalDateTime expiredBefore);
}
