package com.vegeai.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/** Runs the real AI service against a loopback provider, without credentials or Google calls. */
public abstract class GeminiIntegrationTestSupport {
    protected static volatile JsonNode lastRequest;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpServer SERVER = startServer();

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.gemini.api-key", () -> "integration-test-only");
        registry.add("app.gemini.model", () -> "test-model");
        registry.add("app.gemini.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1beta");
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1beta/models/test-model:generateContent", GeminiIntegrationTestSupport::respond);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())
                    || !"integration-test-only".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key"))) {
                send(exchange, 403, Map.of("error", Map.of("message", "Invalid test request")));
                return;
            }
            JsonNode request = MAPPER.readTree(exchange.getRequestBody());
            lastRequest = request;
            JsonNode contents = request.path("contents");
            JsonNode parts = contents.path(contents.size() - 1).path("parts");
            String prompt = parts.path(0).path("text").asText();
            if (prompt.contains("fixture:rate-limit") || prompt.contains("fixture:auth-failed")) {
                send(exchange, prompt.contains("fixture:rate-limit") ? 429 : 403,
                        Map.of("error", Map.of("message", "Test provider failure")));
                return;
            }
            Object reply;
            if (prompt.contains("\"task\":\"VEGEAI_MEAL_PLAN\"")) {
                JsonNode data = MAPPER.readTree(prompt);
                List<String> ids = new java.util.ArrayList<>();
                for (JsonNode recipe : data.path("recipes")) ids.add(recipe.path("recipeId").asText());
                if (prompt.contains("fixture:rank-invalid")) ids = List.of(java.util.UUID.randomUUID().toString());
                reply = Map.of("recipeIds", ids);
            } else if (prompt.contains("\"task\":\"VEGEAI_SEASONAL_MEALS\"")) {
                JsonNode data = MAPPER.readTree(prompt);
                String libraryId = data.path("recipes").path(0).path("recipeId").asText(null);
                java.util.function.BiFunction<String, String, Map<String, Object>> dish = (name, ingredients) -> {
                    Map<String, Object> m = new java.util.HashMap<>(Map.of("mealType", "Bữa trưa", "dishName", name,
                            "seasonalIngredients", ingredients, "healthBenefits", "Test benefit", "regionalHighlights", "Test region",
                            "estimatedCalories", 420, "cookingTip", "Test tip"));
                    m.put("recipeId", null);
                    return m;
                };
                var linked = dish.apply("Library dish", "Rau cải"); linked.put("recipeId", libraryId);
                var invented = dish.apply("Invented id dish", "Bí đỏ"); invented.put("recipeId", java.util.UUID.randomUUID().toString());
                reply = Map.of("climateSummary", "Test climate", "highlightedProduce", List.of("Bí đỏ", "Nấm"),
                        "nutritionAdvice", "Test advice",
                        "suggestions", List.of(linked, invented, dish.apply("Plain dish", "Cà rốt"), dish.apply("Soy dish", "Đậu nành SOY")));
            } else if (parts.path(1).has("inlineData")) {
                reply = Map.of("summary", "Test vegetables", "totalItemsDetected", 1,
                        "hasNonVegetarianItem", false,
                        "ingredients", List.of(Map.of("name", "Nấm bào ngư", "category", "VEGAN",
                                "isVegetarian", true, "freshness", "FRESH", "freshnessScore", 0.95)),
                        "suggestedRecipes", List.of("Canh rau"), "warnings", List.of());
            } else if (prompt.contains("[YÊU CẦU TẠO CÔNG THỨC MÓN ĂN]")) {
                reply = Map.of("title", "Canh cải ngọt nấu nấm bào ngư thanh đạm",
                        "dietaryType", "VEGAN", "ingredients", List.of("Cải ngọt", "Nấm bào ngư"),
                        "instructions", List.of("Wash vegetables", "Cook in vegetable broth"),
                        "cookTimeMinutes", 20, "safetyNotes", List.of());
            } else {
                reply = Map.of("inScope", !prompt.contains("fixture:out-of-scope"), "reply", "Test plant-based advice", "recommendedRecipes", List.of("Canh rau"));
            }
            String text = prompt.contains("fixture:invalid-json") ? "not-json" : MAPPER.writeValueAsString(reply);
            send(exchange, 200, Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                    "content", Map.of("parts", List.of(Map.of("text", text)))))));
        }
    }

    private static void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
