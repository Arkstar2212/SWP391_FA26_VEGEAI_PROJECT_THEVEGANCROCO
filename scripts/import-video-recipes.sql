\set ON_ERROR_STOP on
BEGIN;
SET LOCAL client_encoding = 'UTF8';

CREATE TEMP TABLE video_recipe_import ON COMMIT DROP AS
SELECT item AS document
FROM jsonb_array_elements(pg_read_file('/tmp/vegeai-video-recipes.json')::jsonb) item;

-- Fail atomically if a video or tag cannot be resolved uniquely.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM video_recipe_import s
        WHERE (SELECT count(*) FROM community_videos v
               WHERE v.video_url = 'https://www.youtube.com/watch?v=' || (s.document->>'youtubeId')
                 AND v.status = 'PUBLISHED') <> 1
    ) THEN RAISE EXCEPTION 'Missing or ambiguous published source video'; END IF;
    IF EXISTS (
        SELECT 1 FROM video_recipe_import s,
            jsonb_array_elements_text(s.document->'tags') code
        WHERE NOT EXISTS (SELECT 1 FROM tags t WHERE t.code = code.value)
    ) THEN RAISE EXCEPTION 'Unknown recipe tag'; END IF;
END $$;

CREATE TEMP TABLE imported_video_recipes (recipe_id uuid, slug text) ON COMMIT DROP;
WITH inserted AS (
    INSERT INTO recipes (recipe_id, title, slug, description, prep_time_minutes,
        cook_time_minutes, servings, difficulty, image_url, is_verified,
        author_id, status, created_at, updated_at)
    SELECT gen_random_uuid(), s.document->>'title', s.document->>'slug',
        'Công thức tóm lược từ Ducan Kitchen. Nguồn: ' || (s.document->>'sourceUrl') || E'\n'
        || COALESCE(s.document->>'notes', '')
        || E'\nChưa thẩm định dinh dưỡng; chưa dùng để lập thực đơn cá nhân.',
        (s.document->>'prepTimeMinutes')::integer,
        (s.document->>'cookTimeMinutes')::integer,
        (s.document->>'servings')::integer, 'EASY',
        'https://i.ytimg.com/vi/' || (s.document->>'youtubeId') || '/hqdefault.jpg',
        false, v.created_by, 'PUBLISHED', now(), now()
    FROM video_recipe_import s JOIN community_videos v
      ON v.video_url = 'https://www.youtube.com/watch?v=' || (s.document->>'youtubeId')
      AND v.status = 'PUBLISHED'
    ON CONFLICT (slug) DO NOTHING
    RETURNING recipe_id, slug
) INSERT INTO imported_video_recipes SELECT * FROM inserted;

INSERT INTO recipe_ingredients (ingredient_id, recipe_id, name, normalized_name,
    amount, unit, is_optional, is_allergen, allergen_type)
SELECT gen_random_uuid(), n.recipe_id, i->>'name',
    regexp_replace(normalize(lower(trim(i->>'name')), NFD), U&'[\0300-\036f]', '', 'g'),
    (i->>'amount')::double precision, i->>'unit', false,
    i->>'allergenType' IS NOT NULL, i->>'allergenType'
FROM imported_video_recipes n JOIN video_recipe_import s ON s.document->>'slug' = n.slug,
    jsonb_array_elements(s.document->'ingredients') i;

INSERT INTO recipe_instructions (recipe_id, step_order, step_description)
SELECT n.recipe_id, (step.ordinality - 1)::integer, step.value
FROM imported_video_recipes n JOIN video_recipe_import s ON s.document->>'slug' = n.slug,
    jsonb_array_elements_text(s.document->'instructions') WITH ORDINALITY step;

INSERT INTO recipe_tags (recipe_id, tag_id)
SELECT n.recipe_id, t.tag_id
FROM imported_video_recipes n JOIN video_recipe_import s ON s.document->>'slug' = n.slug,
    jsonb_array_elements_text(s.document->'tags') code
    JOIN tags t ON t.code = code.value;

INSERT INTO recipe_videos (recipe_id, video_id)
SELECT n.recipe_id, v.id
FROM imported_video_recipes n JOIN video_recipe_import s ON s.document->>'slug' = n.slug
JOIN community_videos v ON v.video_url = 'https://www.youtube.com/watch?v=' || (s.document->>'youtubeId')
    AND v.status = 'PUBLISHED';

SELECT count(*) AS newly_imported_recipes FROM imported_video_recipes;
COMMIT;
