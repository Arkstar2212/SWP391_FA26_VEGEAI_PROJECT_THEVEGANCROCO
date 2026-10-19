package com.vegeai.backend.modules.interactions.repository;
import com.vegeai.backend.modules.interactions.entity.ContentRating;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ContentRatingRepository extends JpaRepository<ContentRating,UUID>,JpaSpecificationExecutor<ContentRating>{
 Optional<ContentRating> findByTargetTypeAndTargetIdAndUser_UserId(InteractionTarget type,UUID targetId,UUID userId);
 long countByTargetTypeAndTargetId(InteractionTarget type,UUID targetId);
 @Query("select avg(r.stars) from ContentRating r where r.targetType=:type and r.targetId=:id")
 Double averageStars(@Param("type") InteractionTarget type,@Param("id") UUID id);
 @Query("select r.targetId as targetId, avg(r.stars) as averageStars, count(r) as ratingCount from ContentRating r where r.targetType=:type and r.targetId in :ids group by r.targetId")
 List<RatingAggregate> summarizeByTargetTypeAndTargetIdIn(@Param("type") InteractionTarget type,@Param("ids") List<UUID> ids);
 void deleteByTargetTypeAndTargetId(InteractionTarget type,UUID targetId);
}
