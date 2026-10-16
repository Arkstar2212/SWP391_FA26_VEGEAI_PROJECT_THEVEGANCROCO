\set ON_ERROR_STOP on
BEGIN;
SET LOCAL client_encoding = 'UTF8';
CREATE TEMP TABLE detail_import ON COMMIT DROP AS
SELECT r.recipe_id, item AS document
FROM jsonb_array_elements(pg_read_file('/tmp/vegeai-video-recipe-details.json')::jsonb) item
JOIN recipes r ON r.slug = item->>'slug';

-- Refuse to touch unrelated, reviewed, or structurally changed recipes.
DO $$
BEGIN
    IF (SELECT count(*) FROM detail_import) <> 11 THEN RAISE EXCEPTION 'Expected 11 existing recipes'; END IF;
    PERFORM r.recipe_id FROM recipes r JOIN detail_import s USING(recipe_id) FOR UPDATE OF r;
    IF EXISTS (
        SELECT 1 FROM detail_import s JOIN recipes r USING(recipe_id)
        WHERE r.is_verified OR r.status <> 'PUBLISHED' OR NOT EXISTS (
            SELECT 1 FROM recipe_videos rv JOIN community_videos v ON v.id = rv.video_id
            WHERE rv.recipe_id = r.recipe_id AND v.status = 'PUBLISHED'
              AND v.video_url = 'https://www.youtube.com/watch?v=' || (s.document->>'youtubeId')
        )
    ) THEN RAISE EXCEPTION 'Unexpected recipe state or video link'; END IF;
    IF EXISTS (
        SELECT 1 FROM detail_import s
        WHERE (SELECT count(*) FROM recipe_ingredients i WHERE i.recipe_id=s.recipe_id)
            <> jsonb_array_length(s.document->'ingredients')
        OR EXISTS (
            SELECT 1 FROM jsonb_array_elements(s.document->'ingredients') item
            WHERE (SELECT count(*) FROM recipe_ingredients i WHERE i.recipe_id=s.recipe_id
                AND i.name IN (item->>'sourceName', item->>'name')) <> 1
        )
    ) THEN RAISE EXCEPTION 'Ingredient list changed; review manually before import'; END IF;
END $$;

INSERT INTO tags (tag_id, code, name, tag_group, description) VALUES
    (gen_random_uuid(),'MAIN_DISH','Món chính','DISH_CATEGORY','Standardized main dish'),
    (gen_random_uuid(),'SIDE_DISH','Món ăn kèm','DISH_CATEGORY','Not a complete meal on its own'),
    (gen_random_uuid(),'CONDIMENT','Đồ dùng kèm','DISH_CATEGORY','Not suitable as an independent meal'),
    (gen_random_uuid(),'DESSERT','Tráng miệng','DISH_CATEGORY','Not suitable as an independent meal')
ON CONFLICT (code) DO NOTHING;

UPDATE recipes r SET description=s.document->>'description',
    servings=(s.document->>'servings')::integer,
    prep_time_minutes=(s.document->>'prepTimeMinutes')::integer,
    cook_time_minutes=(s.document->>'cookTimeMinutes')::integer,
    difficulty=s.document->>'difficulty',
    calories=(s.document->'nutrition'->>'calories')::integer,
    protein_grams=(s.document->'nutrition'->>'proteinGrams')::double precision,
    carbs_grams=(s.document->'nutrition'->>'carbsGrams')::double precision,
    fat_grams=(s.document->'nutrition'->>'fatGrams')::double precision,
    fiber_grams=(s.document->'nutrition'->>'fiberGrams')::double precision,
    updated_at=now()
FROM detail_import s WHERE r.recipe_id=s.recipe_id;

UPDATE recipe_ingredients i SET name=item->>'name',
    normalized_name=regexp_replace(normalize(lower(trim(item->>'name')), NFD), U&'[\0300-\036f]', '', 'g'),
    amount=(item->>'amount')::double precision, unit=item->>'unit',
    is_allergen=item->>'allergenType' IS NOT NULL, allergen_type=item->>'allergenType'
FROM detail_import s, jsonb_array_elements(s.document->'ingredients') item
WHERE i.recipe_id=s.recipe_id AND i.name IN (item->>'sourceName', item->>'name');

DELETE FROM recipe_instructions WHERE recipe_id IN (SELECT recipe_id FROM detail_import);
INSERT INTO recipe_instructions (recipe_id,step_order,step_description)
SELECT s.recipe_id,(step.ordinality-1)::integer,step.value
FROM detail_import s, jsonb_array_elements_text(s.document->'instructions') WITH ORDINALITY step;

DELETE FROM recipe_tags WHERE recipe_id IN (SELECT recipe_id FROM detail_import);
INSERT INTO recipe_tags (recipe_id,tag_id)
SELECT s.recipe_id,t.tag_id FROM detail_import s,
    jsonb_array_elements_text(s.document->'tags') code JOIN tags t ON t.code=code.value;
SELECT count(*) AS enriched_recipes FROM detail_import;
COMMIT;
