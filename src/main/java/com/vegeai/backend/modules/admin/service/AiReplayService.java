package com.vegeai.backend.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.admin.dto.AiMonitoringDtos.ReplayResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Admin "Replay" test: sends a fixed probe prompt to the model of a recorded event to check the provider path
 * end to end. User content is never stored, so the original request itself cannot be re-sent.
 */
@Service
@RequiredArgsConstructor
public class AiReplayService {

    public static final String OPERATION = "REPLAY_TEST";
    private static final String PROBE_PROMPT = "Kiểm tra kết nối VEGEAI. Trả lời đúng một từ: OK";

    private final AiAuditService aiAuditService;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    public ReplayResult probe(java.util.UUID sourceRequestId, String model) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your_")) {
            return new ReplayResult(sourceRequestId, "FAILED", 0, null, "AI_NOT_CONFIGURED",
                    "Chưa cấu hình GEMINI_API_KEY nên không thể chạy kiểm thử.");
        }
        long startedAt = System.nanoTime();
        try {
            Integer tokens = aiAuditService.track(OPERATION, model, "SYSTEM", () -> callProvider(model));
            return new ReplayResult(sourceRequestId, "SUCCESS", elapsed(startedAt), tokens, null,
                    "Model " + model + " phản hồi bình thường.");
        } catch (Exception exception) {
            String errorType = exception instanceof RestClientResponseException response
                    ? "HTTP_" + response.getStatusCode().value()
                    : NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName();
            return new ReplayResult(sourceRequestId, "FAILED", elapsed(startedAt), null, errorType,
                    "Model " + model + " không phản hồi thành công (" + errorType + ").");
        }
    }

    private Integer callProvider(String model) throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        String response = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build()
                .post().uri("/models/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey.trim()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", PROBE_PROMPT)))),
                        "generationConfig", Map.of("temperature", 0)))
                .retrieve().body(String.class);
        var usage = objectMapper.readTree(response == null ? "{}" : response).path("usageMetadata");
        AiCallContext.recordUsage(usage);
        return usage.has("totalTokenCount") ? usage.path("totalTokenCount").asInt() : null;
    }

    private static long elapsed(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
