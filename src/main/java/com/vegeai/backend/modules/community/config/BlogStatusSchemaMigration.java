package com.vegeai.backend.modules.community.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BlogStatusSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public BlogStatusSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("ALTER TABLE blog_posts DROP CONSTRAINT IF EXISTS blog_posts_status_check");
        jdbc.execute("""
                ALTER TABLE blog_posts
                ADD CONSTRAINT blog_posts_status_check
                CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
                """);
    }
}
