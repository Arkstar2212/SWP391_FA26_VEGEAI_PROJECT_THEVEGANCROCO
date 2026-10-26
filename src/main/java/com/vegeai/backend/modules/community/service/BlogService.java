package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.community.dto.BlogDtos.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.BlogCommentRepository;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.ai.service.GeminiAiService;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional @Slf4j
public class BlogService {
    private final BlogPostRepository posts;
    private final BlogCommentRepository comments;
    private final CommunityAccess access;
    private final GeminiAiService ai;
    private final NotificationService notifications;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> published(String keyword, BlogCategory category, int page, int size) {
        return list(null, BlogStatus.PUBLISHED, keyword, category, page, size, false);
    }

    @Transactional(readOnly = true)
    public PostResponse published(UUID id) {
        BlogPost post = find(id);
        if (post.getStatus() != BlogStatus.PUBLISHED) throw missing();
        return response(post, true, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> mine(String principal, BlogStatus status, int page, int size) {
        return list(access.user(principal).getUserId(), status, null, null, page, size, true);
    }

    @Transactional(readOnly = true)
    public PostResponse own(String principal, UUID id) {
        User user = access.user(principal);
        BlogPost post = find(id);
        requireOwner(post, user);
        return response(post, true, true);
    }

    public PostResponse create(String principal, PostInput input) {
        BlogPost post = new BlogPost();
        post.setAuthor(access.user(principal));
        post.setStatus(BlogStatus.DRAFT);
        apply(post, input);
        return response(posts.saveAndFlush(post), true, true);
    }

    public PostResponse update(String principal, UUID id, PostInput input) {
        BlogPost post = ownedForUpdate(principal, id, input.version());
        boolean directEdit = post.getStatus() == BlogStatus.PUBLISHED && access.user(principal).getRole() == UserRole.ADMIN;
        if (!directEdit) requireEditable(post);
        apply(post, input);
        if (directEdit) requirePublicationContent(post);
        posts.flush();
        return response(post, true, true);
    }

    public PostResponse submit(String principal, UUID id, long version) {
        BlogPost post = ownedForUpdate(principal, id, version);
        requireEditable(post);
        requirePublicationContent(post);
        boolean directPublish = access.user(principal).getRole() == UserRole.ADMIN;
        post.setStatus(directPublish ? BlogStatus.PUBLISHED : BlogStatus.PENDING_REVIEW);
        post.setSubmittedAt(directPublish ? null : LocalDateTime.now());
        if (directPublish) {
            post.setPublishedAt(LocalDateTime.now());
            post.setReviewer(access.user(principal));
            post.setReviewedAt(LocalDateTime.now());
            post.setModerationReason(null);
            post.setAiModerationStatus(com.vegeai.backend.modules.community.entity.AiModerationStatus.SAFE);
            post.setAiModerationReason("Directly published by administrator.");
        } else {
            runAiModeration(post);
        }
        posts.flush();
        return response(post, true, true);
    }

    public PostResponse withdraw(String principal, UUID id, long version) {
        BlogPost post = ownedForUpdate(principal, id, version);
        requireStatus(post, BlogStatus.PENDING_REVIEW);
        post.setStatus(BlogStatus.DRAFT);
        posts.flush();
        return response(post, true, true);
    }

    public void delete(String principal, UUID id, Long version) {
        BlogPost post = ownedForUpdate(principal, id, version);
        if (post.getStatus() != BlogStatus.DRAFT && post.getStatus() != BlogStatus.REJECTED) {
            throw new AppException(ErrorCode.BLOG_STATE_CONFLICT);
        }
        posts.delete(post);
    }

    private void runAiModeration(BlogPost post) {
        post.setAiModerationAgreedWithAdmin(null);
        try {
            var result = ai.moderateContent(post.getTitle(), post.getContent());
            if (result.flagged()) {
                post.setAiModerationStatus(com.vegeai.backend.modules.community.entity.AiModerationStatus.FLAGGED);
                post.setAiModerationReason(result.reason());
            } else {
                post.setAiModerationStatus(com.vegeai.backend.modules.community.entity.AiModerationStatus.SAFE);
                post.setAiModerationReason("AI safe.");
            }
        } catch (Exception ex) {
            log.warn("AI moderation failed: {}", ex.getMessage());
            post.setAiModerationStatus(com.vegeai.backend.modules.community.entity.AiModerationStatus.UNAVAILABLE);
            post.setAiModerationReason("System busy.");
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<PostResponse> queue(String principal, BlogStatus status, String keyword, int page, int size) {
        return list(null, status, keyword, null, page, size, true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PostResponse reviewPost(String principal, UUID id) {
        return response(find(id), true, true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PostResponse review(String reviewerPrincipal, UUID id, ReviewInput input) {
        BlogPost post = locked(id);
        requireStatus(post, BlogStatus.PENDING_REVIEW);
        access.revision(post.getVersion(), input.version());
        User reviewer = access.user(reviewerPrincipal);
        boolean approve = input.decision() == Decision.APPROVE;
        String reason = input.reason() == null ? "" : input.reason().strip();
        if (!approve && reason.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A reason is mandatory when rejecting a blog post.");
        }
        post.setStatus(approve ? BlogStatus.PUBLISHED : BlogStatus.REJECTED);
        post.setReviewer(reviewer);
        post.setReviewedAt(LocalDateTime.now());
        post.setModerationReason(approve ? null : reason);
        if (approve) post.setPublishedAt(LocalDateTime.now());
        // Feeds the AI/admin agreement rate on the AI monitoring dashboard: AI flagged = it advised rejection.
        if (post.getAiModerationStatus() == AiModerationStatus.SAFE || post.getAiModerationStatus() == AiModerationStatus.FLAGGED) {
            post.setAiModerationAgreedWithAdmin((post.getAiModerationStatus() == AiModerationStatus.FLAGGED) == !approve);
        }
        posts.flush();

        notifications.notify(post.getAuthor().getUserId(),
                approve ? NotificationService.BLOG_APPROVED : NotificationService.BLOG_REJECTED,
                approve ? "Bài viết đã được xuất bản 🎉" : "Bài viết cần chỉnh sửa",
                approve ? "\"" + post.getTitle() + "\" đã được duyệt và hiển thị trên Blog cộng đồng."
                        : "\"" + post.getTitle() + "\" chưa được duyệt. Lý do: " + reason,
                approve ? "/blog/" + post.getId() : "/blog/my-posts/" + post.getId(),
                "BLOG_REVIEW:" + post.getId() + ":" + post.getVersion());
        return response(post, true, true);
    }

    // ── Blog Comments & Ratings ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlogCommentResponse> getComments(UUID postId) {
        find(postId); // verify existence
        return comments.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .map(c -> new BlogCommentResponse(c.getId(), c.getPost().getId(),
                        c.getAuthor().getUserId(), access.name(c.getAuthor()),
                        c.getContent(), c.getRating() == null ? 5 : c.getRating(), c.getCreatedAt()))
                .toList();
    }

    public BlogCommentResponse addComment(String principal, UUID postId, BlogCommentInput input) {
        BlogPost post = locked(postId);
        if (post.getStatus() != BlogStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BLOG_STATE_CONFLICT, "Only published posts can receive comments.");
        }
        User author = access.user(principal);

        BlogComment comment = new BlogComment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(input.content().strip());
        int rating = input.rating() == null ? 5 : Math.max(1, Math.min(5, input.rating()));
        comment.setRating(rating);

        BlogComment saved = comments.saveAndFlush(comment);

        // Recalculate post rating average
        List<BlogComment> allComments = comments.findByPostIdOrderByCreatedAtDesc(postId);
        double sum = allComments.stream().mapToInt(c -> c.getRating() == null ? 5 : c.getRating()).sum();
        double avg = allComments.isEmpty() ? 5.0 : Math.round((sum / allComments.size()) * 10.0) / 10.0;
        post.setRatingAverage(avg);
        post.setRatingCount(allComments.size());
        posts.flush();

        return new BlogCommentResponse(saved.getId(), post.getId(), author.getUserId(),
                access.name(author), saved.getContent(), saved.getRating(), saved.getCreatedAt());
    }

    private PageResponse<PostResponse> list(UUID author, BlogStatus status, String keyword,
                                            BlogCategory category, int page, int size, boolean privateView) {
        String pattern = access.keyword(keyword);
        Specification<BlogPost> spec = (root, query, cb) -> cb.conjunction();
        if (author != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("author").get("userId"), author));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (category != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        if (pattern != null) spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'), cb.like(cb.lower(root.get("excerpt")), pattern, '\\')));
        return PageResponse.from(posts.findAll(spec, access.page(page, size)).map(p -> response(p, false, privateView)));
    }

    private BlogPost find(UUID id) { return posts.findById(id).orElseThrow(this::missing); }
    private BlogPost locked(UUID id) { return posts.findForUpdate(id).orElseThrow(this::missing); }
    private AppException missing() { return new AppException(ErrorCode.BLOG_POST_NOT_FOUND); }
    private void requireOwner(BlogPost post, User user) {
        if (!post.getAuthor().getUserId().equals(user.getUserId())) throw missing();
    }
    private BlogPost ownedForUpdate(String principal, UUID id, Long version) {
        User user = access.user(principal);
        BlogPost post = locked(id);
        requireOwner(post, user);
        access.revision(post.getVersion(), version);
        return post;
    }
    private void requireEditable(BlogPost post) {
        if (post.getStatus() != BlogStatus.DRAFT && post.getStatus() != BlogStatus.REJECTED) {
            throw new AppException(ErrorCode.BLOG_STATE_CONFLICT);
        }
    }
    private void requirePublicationContent(BlogPost post) {
        if (post.getTitle().length() < 5 || post.getContent().strip().length() < 80) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Title must contain at least 5 characters and content at least 80 characters before publication.");
        }
    }
    private void requireStatus(BlogPost post, BlogStatus status) {
        if (post.getStatus() != status) throw new AppException(ErrorCode.BLOG_STATE_CONFLICT);
    }
    private void apply(BlogPost post, PostInput input) {
        post.setTitle(input.title().strip());
        post.setExcerpt(input.excerpt() == null ? "" : input.excerpt().strip());
        post.setContent(input.content() == null ? "" : input.content().strip());
        post.setCategory(input.category());
        post.setCoverImage(cover(input.coverImage()));
        post.setVideoUrl(input.videoUrl() == null || input.videoUrl().isBlank() ? null : input.videoUrl().strip());
    }
    private String cover(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.strip();
        if (trimmed.matches("/images/[A-Za-z0-9._-]+")) return trimmed;
        try {
            URI uri = URI.create(trimmed);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cover image must be an HTTP(S) URL without credentials or a local /images/ path.");
        }
    }
    private PostResponse response(BlogPost p, boolean content, boolean privateView) {
        int words = p.getContent().isBlank() ? 0 : p.getContent().strip().split("\\s+").length;
        return new PostResponse(p.getId(), p.getAuthor().getUserId(), access.name(p.getAuthor()), p.getTitle(), p.getExcerpt(),
                content ? p.getContent() : null, p.getCoverImage(), p.getVideoUrl(),
                p.getRatingAverage() == null ? 5.0 : p.getRatingAverage(),
                p.getRatingCount() == null ? 0 : p.getRatingCount(),
                p.getCategory(), p.getStatus(), p.getVersion(),
                Math.max(1, (int) Math.ceil(words / 200.0)), p.getCreatedAt(), p.getUpdatedAt(),
                privateView ? p.getSubmittedAt() : null, privateView ? p.getReviewedAt() : null, p.getPublishedAt(),
                privateView && p.getReviewer() != null ? access.name(p.getReviewer()) : null,
                privateView ? p.getModerationReason() : null,
                privateView && p.getAiModerationStatus() != null ? p.getAiModerationStatus().name() : null,
                privateView ? p.getAiModerationReason() : null);
    }
}
