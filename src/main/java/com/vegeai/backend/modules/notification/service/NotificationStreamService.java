package com.vegeai.backend.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one Server-Sent Events connection per signed-in browser so the notification bell can update
 * without polling. Emitters are per-instance (single node deployment); a load-balanced deployment would
 * need a Redis pub/sub fan-out, which is documented as a future step.
 */
@Service
@Slf4j
public class NotificationStreamService {
    private static final long CONNECTION_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        connections.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("connected", true)));
        } catch (IOException exception) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Pushes a freshly stored notification to every tab the recipient has open. */
    public void publish(UUID userId, Object payload) {
        Set<SseEmitter> userEmitters = connections.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) return;
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception exception) {
                remove(userId, emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already gone
                }
            }
        }
    }

    /** Keeps proxies from closing idle streams. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        connections.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception exception) {
                    remove(userId, emitter);
                }
            }
        });
    }

    public int connectionCount() {
        return connections.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = connections.get(userId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) connections.remove(userId);
    }
}
