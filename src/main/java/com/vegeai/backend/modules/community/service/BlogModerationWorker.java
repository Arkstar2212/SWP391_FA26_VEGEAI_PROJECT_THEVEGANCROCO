package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.modules.ai.dto.BlogModerationResult;
import com.vegeai.backend.modules.ai.service.GeminiAiService;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Drains persisted CHECKING posts outside the submission request and its database transaction.
 * The database state makes pending work discoverable again after a backend restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlogModerationWorker {
    // Longer than the provider timeout so a slow request is not reclaimed by another instance.
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

    private final BlogPostRepository posts;
    private final GeminiAiService geminiAiService;
    private final BlogModerationResultService results;

    @Scheduled(fixedDelay = 1000)
    public void processNext() {
        LocalDateTime claimedAt = LocalDateTime.now();
        LocalDateTime expiredBefore = claimedAt.minus(CLAIM_LEASE);
        Optional<BlogPost> next = posts.findAvailableModerationJobs(
                BlogStatus.PENDING_REVIEW, AiModerationStatus.CHECKING, expiredBefore, PageRequest.of(0, 1))
                .stream().findFirst();
        next.ifPresent(post -> moderate(post, claimedAt, expiredBefore));
    }

    private void moderate(BlogPost post, LocalDateTime claimedAt, LocalDateTime expiredBefore) {
        UUID postId = post.getId();
        long version = post.getVersion();
        UUID claimToken = UUID.randomUUID();
        int claimed = posts.claimModerationJob(postId, version, BlogStatus.PENDING_REVIEW,
                AiModerationStatus.CHECKING, claimToken, claimedAt, expiredBefore);
        if (claimed != 1) return;

        BlogModerationResult result;
        try {
            result = geminiAiService.moderateBlog(post.getTitle(), post.getExcerpt(), post.getContent());
        } catch (RuntimeException exception) {
            results.markUnavailable(postId, version, claimToken);
            log.warn("[BLOG-MODERATION] AI check unavailable for post {} ({})",
                    postId, exception.getClass().getSimpleName());
            return;
        }

        results.complete(postId, version, claimToken, result);
    }
}
