package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.CommunityQuestion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<CommunityQuestion, UUID>, JpaSpecificationExecutor<CommunityQuestion> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from CommunityQuestion q where q.id = :id")
    Optional<CommunityQuestion> findForUpdate(@Param("id") UUID id);
}
