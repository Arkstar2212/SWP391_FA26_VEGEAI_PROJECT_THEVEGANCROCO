package com.example.swp391_fa26_vegeai_project.modules.ai.entity;

import com.example.swp391_fa26_vegeai_project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "ai_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditLog extends BaseEntity {

    private Long userId;
    private String featureType; // CHATBOT, INGREDIENT_VISION, RECIPE_RECOMMENDATION, MODERATION
    private String modelProvider; // GOOGLE_GEMINI, OPENAI
    private String modelVersion;

    @Column(length = 2000)
    private String promptSummary;

    private Integer tokensConsumed;
    private Long latencyMs;
    private String moderationStatus; // PASSED, FLAGGED, BLOCKED
}
