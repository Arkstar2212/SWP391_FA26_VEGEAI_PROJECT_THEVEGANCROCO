package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.modules.ai.dto.BlogModerationResult;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogModerationResultService {
    private static final String UNAVAILABLE_REASON =
            "AI không thể kiểm tra bài viết; cần Admin xem xét thủ công.";

    private final BlogPostRepository posts;

    @Transactional
    public void complete(UUID postId, long expectedVersion, UUID claimToken, BlogModerationResult result) {
        posts.findForUpdate(postId).ifPresent(post -> {
            if (!isCurrentCheck(post, expectedVersion, claimToken)) return;

            post.setAiModerationStatus(result.flagged() ? AiModerationStatus.FLAGGED : AiModerationStatus.SAFE);
            post.setAiModerationReason(result.flagged() ? flaggedReason(result.reason()) : null);
            post.clearAiModerationClaim();
        });
    }

    @Transactional
    public void markUnavailable(UUID postId, long expectedVersion, UUID claimToken) {
        posts.findForUpdate(postId).ifPresent(post -> {
            if (!isCurrentCheck(post, expectedVersion, claimToken)) return;

            post.setAiModerationStatus(AiModerationStatus.UNAVAILABLE);
            post.setAiModerationReason(UNAVAILABLE_REASON);
            post.clearAiModerationClaim();
        });
    }

    private boolean isCurrentCheck(BlogPost post, long expectedVersion, UUID claimToken) {
        // The lock plus status/version guard prevents a slow provider response replacing newer human work.
        return post.getStatus() == BlogStatus.PENDING_REVIEW
                && post.getAiModerationStatus() == AiModerationStatus.CHECKING
                && post.getVersion() == expectedVersion
                && claimToken.equals(post.getAiModerationClaimToken());
    }

    private String flaggedReason(String reason) {
        String value = reason == null || reason.isBlank()
                ? "AI đề nghị Admin xem xét kỹ hơn."
                : reason.strip();
        return value.substring(0, Math.min(value.length(), 1000));
    }
}
