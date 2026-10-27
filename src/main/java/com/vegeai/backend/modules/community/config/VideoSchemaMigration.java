package com.vegeai.backend.modules.community.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps existing Docker database volumes compatible with video moderation fields. */
@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VideoSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public VideoSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("ALTER TABLE community_videos ADD COLUMN IF NOT EXISTS ai_moderation_status VARCHAR(20)");
        jdbc.execute("ALTER TABLE community_videos ADD COLUMN IF NOT EXISTS ai_moderation_reason VARCHAR(1000)");
        jdbc.execute("ALTER TABLE community_videos ADD COLUMN IF NOT EXISTS ai_moderation_agreed_with_admin BOOLEAN");
        jdbc.update("UPDATE community_videos SET ai_moderation_status = 'NOT_CHECKED' WHERE ai_moderation_status IS NULL");
        jdbc.execute("ALTER TABLE community_videos ALTER COLUMN ai_moderation_status SET DEFAULT 'NOT_CHECKED'");
        jdbc.execute("ALTER TABLE community_videos ALTER COLUMN ai_moderation_status SET NOT NULL");
        jdbc.execute("ALTER TABLE community_videos DROP CONSTRAINT IF EXISTS community_videos_status_check");
        jdbc.execute("""
                ALTER TABLE community_videos
                ADD CONSTRAINT community_videos_status_check
                CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
                """);
        jdbc.execute("ALTER TABLE community_videos DROP CONSTRAINT IF EXISTS community_videos_ai_moderation_status_check");
        jdbc.execute("""
                ALTER TABLE community_videos
                ADD CONSTRAINT community_videos_ai_moderation_status_check
                CHECK (ai_moderation_status IN ('NOT_CHECKED', 'CHECKING', 'SAFE', 'FLAGGED', 'UNAVAILABLE'))
                """);
    }
}
