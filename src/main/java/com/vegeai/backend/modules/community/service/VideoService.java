package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.*;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.user.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional
public class VideoService {
    private final VideoRepository videos;
    private final VideoCommentRepository comments;
    private final VideoFavoriteRepository favorites;
    private final CommunityAccess access;
    private final NotificationService notificationService;
    private final com.vegeai.backend.modules.ai.service.GeminiAiService geminiAiService;

    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> published(String keyword, String category, int page, int size) {
        return list(VideoStatus.PUBLISHED, keyword, category, page, size);
    }

    @Transactional(readOnly = true)
    public VideoResponse published(UUID id) { return response(visible(id)); }

    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> mine(String principal, int page, int size) {
        User user = access.user(principal);
        Specification<CommunityVideo> specification = (root, query, cb) -> cb.equal(root.get("createdBy").get("userId"), user.getUserId());
        return PageResponse.from(videos.findAll(specification, access.page(page, size)).map(this::response));
    }

    @Transactional(readOnly = true)
    public VideoResponse mineDetail(String principal, UUID id) {
        User user = access.user(principal);
        CommunityVideo video = videos.findById(id).orElseThrow(() -> new AppException(ErrorCode.VIDEO_NOT_FOUND));
        if (!video.getCreatedBy().getUserId().equals(user.getUserId())) throw new AppException(ErrorCode.VIDEO_NOT_FOUND);
        return response(video);
    }

    public VideoResponse createMember(String principal, VideoInput input) {
        User user = access.user(principal);
        CommunityVideo video = new CommunityVideo();
        video.setCreatedBy(user);
        apply(video, input);
        if (video.getStatus() != VideoStatus.DRAFT) video.setStatus(VideoStatus.PENDING_REVIEW);
        return response(videos.saveAndFlush(video));
    }

