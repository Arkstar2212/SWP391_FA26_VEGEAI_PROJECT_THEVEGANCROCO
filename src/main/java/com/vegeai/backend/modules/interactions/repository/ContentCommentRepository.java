package com.vegeai.backend.modules.interactions.repository;
import com.vegeai.backend.modules.interactions.entity.ContentComment;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;
public interface ContentCommentRepository extends JpaRepository<ContentComment,UUID>,JpaSpecificationExecutor<ContentComment>{
 Page<ContentComment> findByTargetTypeAndTargetId(InteractionTarget targetType,UUID targetId,Pageable pageable);
 Optional<ContentComment> findByIdAndTargetTypeAndTargetId(UUID id,InteractionTarget type,UUID targetId);
 void deleteByTargetTypeAndTargetId(InteractionTarget type,UUID targetId);
}
