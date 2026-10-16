package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.community.dto.BlogDtos.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional
public class BlogService {
    private final BlogPostRepository posts;
    private final CommunityAccess access;

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
        post.setReviewer(directPublish ? post.getAuthor() : null);
        post.setReviewedAt(directPublish ? LocalDateTime.now() : null);
        post.setPublishedAt(directPublish ? LocalDateTime.now() : null);
        post.setModerationReason(null);
        posts.flush();
        return response(post, true, true);
    }

    public PostResponse withdraw(String principal, UUID id, long version) {
        BlogPost post = ownedForUpdate(principal, id, version);
        requireStatus(post, BlogStatus.PENDING_REVIEW);
        post.setStatus(BlogStatus.DRAFT);
        post.setSubmittedAt(null);
        posts.flush();
        return response(post, true, true);
    }

    public void delete(String principal, UUID id, Long version) {
        BlogPost post = ownedForUpdate(principal, id, version);
        requireEditable(post);
        posts.delete(post);
        posts.flush();
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public PageResponse<PostResponse> queue(String principal, BlogStatus status, String keyword, int page, int size) {
        access.admin(principal);
        return list(null, status, keyword, null, page, size, true);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public PostResponse reviewPost(String principal, UUID id) {
        access.admin(principal);
        return response(find(id), true, true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PostResponse review(String principal, UUID id, ReviewInput input) {
        User admin = access.admin(principal);
        BlogPost post = locked(id);
        access.revision(post.getVersion(), input.version());
        requireStatus(post, BlogStatus.PENDING_REVIEW);
        String reason = input.reason() == null ? "" : input.reason().strip();
        if (input.decision() == Decision.REJECT && reason.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A rejection reason is required.");
        }
        boolean approve = input.decision() == Decision.APPROVE;
        post.setStatus(approve ? BlogStatus.PUBLISHED : BlogStatus.REJECTED);
        post.setReviewer(admin);
        post.setReviewedAt(LocalDateTime.now());
        post.setModerationReason(approve ? null : reason);
        post.setPublishedAt(approve ? LocalDateTime.now() : null);
        posts.flush();
        return response(post, true, true);
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
                content ? p.getContent() : null, p.getCoverImage(), p.getCategory(), p.getStatus(), p.getVersion(),
                Math.max(1, (int) Math.ceil(words / 200.0)), p.getCreatedAt(), p.getUpdatedAt(),
                privateView ? p.getSubmittedAt() : null, privateView ? p.getReviewedAt() : null, p.getPublishedAt(),
                privateView && p.getReviewer() != null ? access.name(p.getReviewer()) : null,
                privateView ? p.getModerationReason() : null);
    }
}
