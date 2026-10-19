package com.vegeai.backend.modules.interactions.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.community.service.CommunityAccess;
import com.vegeai.backend.modules.interactions.dto.InteractionDtos.*;
import com.vegeai.backend.modules.interactions.entity.ContentComment;
import com.vegeai.backend.modules.interactions.entity.ContentRating;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import com.vegeai.backend.modules.interactions.repository.ContentCommentRepository;
import com.vegeai.backend.modules.interactions.repository.ContentRatingRepository;
import com.vegeai.backend.modules.interactions.repository.RatingAggregate;
import com.vegeai.backend.modules.recipe.entity.Recipe;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentInteractionService {
    private final ContentCommentRepository comments;
    private final ContentRatingRepository ratings;
    private final BlogPostRepository blogs;
    private final RecipeRepository recipes;
    private final CommunityAccess access;

    @Transactional(readOnly = true)
    public ContentView publicContent(InteractionTarget type, UUID id, String principal, int page, int size) {
        requirePublished(type, id);
        UUID userId = optionalUserId(principal);
        var pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var items = comments.findByTargetTypeAndTargetId(type, id, pageable).map(this::view);
        return new ContentView(summary(type, id, userId), PageResponse.from(items));
    }

    @Transactional(readOnly = true)
    public java.util.List<RatingSummary> publicRatingSummaries(InteractionTarget type, java.util.List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.size() > 50)
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        var ids = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        for (UUID id : ids) requirePublished(type, id);
        Map<UUID, RatingAggregate> aggregates = ratings.summarizeByTargetTypeAndTargetIdIn(type, ids).stream()
                .collect(Collectors.toMap(RatingAggregate::getTargetId, Function.identity()));
        return ids.stream().map(id -> {
            RatingAggregate aggregate = aggregates.get(id);
            return new RatingSummary(type, id, aggregate == null ? null : aggregate.getAverageStars(),
                    aggregate == null ? 0 : aggregate.getRatingCount(), null);
        }).toList();
    }

    public RatingSummary rate(String principal, InteractionTarget type, UUID id, int stars) {
        validateStars(stars);
        requirePublished(type, id);
        User user = access.user(principal);
        ContentRating rating = ratings.findByTargetTypeAndTargetIdAndUser_UserId(type, id, user.getUserId())
                .orElseGet(ContentRating::new);
        rating.setTargetType(type);
        rating.setTargetId(id);
        rating.setUser(user);
        rating.setStars(stars);
        ratings.saveAndFlush(rating);
        return summary(type, id, user.getUserId());
    }

    public RatingSummary removeRating(String principal, InteractionTarget type, UUID id) {
        requirePublished(type, id);
        User user = access.user(principal);
        ratings.findByTargetTypeAndTargetIdAndUser_UserId(type, id, user.getUserId()).ifPresent(ratings::delete);
        ratings.flush();
        return summary(type, id, user.getUserId());
    }

    public CommentView addComment(String principal, InteractionTarget type, UUID id, CommentInput input) {
        requirePublished(type, id);
        ContentComment comment = new ContentComment();
        comment.setTargetType(type);
        comment.setTargetId(id);
        comment.setAuthor(access.user(principal));
        comment.setContent(input.content().strip());
        return view(comments.saveAndFlush(comment));
    }

    public CommentView editComment(String principal, InteractionTarget type, UUID targetId,
                                   UUID commentId, CommentInput input) {
        requirePublished(type, targetId);
        User user = access.user(principal);
        ContentComment comment = comments.findByIdAndTargetTypeAndTargetId(commentId, type, targetId)
                .filter(c -> c.getAuthor().getUserId().equals(user.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        access.revision(comment.getVersion(), input.version());
        comment.setContent(input.content().strip());
        comments.flush();
        return view(comment);
    }

    public void deleteComment(String principal, InteractionTarget type, UUID targetId,
                              UUID commentId, Long version) {
        User user = access.user(principal);
        ContentComment comment = comments.findByIdAndTargetTypeAndTargetId(commentId, type, targetId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (user.getRole() != UserRole.ADMIN && !user.getUserId().equals(comment.getAuthor().getUserId()))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        access.revision(comment.getVersion(), version);
        comments.delete(comment);
        comments.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentView> adminComments(InteractionTarget type, int page, int size) {
        var pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Specification<ContentComment> spec = (root, query, cb) -> cb.conjunction();
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), type));
        return PageResponse.from(comments.findAll(spec, pageable).map(this::view));
    }

    public void adminDeleteComment(String principal, UUID id, Long version) {
        access.admin(principal);
        ContentComment comment = comments.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        access.revision(comment.getVersion(), version);
        comments.delete(comment);
        comments.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRating> adminRatings(InteractionTarget type, int page, int size) {
        var pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Specification<ContentRating> spec = (root, query, cb) -> cb.conjunction();
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), type));
        return PageResponse.from(ratings.findAll(spec, pageable).map(r ->
                new AdminRating(r.getId(), r.getTargetType(), r.getTargetId(), r.getUser().getUserId(),
                        access.name(r.getUser()), r.getStars(), r.getUpdatedAt())));
    }

    public void adminDeleteRating(String principal, UUID id) {
        access.admin(principal);
        ratings.delete(ratings.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND)));
        ratings.flush();
    }

    private RatingSummary summary(InteractionTarget type, UUID id, UUID userId) {
        Integer mine = userId == null ? null : ratings.findByTargetTypeAndTargetIdAndUser_UserId(type, id, userId)
                .map(ContentRating::getStars).orElse(null);
        return new RatingSummary(type, id, ratings.averageStars(type, id),
                ratings.countByTargetTypeAndTargetId(type, id), mine);
    }

    private void requirePublished(InteractionTarget type, UUID id) {
        if (type == InteractionTarget.BLOG) {
            BlogPost post = blogs.findById(id).orElseThrow(() -> new AppException(ErrorCode.BLOG_POST_NOT_FOUND));
            if (post.getStatus() != BlogStatus.PUBLISHED) throw new AppException(ErrorCode.BLOG_POST_NOT_FOUND);
        } else if (type == InteractionTarget.RECIPE) {
            Recipe recipe = recipes.findById(id).orElseThrow(() -> new AppException(ErrorCode.RECIPE_NOT_FOUND));
            if (recipe.getStatus() != RecipeStatus.PUBLISHED) throw new AppException(ErrorCode.RECIPE_NOT_FOUND);
        } else throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    private CommentView view(ContentComment c) {
        return new CommentView(c.getId(), c.getTargetType(), c.getTargetId(), c.getAuthor().getUserId(),
                access.name(c.getAuthor()), c.getContent(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    private UUID optionalUserId(String principal) {
        if (principal == null || principal.isBlank()) return null;
        try { return UUID.fromString(principal); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 50) throw new AppException(ErrorCode.VALIDATION_ERROR);
        return PageRequest.of(page, size, sort);
    }

    private void validateStars(int stars) {
        if (stars < 1 || stars > 5) throw new AppException(ErrorCode.VALIDATION_ERROR, "stars must be between 1 and 5.");
    }
}
