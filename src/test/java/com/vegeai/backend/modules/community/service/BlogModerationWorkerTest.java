package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.modules.ai.dto.BlogModerationResult;
import com.vegeai.backend.modules.ai.service.GeminiAiService;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogModerationWorkerTest {
    @Mock private BlogPostRepository posts;
    @Mock private GeminiAiService geminiAiService;
    @Mock private BlogModerationResultService results;
    @InjectMocks private BlogModerationWorker worker;

    @Test
    void sendsPersistedCheckingPostToTheProviderAndStoresItsResult() {
        BlogPost post = checkingPost();
        BlogModerationResult result = new BlogModerationResult(true, "Review this content");
        when(posts.findAvailableModerationJobs(eq(BlogStatus.PENDING_REVIEW), eq(AiModerationStatus.CHECKING),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(post));
        when(posts.claimModerationJob(eq(post.getId()), eq(post.getVersion()), eq(BlogStatus.PENDING_REVIEW),
                eq(AiModerationStatus.CHECKING), any(UUID.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(geminiAiService.moderateBlog(post.getTitle(), post.getExcerpt(), post.getContent())).thenReturn(result);

        worker.processNext();

        ArgumentCaptor<UUID> claimToken = ArgumentCaptor.forClass(UUID.class);
        verify(posts).claimModerationJob(eq(post.getId()), eq(post.getVersion()), eq(BlogStatus.PENDING_REVIEW),
                eq(AiModerationStatus.CHECKING), claimToken.capture(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(results).complete(post.getId(), post.getVersion(), claimToken.getValue(), result);
        verify(results, never()).markUnavailable(any(), anyLong(), any(UUID.class));
    }

    @Test
    void convertsProviderFailureToManualReviewWithoutChangingTheSubmissionState() {
        BlogPost post = checkingPost();
        when(posts.findAvailableModerationJobs(eq(BlogStatus.PENDING_REVIEW), eq(AiModerationStatus.CHECKING),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(post));
        when(posts.claimModerationJob(eq(post.getId()), eq(post.getVersion()), eq(BlogStatus.PENDING_REVIEW),
                eq(AiModerationStatus.CHECKING), any(UUID.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(geminiAiService.moderateBlog(post.getTitle(), post.getExcerpt(), post.getContent()))
                .thenThrow(new IllegalStateException("Provider unavailable"));

        worker.processNext();

        verify(results).markUnavailable(eq(post.getId()), eq(post.getVersion()), any(UUID.class));
        verify(results, never()).complete(eq(post.getId()), anyLong(), any(UUID.class), any());
    }

    @Test
    void doesNothingWhenTheDurableQueueIsEmpty() {
        when(posts.findAvailableModerationJobs(eq(BlogStatus.PENDING_REVIEW), eq(AiModerationStatus.CHECKING),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of());

        worker.processNext();

        verifyNoInteractions(geminiAiService, results);
    }

    @Test
    void doesNotCallProviderWhenAnotherWorkerWinsTheClaim() {
        BlogPost post = checkingPost();
        when(posts.findAvailableModerationJobs(eq(BlogStatus.PENDING_REVIEW), eq(AiModerationStatus.CHECKING),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(post));
        when(posts.claimModerationJob(eq(post.getId()), eq(post.getVersion()), eq(BlogStatus.PENDING_REVIEW),
                eq(AiModerationStatus.CHECKING), any(UUID.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

        worker.processNext();

        verifyNoInteractions(geminiAiService, results);
    }

    private BlogPost checkingPost() {
        BlogPost post = new BlogPost();
        post.setId(UUID.randomUUID());
        post.setTitle("A community story");
        post.setExcerpt("Short summary");
        post.setContent("Full submission");
        post.setStatus(BlogStatus.PENDING_REVIEW);
        post.setAiModerationStatus(AiModerationStatus.CHECKING);
        return post;
    }
}
