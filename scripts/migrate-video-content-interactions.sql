-- Allow the shared interaction tables to store ratings and comments for videos.
ALTER TABLE content_ratings
    DROP CONSTRAINT IF EXISTS content_ratings_target_type_check;
ALTER TABLE content_ratings
    ADD CONSTRAINT content_ratings_target_type_check
    CHECK (target_type IN ('BLOG', 'RECIPE', 'VIDEO'));

ALTER TABLE content_comments
    DROP CONSTRAINT IF EXISTS content_comments_target_type_check;
ALTER TABLE content_comments
    ADD CONSTRAINT content_comments_target_type_check
    CHECK (target_type IN ('BLOG', 'RECIPE', 'VIDEO'));
