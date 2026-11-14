package com.vegeai.backend.modules.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.admin.service.AiAuditLogWriter;
import com.vegeai.backend.modules.admin.service.AiAuditService;
import com.vegeai.backend.modules.ai.dto.AiChatRequest;
import com.vegeai.backend.modules.ai.dto.AiChatResponse;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.recipe.service.CookbookService;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.nutrition.repository.NutritionProfileRepository;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GeminiAiServiceFallbackTest {

    private GeminiAiService createService(String baseUrl, String model) {
        GeminiAiService service = new GeminiAiService(
                mock(UserRepository.class),
                mock(FridgeItemRepository.class),
                mock(BlogPostRepository.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(CookbookService.class),
                mock(NutritionProfileRepository.class),
                new AiAuditService(mock(AiAuditLogWriter.class))
        );
        ReflectionTestUtils.setField(service, "apiKey", "AIzaDummyKeyForUnitTest1234567890");
        ReflectionTestUtils.setField(service, "model", model);
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl);
        return service;
    }

    @Test
    void fallsBackToSecondaryModelOn404And429Responses() throws Exception {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        byte[] okResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "{\\"inScope\\":true,\\"reply\\":\\"Gợi ý đậu hũ sốt cà chua\\",\\"recommendedRecipes\\":[]}" }
                        ]
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            exchange.getRequestBody().readAllBytes();
            if (path.contains("gemini-3.5-flash-lite")) {
                byte[] err = "{\"error\":{\"code\":404}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, err.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(err);
                }
            } else if (path.contains("gemini-3.5-flash")) {
                byte[] err = "{\"error\":{\"code\":429}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(429, err.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(err);
                }
            } else {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, okResponse.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(okResponse);
                }
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiAiService service = createService(baseUrl, "gemini-3.5-flash-lite");

            AiChatResponse response = service.processChat(
                    AiChatRequest.builder().message("Tư vấn món đậu hũ chay").build(),
                    null,
                    true
            );

            assertEquals("Gợi ý đậu hũ sốt cà chua", response.getReply());
            assertTrue(requestedPaths.stream().anyMatch(p -> p.contains("gemini-3.5-flash-lite")));
            assertTrue(requestedPaths.stream().anyMatch(p -> p.contains("gemini-3.5-flash:")));
            assertTrue(requestedPaths.stream().anyMatch(p -> p.contains("gemini-3.8-flash")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testModelStopsImmediatelyOnQuotaExceededWithoutFallback() throws Exception {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] err = "{\"error\":{\"code\":429}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, err.length);
            try (var out = exchange.getResponseBody()) {
                out.write(err);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiAiService service = createService(baseUrl, "test-model");

            AppException thrown = assertThrows(AppException.class, () ->
                    service.processChat(AiChatRequest.builder().message("Chào bạn").build(), null, true)
            );
            assertEquals(ErrorCode.AI_QUOTA_EXCEEDED, thrown.getErrorCode());
            assertEquals(1, requestedPaths.size());
        } finally {
            server.stop(0);
        }
    }
}
