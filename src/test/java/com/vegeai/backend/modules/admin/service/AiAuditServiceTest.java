package com.vegeai.backend.modules.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiAuditServiceTest {
    @Mock
    private AiAuditLogWriter writer;

    @Test
    void recordsTheRootCauseTypeForProviderFailures() {
        AiAuditService service = new AiAuditService(writer);
        RestClientException failure = new RestClientException("Provider request failed", new SocketTimeoutException());

        assertThatThrownBy(() -> service.track("CONTENT_MODERATION", "gemini-test", "MEMBER", () -> {
            throw failure;
        })).isSameAs(failure);

        verify(writer).save(eq("CONTENT_MODERATION"), eq("gemini-test"), eq("MEMBER"), eq("FAILED"),
                isNull(), anyLong(), eq("SocketTimeoutException"));
    }

    @Test
    void recordsHttpStatusForProviderResponseFailures() {
        AiAuditService service = new AiAuditService(writer);
        HttpClientErrorException failure = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Quota exceeded", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.track("CONTENT_MODERATION", "gemini-test", "MEMBER", () -> {
            throw failure;
        })).isSameAs(failure);

        verify(writer).save(eq("CONTENT_MODERATION"), eq("gemini-test"), eq("MEMBER"), eq("FAILED"),
                isNull(), anyLong(), eq("HTTP_429"));
    }
}
