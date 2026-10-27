ALTER TABLE content_comments
    ADD COLUMN IF NOT EXISTS public_display_name BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS review_stars INTEGER;

CREATE TABLE IF NOT EXISTS content_comment_tags (
    comment_id UUID NOT NULL REFERENCES content_comments(id) ON DELETE CASCADE,
    tag_order INTEGER NOT NULL,
    tag VARCHAR(40) NOT NULL,
    PRIMARY KEY (comment_id, tag_order)
);

CREATE TABLE IF NOT EXISTS content_comment_photos (
    comment_id UUID NOT NULL REFERENCES content_comments(id) ON DELETE CASCADE,
    photo_order INTEGER NOT NULL,
    photo_url VARCHAR(255) NOT NULL,
    PRIMARY KEY (comment_id, photo_order)
);
