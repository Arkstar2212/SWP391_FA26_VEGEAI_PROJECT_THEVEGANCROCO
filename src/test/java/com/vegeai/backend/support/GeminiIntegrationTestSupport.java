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
            JsonNode contents = request.path("contents");
            JsonNode parts = contents.path(contents.size() - 1).path("parts");
            String prompt = parts.path(0).path("text").asText();
            if (prompt.contains("fixture:rate-limit") || prompt.contains("fixture:auth-failed")) {
                send(exchange, prompt.contains("fixture:rate-limit") ? 429 : 403,
                        Map.of("error", Map.of("message", "Test provider failure")));
                return;
            }
            Object reply;
            if (parts.path(1).has("inlineData")) {
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
                reply = Map.of("reply", "Test plant-based advice", "recommendedRecipes", List.of("Canh rau"));
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
