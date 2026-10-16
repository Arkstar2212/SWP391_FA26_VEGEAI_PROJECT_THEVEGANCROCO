# Admin Role Boundaries

## Responsibilities

- Manage accounts, recipes, ingredient quantities, per-serving nutrition, recipe-video links, videos, Blog and Q&A.
- Write a Blog draft and publish it immediately without another moderator. Member posts still require review.
- Answer, approve, reject or delete member questions. Admins cannot create questions or use `questions/mine`.
- Admins do not enter personal AI, fridge, nutrition or cookbook workflows.

## Access And Privacy

`RoleBoundaryFilter` checks the current database role and active account status on
`/api/admin/**` and authenticated personal routes (`/api/ai/**`, `/api/fridge/**`,
`/api/nutrition/**`, `/api/recipes/cookbook/**`, `/api/recipes/fridge-match`).
Revoked admin privileges or newly promoted admin accounts cannot bypass these
boundaries with stale JWT claims. Existing method-level checks remain in force.
Guest public content and the existing guest AI/nutrition behavior are unchanged.

Admin user list/detail/status/role responses expose only identity, role, account
status and account timestamps, not dietary preferences, allergies, measurements,
health targets or private contact/profile fields. A user's own profile is unchanged.
AI audit responses omit prompt text; these endpoints do not grant access to member
chat histories or fridge images. Existing sample AI telemetry is not live monitoring.

## Recipe-Video API

- New admin detail: `GET /api/admin/recipes/{id}`, including draft recipes and linked draft videos.
- Create/update recipe accepts optional `videoIds`: at most 10 existing video UUIDs.
- Omitted `videoIds` preserves existing links on update; `[]` removes all links.
- Public recipe detail includes `videos` in the existing VideoResponse shape, published videos only.
- Public detail returns 404 for draft/archived recipes; admin detail remains available.
- Deleting a video removes its recipe associations, not the recipes.
- Hibernate schema update creates the additive `recipe_videos` join table. No database reset is needed.

## Frontend

Admin login defaults to `/admin`. The navigation contains account overview,
`/admin/recipes`, `/admin/blog`, `/admin/videos`, `/admin/questions`.
Personal deep links redirect to `/admin` before mounting the personal screen.
The recipe editor selects videos from the database with search and pagination;
public recipe details render the linked published videos using the existing player.

## Verification

`AdminRoleBoundaryTest` and `BlogApiTest` use H2 and rollback transactions to cover
direct publishing, member review, ownership, stale versions, current-role checks,
private data redaction, recipe/video links and deletion. No production data is seeded
by these tests. Weekly/monthly personalized meal planning is not part of this change.
