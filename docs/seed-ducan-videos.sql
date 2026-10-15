-- Reviewed source: https://ducankitchen.com/category/video/
-- Verified against the article embeds and YouTube oEmbed on 2026-10-15.
-- Adds references only, never copies video files, articles, comments or ratings.
-- Run manually with psql -v ON_ERROR_STOP=1. Re-running does not overwrite admin edits.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('vegeai:seed:ducan-videos'));

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE') THEN
        RAISE EXCEPTION 'An active admin account is required before importing videos.';
    END IF;
END $$;

WITH catalog (position, youtube_id, title, category, summary, article_slug) AS (VALUES
    (0, 'ao_tEpz6niU', 'Cách làm cơm chiên mì gói chay', 'MON_VIET',
     'Video hướng dẫn kết hợp cơm và mì gói cho một món chay đổi vị.', 'cach-lam-com-chien-mi-goi-chay'),
    (1, 'wTqClpgLWWs', '2 cách làm sữa đặc thuần chay đơn giản', 'DO_UONG',
     'Tham khảo hai cách chuẩn bị sữa đặc từ nguyên liệu thực vật.', 'video-2-cach-lam-sua-dac-thuan-chay-don-gian'),
    (2, '22rmN4AYkkk', 'Cách làm bún lứt xào rau củ', 'MON_CHAY',
     'Cùng vào bếp với món bún lứt và rau củ nhiều màu sắc.', 'cach-lam-bun-lut-xao-rau-cu'),
    (3, '9gG--xBmSa8', 'Cách làm canh chua thanh mát cho mùa hè', 'MON_VIET',
     'Một video về cách nấu canh chua chay cho bữa cơm ngày nóng.', 'video-cach-lam-canh-chua-thanh-mat-cho-mua-he'),
    (4, 'JqMTUKuL2_U', 'Cách làm đậu khìa nước dừa đưa cơm', 'MON_VIET',
     'Theo dõi cách chế biến đậu phụ cùng nước dừa cho bữa cơm chay.', 'video-cach-lam-dau-khia-nuoc-dua-dua-com'),
    (5, '8GKi8myWT1w', 'Cách làm si rô mận và mứt mận', 'DO_UONG',
     'Gợi ý chế biến quả mận thành si rô và mứt trong căn bếp gia đình.', 'video-cach-lam-si-ro-man-va-mut-man'),
    (6, 'mdODLbXFaLI', 'Kem trà đào cam sả không cần máy làm kem', 'MON_CHAY',
     'Xem cách làm món kem thực vật với hương vị trà đào, cam và sả.', 'video-cach-lam-kem-tra-dao-cam-sa-khong-can-may-lam-kem'),
    (7, 'EhM4zdA2txI', 'Cách làm cơm hấp lá sen chay', 'MON_VIET',
     'Video vào bếp với món cơm chay gói trong lá sen.', 'cach-lam-com-hap-la-sen-chay'),
    (8, 'AJ1ln7x1XoA', 'Đi chợ ít rác, nấu nồi bún canh chua', 'MEO_BEP',
     'Một chuyến đi chợ và chuẩn bị bữa chay trong vlog của Ducan Kitchen.', 'vlog-1-di-cho-it-rac-nau-noi-bun-canh-chua'),
    (9, 'XG-3d3uPJgo', 'Cách làm sữa chua đậu nành úp ngược', 'MON_CHAY',
     'Tham khảo video làm sữa chua từ đậu nành tại nhà.', 'cach-lam-sua-chua-dau-nanh-up-nguoc-thom-ngon')
), uploader AS (
    SELECT user_id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'
    ORDER BY created_at, user_id LIMIT 1
)
INSERT INTO community_videos
    (id, title, description, video_url, thumbnail_url, category, duration_seconds,
     status, created_by, created_at, updated_at, version)
SELECT gen_random_uuid(), catalog.title,
       catalog.summary || E'\n\nNguồn video: Ducan Kitchen.\nBài viết gốc:\nhttps://ducankitchen.com/' || catalog.article_slug || '/',
       'https://www.youtube.com/watch?v=' || catalog.youtube_id,
       'https://i.ytimg.com/vi/' || catalog.youtube_id || '/hqdefault.jpg',
       catalog.category, NULL, 'PUBLISHED', uploader.user_id,
       CURRENT_TIMESTAMP - catalog.position * INTERVAL '1 second', CURRENT_TIMESTAMP, 0
FROM catalog CROSS JOIN uploader
WHERE NOT EXISTS (
    SELECT 1 FROM community_videos existing
    WHERE substring(existing.video_url FROM '(?:[?&]v=|youtu[.]be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})') = catalog.youtube_id
);
COMMIT;
