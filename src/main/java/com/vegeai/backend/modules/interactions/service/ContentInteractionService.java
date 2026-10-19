package com.vegeai.backend.modules.interactions.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.entity.CommunityVideo;
import com.vegeai.backend.modules.community.entity.VideoStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.community.repository.VideoRepository;
import com.vegeai.backend.modules.community.service.CommunityAccess;
import com.vegeai.backend.modules.interactions.dto.InteractionDtos.*;
import com.vegeai.backend.modules.interactions.entity.ContentComment;
import com.vegeai.backend.modules.interactions.entity.ContentRating;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import com.vegeai.backend.modules.interactions.repository.ContentCommentRepository;
import com.vegeai.backend.modules.interactions.repository.ContentRatingRepository;
import com.vegeai.backend.modules.interactions.repository.RatingAggregate;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.List;
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
    private final VideoRepository videos;
    private final CommunityAccess access;
    private final ContentInteractionAssetService assets;

    @Transactional(readOnly = true)
    public ContentView publicContent(InteractionTarget type, UUID id, String principal, int page, int size) {
        requirePublished(type, id);
        UUID userId = optionalUserId(principal);
        var pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var items = comments.findByTargetTypeAndTargetId(type, id, pageable).map(comment -> view(comment, true));
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
        return view(comments.saveAndFlush(comment), false);
    }

    public CommentView addReview(String principal, InteractionTarget type, UUID id, ReviewInput input,
                                 List<MultipartFile> photos) {
        requirePublished(type, id);
        User user = access.user(principal);
        saveRating(user, type, id, input.stars());
        ContentComment comment = new ContentComment();
        comment.setTargetType(type);
        comment.setTargetId(id);
        comment.setAuthor(user);
        applyReview(comment, input);
        if (input.photoUrls() != null && !input.photoUrls().isEmpty())
            throw new AppException(ErrorCode.VALIDATION_ERROR, "New reviews cannot reference previously uploaded photos.");
        comment.setPhotoUrls(assets.store(photos));
        return view(comments.saveAndFlush(comment), true);
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
        return view(comment, false);
    }

    public CommentView editReview(String principal, InteractionTarget type, UUID targetId,
                                  UUID commentId, ReviewInput input, List<MultipartFile> photos) {
        requirePublished(type, targetId);
        User user = access.user(principal);
        ContentComment comment = comments.findByIdAndTargetTypeAndTargetId(commentId, type, targetId)
                .filter(c -> c.getAuthor().getUserId().equals(user.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        access.revision(comment.getVersion(), input.version());
        applyReview(comment, input);
        List<String> retained = input.photoUrls() == null ? List.of() : input.photoUrls();
        if (retained.size() + (photos == null ? 0 : photos.size()) > 4
                || !comment.getPhotoUrls().containsAll(retained))
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A review can include up to four of its existing or new photos.");
        List<String> previous = new ArrayList<>(comment.getPhotoUrls());
        List<String> next = new ArrayList<>(retained);
        next.addAll(assets.store(photos));
        comment.setPhotoUrls(next);
        List<String> removed = previous.stream().filter(url -> !retained.contains(url)).toList();
        assets.delete(removed);
        saveRating(user, type, targetId, input.stars());
        comments.flush();
        return view(comment, false);
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
        assets.delete(comment.getPhotoUrls());
        comments.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentView> adminComments(InteractionTarget type, int page, int size) {
        var pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Specification<ContentComment> spec = (root, query, cb) -> cb.conjunction();
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), type));
        return PageResponse.from(comments.findAll(spec, pageable).map(comment -> view(comment, false)));
    }

    public void adminDeleteComment(String principal, UUID id, Long version) {
        access.admin(principal);
        ContentComment comment = comments.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        access.revision(comment.getVersion(), version);
        comments.delete(comment);
        assets.delete(comment.getPhotoUrls());
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
        } else if (type == InteractionTarget.VIDEO) {
            CommunityVideo video = videos.findById(id).orElseThrow(() -> new AppException(ErrorCode.VIDEO_NOT_FOUND));
            if (video.getStatus() != VideoStatus.PUBLISHED) throw new AppException(ErrorCode.VIDEO_NOT_FOUND);
        } else throw new AppException(ErrorCode.VALIDATION_ERROR);
    }

    private CommentView view(ContentComment c, boolean publicView) {
        String authorName = publicView && !c.isPublicDisplayName() ? "Ẩn danh" : access.name(c.getAuthor());
        return new CommentView(c.getId(), c.getTargetType(), c.getTargetId(), c.getAuthor().getUserId(),
                authorName, c.getContent(), c.getReviewStars(), List.copyOf(c.getTags()),
                List.copyOf(c.getPhotoUrls()), c.isPublicDisplayName(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    private void applyReview(ContentComment comment, ReviewInput input) {
        comment.setContent(input.content().strip());
        comment.setReviewStars(input.stars());
        comment.setTags(input.tags() == null ? new ArrayList<>() : new ArrayList<>(input.tags()));
        comment.setPublicDisplayName(input.publicDisplayName());
    }

    private void saveRating(User user, InteractionTarget type, UUID id, int stars) {
        validateStars(stars);
        ContentRating rating = ratings.findByTargetTypeAndTargetIdAndUser_UserId(type, id, user.getUserId())
                .orElseGet(ContentRating::new);
        rating.setTargetType(type);
        rating.setTargetId(id);
        rating.setUser(user);
        rating.setStars(stars);
        ratings.save(rating);
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
