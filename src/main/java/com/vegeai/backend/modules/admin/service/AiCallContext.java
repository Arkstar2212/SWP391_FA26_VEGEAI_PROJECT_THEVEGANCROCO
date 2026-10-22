package com.vegeai.backend.modules.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Per-thread metadata for the AI call being audited: the signed-in caller and Gemini token usage.
 * AiAuditService runs the provider call and the audit write on the same thread, so the usage parsed
 * from the response is attached to that call's log entry and then cleared.
 */
public final class AiCallContext {

    private static final ThreadLocal<int[]> USAGE = new ThreadLocal<>();

    private AiCallContext() {}

    /** Records {@code usageMetadata} from a Gemini generateContent response; missing counts are ignored. */
    public static void recordUsage(JsonNode usageMetadata) {
        if (usageMetadata == null || !usageMetadata.isObject()) return;
        int prompt = usageMetadata.path("promptTokenCount").asInt(0);
        int response = usageMetadata.path("candidatesTokenCount").asInt(0);
        int total = usageMetadata.path("totalTokenCount").asInt(prompt + response);
        USAGE.set(new int[]{prompt, response, total});
    }

    /** Copies the caller and token usage onto the entry and clears the per-thread usage. */
    public static void applyTo(AiAuditLog entry) {
        int[] usage = USAGE.get();
        USAGE.remove();
        if (usage != null) {
            entry.setPromptTokens(usage[0]);
            entry.setResponseTokens(usage[1]);
            entry.setTotalTokens(usage[2]);
        }
        entry.setUserId(currentUserId());
    }

    /** The JWT principal is the user's UUID; guests and background jobs have none. */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || auth.getPrincipal() == null) return null;
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