    public VideoResponse updateMember(String principal, UUID id, VideoInput input) {
        User user = access.user(principal);
        CommunityVideo video = locked(id);
        if (!video.getCreatedBy().getUserId().equals(user.getUserId())) throw new AppException(ErrorCode.VIDEO_NOT_FOUND);
        access.revision(video.getVersion(), input.version());
        apply(video, input);
        if (video.getStatus() != VideoStatus.DRAFT) video.setStatus(VideoStatus.PENDING_REVIEW);
        videos.flush();
        return response(video);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public PageResponse<VideoResponse> adminList(String principal, VideoStatus status, String keyword, String category, int page, int size) {
        access.admin(principal);
        return list(status, keyword, category, page, size);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public VideoResponse adminDetail(String principal, UUID id) {
        access.admin(principal);
        return response(videos.findById(id).orElseThrow(() -> new AppException(ErrorCode.VIDEO_NOT_FOUND)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse create(String principal, VideoInput input) {
        User admin = access.admin(principal);
        CommunityVideo video = new CommunityVideo();
        video.setCreatedBy(admin);
        apply(video, input);
        return response(videos.saveAndFlush(video));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse update(String principal, UUID id, VideoInput input) {
        access.admin(principal);
        CommunityVideo video = locked(id);
        access.revision(video.getVersion(), input.version());
        apply(video, input);
        videos.flush();
        return response(video);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse reviewAiFlag(String principal, UUID id, ReviewInput input) {
        access.admin(principal);
        CommunityVideo video = locked(id);
        access.revision(video.getVersion(), input.version());
        boolean approve = input.decision() == Decision.APPROVE;
        video.setStatus(approve ? VideoStatus.PUBLISHED : VideoStatus.REJECTED);
        video.setAiModerationAgreedWithAdmin(video.getAiModerationStatus() == AiModerationStatus.FLAGGED ? !approve : approve);
        videos.flush();
        return response(video);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse archive(String principal, UUID id, long version) {
        access.admin(principal);
        CommunityVideo video = locked(id);
        access.revision(video.getVersion(), version);
        video.setStatus(VideoStatus.ARCHIVED);
        videos.flush();
        return response(video);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse restore(String principal, UUID id, long version) {
        access.admin(principal);
        CommunityVideo video = locked(id);
        access.revision(video.getVersion(), version);
        video.setStatus(VideoStatus.PUBLISHED);
        videos.flush();
        return response(video);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String principal, UUID id, long version) {
        access.admin(principal);
        CommunityVideo video = locked(id);
        access.revision(video.getVersion(), version);
        comments.deleteAllForVideo(id);
        favorites.deleteAllForVideo(id);
        videos.unlinkRecipes(id);
        videos.delete(video);
        videos.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(UUID id, int page, int size) {
        visible(id);
        var pageable = access.page(page, size).withSort(Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        return PageResponse.from(comments.findByVideoId(id, pageable).map(this::response));
    }

    public CommentResponse comment(String principal, UUID id, CommentInput input) {
        User author = access.user(principal);
        CommunityVideo video = locked(id);
        requirePublished(video);
        VideoComment comment = new VideoComment();
        comment.setAuthor(author);
        comment.setVideo(video);
        comment.setContent(input.content().strip());
        VideoComment saved = comments.saveAndFlush(comment);
        // Notify the video owner about another member's comment, but do not notify authors about their own comments.
        if (!video.getCreatedBy().getUserId().equals(author.getUserId())) {
            notificationService.notifyUser(video.getCreatedBy(), "VIDEO_COMMENT_ADDED", "Video có bình luận mới",
                    "Một thành viên vừa bình luận trên video của bạn.", "/videos/" + video.getId());
        }
        return response(saved);
    }

    public CommentResponse editComment(String principal, UUID videoId, UUID commentId, CommentInput input) {
        User author = access.user(principal);
        requirePublished(locked(videoId));
        VideoComment comment = comments.findByIdAndVideoId(commentId, videoId)
                .filter(c -> c.getAuthor().getUserId().equals(author.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.VIDEO_COMMENT_NOT_FOUND));
        access.revision(comment.getVersion(), input.version());
        comment.setContent(input.content().strip());
        comments.flush();
        return response(comment);
    }

    public void deleteComment(String principal, UUID videoId, UUID commentId, long version) {
        User user = access.user(principal);
        CommunityVideo video = locked(videoId);
        if (user.getRole() != UserRole.ADMIN) requirePublished(video);
        VideoComment comment = comments.findByIdAndVideoId(commentId, videoId)
                .filter(c -> user.getRole() == UserRole.ADMIN || c.getAuthor().getUserId().equals(user.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.VIDEO_COMMENT_NOT_FOUND));
        access.revision(comment.getVersion(), version);
        comments.delete(comment);
        comments.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> favorites(String principal, int page, int size) {
        User user = access.user(principal);
        return PageResponse.from(favorites.findSaved(user.getUserId(), VideoStatus.PUBLISHED,
                access.page(page, size).withSort(Sort.unsorted())).map(this::response));
    }

    @Transactional(readOnly = true)
    public FavoriteResponse favorite(String principal, UUID id) {
        User user = access.user(principal);
        visible(id);
        return new FavoriteResponse(id, favorites.findByVideoIdAndUserUserId(id, user.getUserId()).isPresent());
    }

    public FavoriteResponse saveFavorite(String principal, UUID id) {
        User user = access.user(principal);
        CommunityVideo video = locked(id);
        requirePublished(video);
        boolean addedFavorite = favorites.findByVideoIdAndUserUserId(id, user.getUserId()).isEmpty();
        if (addedFavorite) {
            VideoFavorite favorite = new VideoFavorite();
            favorite.setUser(user);
            favorite.setVideo(video);
            favorites.saveAndFlush(favorite);
            // Notify the video owner once for each new favorite, never for duplicate requests or self-favorites.
            if (!video.getCreatedBy().getUserId().equals(user.getUserId())) {
                notificationService.notifyUser(video.getCreatedBy(), "VIDEO_FAVORITED", "Video của bạn được lưu",
                        "Một thành viên đã lưu video của bạn vào danh sách yêu thích.", "/videos/" + video.getId());
            }
        }
        return new FavoriteResponse(id, true);
    }

    public FavoriteResponse removeFavorite(String principal, UUID id) {
        User user = access.user(principal);
        locked(id);
        favorites.findByVideoIdAndUserUserId(id, user.getUserId()).ifPresent(favorites::delete);
        favorites.flush();
        return new FavoriteResponse(id, false);
    }

    private PageResponse<VideoResponse> list(VideoStatus status, String keyword, String category, int page, int size) {
        String pattern = access.keyword(keyword);
        if (category != null && category.length() > 80) throw new AppException(ErrorCode.VALIDATION_ERROR);
        Specification<CommunityVideo> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (category != null && !category.isBlank()) specification = specification.and((root, query, cb) -> cb.equal(cb.lower(root.get("category")), category.strip().toLowerCase(java.util.Locale.ROOT)));
        if (pattern != null) specification = specification.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'), cb.like(cb.lower(root.get("description")), pattern, '\\')));
        return PageResponse.from(videos.findAll(specification, access.page(page, size)).map(this::response));
    }

    private CommunityVideo visible(UUID id) {
        CommunityVideo video = videos.findById(id).orElseThrow(() -> new AppException(ErrorCode.VIDEO_NOT_FOUND));
        requirePublished(video);
        return video;
    }

    private CommunityVideo locked(UUID id) { return videos.findForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.VIDEO_NOT_FOUND)); }
    private void requirePublished(CommunityVideo video) {
        if (video.getStatus() != VideoStatus.PUBLISHED) throw new AppException(ErrorCode.VIDEO_NOT_FOUND);
    }

    private void apply(CommunityVideo video, VideoInput input) {
        video.setTitle(input.title().strip());
        video.setDescription(input.description());
        video.setVideoUrl(url(input.videoUrl()));
        video.setThumbnailUrl(input.thumbnailUrl() == null || input.thumbnailUrl().isBlank() ? null : url(input.thumbnailUrl()));
        video.setCategory(input.category() == null ? null : input.category().strip());
        video.setDurationSeconds(input.durationSeconds());
        var verdict = geminiAiService.moderateContent(input.title(), (input.description() == null ? "" : input.description()) + "\nURL: " + input.videoUrl());
        video.setAiModerationStatus(verdict.unavailable() ? AiModerationStatus.UNAVAILABLE
                : verdict.flagged() ? AiModerationStatus.FLAGGED : AiModerationStatus.SAFE);
        video.setAiModerationReason(verdict.reason());
        video.setAiModerationAgreedWithAdmin(null);
        video.setStatus((verdict.flagged() || verdict.unavailable()) && input.status() == VideoStatus.PUBLISHED
                ? VideoStatus.PENDING_REVIEW : input.status());
    }

    private String url(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.matches("/api/public/community-media/[a-f0-9-]{36}\\.(jpg|png|webp|mp4|webm)")) return normalized;
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            return uri.toString();
        } catch (IllegalArgumentException ex) { throw new AppException(ErrorCode.VALIDATION_ERROR, "Use an absolute HTTP(S) URL without credentials."); }
    }

    private VideoResponse response(CommunityVideo v) {
        return new VideoResponse(v.getId(), v.getTitle(), v.getDescription(), v.getVideoUrl(), v.getThumbnailUrl(),
                v.getCategory(), v.getDurationSeconds(), v.getStatus(), v.getAiModerationStatus(),
                v.getAiModerationReason(), v.getAiModerationAgreedWithAdmin(), access.name(v.getCreatedBy()),
                v.getCreatedAt(), v.getUpdatedAt(), v.getVersion());
    }

    private CommentResponse response(VideoComment c) {
        return new CommentResponse(c.getId(), c.getVideo().getId(), c.getAuthor().getUserId(), access.name(c.getAuthor()),
                c.getContent(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }
}
