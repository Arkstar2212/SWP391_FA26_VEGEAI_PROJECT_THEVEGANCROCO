package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.community.dto.BlogDtos.PostInput;
import com.vegeai.backend.modules.community.dto.BlogDtos.PostResponse;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import com.vegeai.backend.modules.interactions.repository.ContentCommentRepository;
import com.vegeai.backend.modules.interactions.repository.ContentRatingRepository;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/** Adds member-managed actions without changing the existing blog API behavior. */
@Service
@RequiredArgsConstructor
@Transactional
public class PersonalBlogActionsService {
    private final BlogPostRepository posts;
    private final CommunityAccess access;
    private final NotificationService notifications;
    private final ContentCommentRepository comments;
    private final ContentRatingRepository ratings;

    public PostResponse requestPublishedEdit(String principal, UUID id, PostInput input) {
        User user = access.user(principal);
        BlogPost post = posts.findForUpdate(id).orElseThrow(this::missing);
        requireOwner(post, user);
        access.revision(post.getVersion(), input.version());
        if (post.getStatus() != BlogStatus.PUBLISHED) throw conflict();
        post.setTitle(input.title().strip());
        post.setExcerpt(input.excerpt() == null ? "" : input.excerpt().strip());
        post.setContent(input.content() == null ? "" : input.content().strip());
        post.setCategory(input.category());
        post.setCoverImage(validateCover(input.coverImage()));
        if (post.getTitle().length() < 5 || post.getContent().length() < 80) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Title must contain at least 5 characters and content at least 80 characters before review.");
        }
        post.setStatus(BlogStatus.PENDING_REVIEW);
        post.queueAiModeration();
        post.setSubmittedAt(LocalDateTime.now());
        post.setReviewedAt(null);
        post.setReviewer(null);
        post.setModerationReason(null);
        post.setPublishedAt(null);
        posts.flush();
        notifications.notifyAdministrators("BLOG_SUBMITTED", "Bài viết chỉnh sửa cần được duyệt",
                "Một thành viên đã gửi phiên bản chỉnh sửa của bài viết.", "/admin/blog/" + post.getId());
        return response(post, user);
    }

    public void deleteOwnedPost(String principal, UUID id, Long version) {
        User user = access.user(principal);
        BlogPost post = posts.findForUpdate(id).orElseThrow(this::missing);
        requireOwner(post, user);
        access.revision(post.getVersion(), version);
        // Ratings and comments use polymorphic IDs, so remove them explicitly before deleting the post.
        comments.deleteByTargetTypeAndTargetId(InteractionTarget.BLOG, id);
        ratings.deleteByTargetTypeAndTargetId(InteractionTarget.BLOG, id);
        posts.delete(post);
        posts.flush();
    }

    private PostResponse response(BlogPost p, User author) {
        int words = p.getContent().isBlank() ? 0 : p.getContent().strip().split("\\s+").length;
        return new PostResponse(p.getId(), author.getUserId(), access.name(author), p.getTitle(), p.getExcerpt(),
                p.getContent(), p.getCoverImage(), p.getCategory(), p.getStatus(), p.getVersion(),
                Math.max(1, (int) Math.ceil(words / 200.0)), p.getCreatedAt(), p.getUpdatedAt(),
                p.getSubmittedAt(), p.getReviewedAt(), p.getPublishedAt(),
                p.getAiModerationStatus() == null ? AiModerationStatus.NOT_CHECKED : p.getAiModerationStatus(),
                p.getAiModerationReason(), p.getAiModerationAgreedWithAdmin(), null, p.getModerationReason());
    }

    private String validateCover(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.strip();
        if (trimmed.matches("/images/[A-Za-z0-9._-]+")) return trimmed;
        try {
            URI uri = URI.create(trimmed);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null) return uri.toString();
        } catch (IllegalArgumentException ignored) { }
        throw new AppException(ErrorCode.VALIDATION_ERROR, "Cover image must be an HTTP(S) URL or local /images/ path.");
    }

    private void requireOwner(BlogPost post, User user) {
        if (!post.getAuthor().getUserId().equals(user.getUserId())) throw missing();
    }
    private AppException missing() { return new AppException(ErrorCode.BLOG_POST_NOT_FOUND); }
    private AppException conflict() { return new AppException(ErrorCode.BLOG_STATE_CONFLICT); }
}
