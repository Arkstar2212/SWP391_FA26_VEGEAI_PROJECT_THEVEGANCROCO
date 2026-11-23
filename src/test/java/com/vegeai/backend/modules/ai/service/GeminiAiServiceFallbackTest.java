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

    @Test
    void scanFridgeExcludesFridgeInventoryFromPromptAndFixesLactoOvoAndNullReason() throws Exception {
        var userRepo = mock(UserRepository.class);
        var fridgeRepo = mock(FridgeItemRepository.class);
        var userId = java.util.UUID.randomUUID();
        var user = com.vegeai.backend.modules.user.entity.User.builder()
                .userId(userId)
                .username("member")
                .email("member@vegeai.com")
                .fullName("Member")
                .dietaryPreferences("VEGAN")
                .allergies("NONE")
                .build();
        org.mockito.Mockito.when(userRepo.findByUsernameOrEmailIgnoreCase("member")).thenReturn(java.util.Optional.of(user));
        var existing = com.vegeai.backend.modules.fridge.entity.FridgeItem.builder()
                .itemId(java.util.UUID.randomUUID())
                .userId(userId)
                .name("Bí đỏ hồ lô dẻo")
                .normalizedName("bi do ho lo deo")
                .firstScannedAt(java.time.LocalDateTime.now().minusDays(2))
                .expiryDate(java.time.LocalDate.now().plusDays(5))
                .status(com.vegeai.backend.modules.fridge.entity.FridgeItemStatus.ACTIVE)
                .build();
        org.mockito.Mockito.when(fridgeRepo.findByUserIdAndStatusOrderByExpiryDateAsc(
                userId, com.vegeai.backend.modules.fridge.entity.FridgeItemStatus.ACTIVE))
                .thenReturn(List.of(existing));

        var capturedPrompt = new java.util.concurrent.atomic.AtomicReference<String>("");
        String geminiPayload = """
                {
                  "summary": "Có trứng, sữa và bơ",
                  "totalItemsDetected": 3,
                  "hasNonVegetarianItem": false,
                  "ingredients": [
                    { "name": "Trứng gà", "category": "VEGAN", "isVegetarian": true, "rejectionReason": "null", "freshness": "FRESH", "freshnessScore": 0.9 },
                    { "name": "Sữa hạnh nhân", "category": "VEGAN", "isVegetarian": true, "freshness": "FRESH", "freshnessScore": 0.9 },
                    { "name": "Bơ sáp", "category": "VEGAN", "isVegetarian": true, "freshness": "FRESH", "freshnessScore": 0.9 }
                  ],
                  "suggestedRecipes": [],
                  "warnings": []
                }
                """;
        byte[] okResponse = new ObjectMapper().writeValueAsBytes(java.util.Map.of(
                "candidates", List.of(java.util.Map.of("finishReason", "STOP",
                        "content", java.util.Map.of("parts", List.of(java.util.Map.of("text", geminiPayload)))))
        ));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", exchange -> {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            var json = new ObjectMapper().readTree(reqBytes);
            capturedPrompt.set(json.path("contents").path(0).path("parts").path(0).path("text").asText());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, okResponse.length);
            try (var out = exchange.getResponseBody()) {
                out.write(okResponse);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiAiService service = new GeminiAiService(
                    userRepo, fridgeRepo, mock(BlogPostRepository.class), mock(StringRedisTemplate.class),
                    new ObjectMapper(), mock(CookbookService.class), mock(NutritionProfileRepository.class),
                    new AiAuditService(mock(AiAuditLogWriter.class))
            );
            ReflectionTestUtils.setField(service, "apiKey", "AIzaDummyKeyForUnitTest1234567890");
            ReflectionTestUtils.setField(service, "model", "gemini-3.5-flash-lite");
            ReflectionTestUtils.setField(service, "baseUrl", baseUrl);

            var mockImage = new org.springframework.mock.web.MockMultipartFile(
                    "image", "fridge.jpg", "image/jpeg", "bytes".getBytes(StandardCharsets.UTF_8));
            var result = service.scanFridge(mockImage, "member", false);

            assertFalse(capturedPrompt.get().contains("Bí đỏ hồ lô dẻo"));
            assertFalse(capturedPrompt.get().contains("NGUYÊN LIỆU TRONG KHO TỦ LẠNH HIỆN CÓ"));
            assertEquals("LACTO_OVO", result.getIngredients().get(0).getCategory());
            assertNull(result.getIngredients().get(0).getRejectionReason());
            assertEquals("VEGAN", result.getIngredients().get(1).getCategory());
            assertEquals("VEGAN", result.getIngredients().get(2).getCategory());
            assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Lacto-Ovo")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToSecondaryModelOnNetworkDropAnd400ThinkingRejection() throws Exception {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        List<Boolean> sawThinkingConfig = new CopyOnWriteArrayList<>();
        byte[] okResponse = """
                {
                  "candidates": [
                    {
                      "finishReason": "STOP",
                      "content": {
                        "parts": [
                          { "text": "{\\"inScope\\":true,\\"reply\\":\\"Gợi ý đậu hũ kho tiêu\\",\\"recommendedRecipes\\":[]}" }
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
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            var reqJson = new ObjectMapper().readTree(reqBytes);
            boolean hasThinking = reqJson.path("generationConfig").has("thinkingConfig");
            sawThinkingConfig.add(hasThinking);

            if (path.contains("gemini-3.5-flash-lite")) {
                // Close raw exchange without HTTP headers to trigger ResourceAccessException (network drop)
                exchange.close();
            } else if (path.contains("gemini-3.5-flash:") && hasThinking) {
                byte[] err = "{\"error\":{\"code\":400,\"message\":\"Unknown name thinkingLevel\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, err.length);
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
                    AiChatRequest.builder().message("Gợi ý món chay kho").build(),
                    null,
                    true
            );

            assertEquals("Gợi ý đậu hũ kho tiêu", response.getReply());
            assertTrue(requestedPaths.stream().anyMatch(p -> p.contains("gemini-3.5-flash-lite")));
            assertEquals(2, requestedPaths.stream().filter(p -> p.contains("gemini-3.5-flash:")).count());
            assertTrue(sawThinkingConfig.contains(true));
            assertTrue(sawThinkingConfig.contains(false));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void scanFridgeRejectsNonFoodSceneAndFlagsLabelAndFivePungentWithoutFalseNonFood() throws Exception {
        var userRepo = mock(UserRepository.class);
        var fridgeRepo = mock(FridgeItemRepository.class);

        String nonFoodScenePayload = """
                {
                  "isFoodScene": false,
                  "summary": "Đây là ảnh chụp bàn làm việc, không phải tủ lạnh hay nguyên liệu nấu ăn.",
                  "totalItemsDetected": 1,
                  "hasNonVegetarianItem": false,
                  "ingredients": [
                    { "name": "Bàn gỗ", "category": "NON_FOOD", "isVegetarian": false, "isEdible": false, "freshness": "SPOILED" }
                  ],
                  "suggestedRecipes": ["Món không hợp lệ"],
                  "warnings": []
                }
                """;
        String veganSubtypesPayload = """
                {
                  "isFoodScene": true,
                  "summary": "Phát hiện gia vị, tỏi và đá bào ăn kèm chè",
                  "totalItemsDetected": 3,
                  "hasNonVegetarianItem": false,
                  "ingredients": [
                    { "name": "Nước tương Tamari", "quantity": 1, "unit": "chai", "category": "VEGAN", "isVegetarian": true, "freshness": "FRESH", "freshnessScore": 0.9 },
                    { "name": "Tỏi băm", "quantity": 2.4, "unit": "củ", "category": "VEGAN", "isVegetarian": true, "freshness": "FRESH", "freshnessScore": 0.9 },
                    { "name": "Đá bào chè", "category": "VEGAN", "isVegetarian": true, "freshness": "FRESH", "freshnessScore": 0.9 },
                    { "name": "Cà chua bi", "category": "VEGAN", "isVegetarian": true, "freshness": "FRESH", "freshnessScore": 0.9 }
                  ],
                  "suggestedRecipes": [],
                  "warnings": []
                }
                """;
        var callIndex = new java.util.concurrent.atomic.AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String payload = callIndex.getAndIncrement() == 0 ? nonFoodScenePayload : veganSubtypesPayload;
            byte[] okResponse = new ObjectMapper().writeValueAsBytes(java.util.Map.of(
                    "candidates", List.of(java.util.Map.of("finishReason", "STOP",
                            "content", java.util.Map.of("parts", List.of(java.util.Map.of("text", payload)))))
            ));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, okResponse.length);
            try (var out = exchange.getResponseBody()) {
                out.write(okResponse);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiAiService service = new GeminiAiService(
                    userRepo, fridgeRepo, mock(BlogPostRepository.class), mock(StringRedisTemplate.class),
                    new ObjectMapper(), mock(CookbookService.class), mock(NutritionProfileRepository.class),
                    new AiAuditService(mock(AiAuditLogWriter.class))
            );
            ReflectionTestUtils.setField(service, "apiKey", "integration-test-only");
            ReflectionTestUtils.setField(service, "model", "test-model");
            ReflectionTestUtils.setField(service, "baseUrl", baseUrl);

            var mockImage = new org.springframework.mock.web.MockMultipartFile(
                    "image", "desk.jpg", "image/jpeg", "desk-bytes".getBytes(StandardCharsets.UTF_8));
            var nonFoodSceneRes = service.scanFridge(mockImage, null, true);
            assertFalse(nonFoodSceneRes.isFoodScene());
            assertTrue(nonFoodSceneRes.getIngredients().isEmpty());
            assertTrue(nonFoodSceneRes.getSuggestedRecipes().isEmpty());

            var foodRes = service.scanFridge(mockImage, null, true);
            assertTrue(foodRes.isFoodScene());
            assertTrue(foodRes.getIngredients().get(0).isNeedsLabelVerification());
            assertEquals("chai", foodRes.getIngredients().get(0).getUnit());
            assertEquals(1.0, foodRes.getIngredients().get(0).getQuantity());
            assertTrue(foodRes.getIngredients().get(1).isContainsFivePungent());
            assertEquals("củ", foodRes.getIngredients().get(1).getUnit());
            assertEquals(2.0, foodRes.getIngredients().get(1).getQuantity());
            assertTrue(foodRes.getIngredients().get(2).isEdible());
            assertEquals("VEGAN", foodRes.getIngredients().get(2).getCategory());
            assertEquals("quả", foodRes.getIngredients().get(3).getUnit());
            assertEquals(1.0, foodRes.getIngredients().get(3).getQuantity());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fridgeScanAssetServiceDeduplicatesIdenticalImagesAndPurgesOnlyUnreferencedExpiredFiles(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        var repo = mock(FridgeItemRepository.class);
        var assetService = new com.vegeai.backend.modules.fridge.service.FridgeScanAssetService(
                tempDir.toString(), repo);

        byte[] imgBytes = "identical-fridge-image-bytes".getBytes(StandardCharsets.UTF_8);
        String url1 = assetService.storeSafely(imgBytes, "image/jpeg");
        String url2 = assetService.storeSafely(imgBytes, "image/jpeg");
        assertNotNull(url1);
        assertEquals(url1, url2);
        try (var list = java.nio.file.Files.list(tempDir)) {
            assertEquals(1, list.count());
        }

        byte[] orphanBytes = "orphan-fridge-image-bytes".getBytes(StandardCharsets.UTF_8);
        String orphanUrl = assetService.storeSafely(orphanBytes, "image/png");
        assertNotNull(orphanUrl);
        org.mockito.Mockito.when(repo.existsByImageUrl(url1)).thenReturn(true);
        org.mockito.Mockito.when(repo.existsByImageUrl(orphanUrl)).thenReturn(false);

        int deleted = assetService.purgeExpiredOrphans(java.time.Duration.ZERO);
        assertEquals(1, deleted);
        String keptFilename = url1.substring(url1.lastIndexOf('/') + 1);
        String purgedFilename = orphanUrl.substring(orphanUrl.lastIndexOf('/') + 1);
        assertTrue(java.nio.file.Files.exists(tempDir.resolve(keptFilename)));
        assertFalse(java.nio.file.Files.exists(tempDir.resolve(purgedFilename)));
    }
}
