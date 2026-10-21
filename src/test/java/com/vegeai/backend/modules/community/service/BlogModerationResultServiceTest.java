package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.modules.ai.dto.BlogModerationResult;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogModerationResultServiceTest {
    @Mock private BlogPostRepository posts;
    private static final UUID CLAIM_TOKEN = UUID.randomUUID();

    @Test
    void recordsProviderResultOnlyForTheCurrentPendingVersion() {
        BlogPost post = checkingPost();
        when(posts.findForUpdate(post.getId())).thenReturn(Optional.of(post));
        BlogModerationResultService service = new BlogModerationResultService(posts);

        service.complete(post.getId(), 0, CLAIM_TOKEN, new BlogModerationResult(true, "Review this content"));

        assertThat(post.getAiModerationStatus()).isEqualTo(AiModerationStatus.FLAGGED);
        assertThat(post.getAiModerationReason()).isEqualTo("Review this content");
        assertThat(post.getAiModerationClaimToken()).isNull();
        assertThat(post.getAiModerationClaimedAt()).isNull();
    }

    @Test
    void ignoresLateResultsAfterAnAdminDecision() {
        BlogPost post = checkingPost();
        post.setStatus(BlogStatus.PUBLISHED);
        post.setAiModerationStatus(AiModerationStatus.NOT_CHECKED);
        when(posts.findForUpdate(post.getId())).thenReturn(Optional.of(post));
        BlogModerationResultService service = new BlogModerationResultService(posts);

        service.complete(post.getId(), 0, CLAIM_TOKEN, new BlogModerationResult(true, "Review this content"));

        assertThat(post.getStatus()).isEqualTo(BlogStatus.PUBLISHED);
        assertThat(post.getAiModerationStatus()).isEqualTo(AiModerationStatus.NOT_CHECKED);
    }

    @Test
    void leavesContentEditsQueuedForAFreshModerationPass() {
        BlogPost post = checkingPost();
        post.setVersion(1);
        when(posts.findForUpdate(post.getId())).thenReturn(Optional.of(post));
        BlogModerationResultService service = new BlogModerationResultService(posts);

        service.complete(post.getId(), 0, CLAIM_TOKEN, new BlogModerationResult(false, null));

        assertThat(post.getAiModerationStatus()).isEqualTo(AiModerationStatus.CHECKING);
        assertThat(post.getAiModerationReason()).isEqualTo("AI đang kiểm tra bài viết; Admin vẫn là người quyết định cuối cùng.");
    }

    @Test
    void recordsUnavailableOnlyForTheVersionThatWasActuallyChecked() {
        BlogPost post = checkingPost();
        when(posts.findForUpdate(post.getId())).thenReturn(Optional.of(post));
        BlogModerationResultService service = new BlogModerationResultService(posts);

        service.markUnavailable(post.getId(), 0, CLAIM_TOKEN);

        assertThat(post.getAiModerationStatus()).isEqualTo(AiModerationStatus.UNAVAILABLE);
        assertThat(post.getAiModerationReason()).contains("cần Admin xem xét thủ công");
        assertThat(post.getAiModerationClaimToken()).isNull();
        assertThat(post.getAiModerationClaimedAt()).isNull();
    }

    @Test
    void ignoresResultFromAnExpiredClaimAfterAnotherWorkerReclaimsThePost() {
        BlogPost post = checkingPost();
        post.setAiModerationClaimToken(UUID.randomUUID());
        when(posts.findForUpdate(post.getId())).thenReturn(Optional.of(post));
        BlogModerationResultService service = new BlogModerationResultService(posts);

        service.complete(post.getId(), 0, CLAIM_TOKEN, new BlogModerationResult(true, "Stale result"));

        assertThat(post.getAiModerationStatus()).isEqualTo(AiModerationStatus.CHECKING);
        assertThat(post.getAiModerationClaimToken()).isNotEqualTo(CLAIM_TOKEN);
    }

    private BlogPost checkingPost() {
        BlogPost post = new BlogPost();
        post.setId(UUID.randomUUID());
        post.setStatus(BlogStatus.PENDING_REVIEW);
        post.setAiModerationStatus(AiModerationStatus.CHECKING);
        post.setAiModerationReason("AI đang kiểm tra bài viết; Admin vẫn là người quyết định cuối cùng.");
        post.setAiModerationClaimToken(CLAIM_TOKEN);
        post.setAiModerationClaimedAt(LocalDateTime.now());
        return post;
    }
}
