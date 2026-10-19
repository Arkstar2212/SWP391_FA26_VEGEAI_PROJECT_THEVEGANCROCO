# Blog API

The current frontend's `blogService` uses these database-backed routes.
The entity maps to the existing `blog_posts.post_id` primary key and preserves
the existing author/reviewer foreign keys and post data; no database reset is needed.

## Public

- `GET /api/blog/posts?page=0&size=9&keyword=&category=GREEN_LIVING`
- `GET /api/blog/posts/{id}`

Only `PUBLISHED` posts are public. Other statuses and unknown IDs return 404.
Lists omit content. Public responses omit private reviewer metadata and reasons.
Categories: `KITCHEN`, `GREEN_LIVING`, `EXPLORATION`, `NUTRITION`.
Pagination uses the common `items/page/size/totalElements/totalPages/first/last` shape.
- `GET /api/public/content-interactions/BLOG/ratings?ids={id}&ids={id}` returns rating summaries for up to 50 published posts in one request; it does not load comment pages.

## Active authenticated author

- `GET /api/blog/my-posts?status=&page=0&size=9`
- `GET /api/blog/my-posts/{id}`
- `POST /api/blog/my-posts`: `{title, excerpt, content, coverImage, category}` -> 201 DRAFT
- `PUT /api/blog/my-posts/{id}`: same body plus current `version`
- `POST /api/blog/my-posts/{id}/submit`: `{version}` -> PENDING_REVIEW for members, PUBLISHED for admins
- `POST /api/blog/my-posts/{id}/withdraw`: `{version}` -> DRAFT (pending only)
- `POST /api/blog/my-posts/{id}/archive`: `{version}` -> ARCHIVED
- `POST /api/blog/my-posts/{id}/restore`: `{version}` -> the status the post had before archiving
- `DELETE /api/blog/my-posts/{id}?version=...` (legacy hide action; moves the post to ARCHIVED)
- `DELETE /api/blog/my-posts/{id}/permanent?version=...` (permanently deletes the post and its comments/ratings)

Authors cannot read or modify another author's private post, even if an ID is known.
The author and initial status come from the server, not request-supplied fields.
Members can edit only drafts/rejected posts. Admins can also edit their own
published posts, retaining publication status; ownership and version checks still apply.
Archived posts are hidden from public reads but retain their previous status and interactions.
Restoring a post returns it to that status. A permanent delete is a separate, irreversible action.
At startup, the backend updates the PostgreSQL status check constraint so existing databases accept ARCHIVED.
Admin drafts remain private until explicitly published with `submit`, which bypasses
the review queue using the author's current database role, not a client-supplied role.
Submission and published edits require a title >=5 characters
and content >=80 characters. Limits are title 150, excerpt 400, content 50000,
cover URL 1000 characters. Content is plain text, not trusted HTML. Cover images
accept HTTP(S) without credentials or a local `/images/filename` path.

## Active admin

- `GET /api/admin/blog/posts?status=PENDING_REVIEW&keyword=&page=0&size=10`
- `GET /api/admin/blog/posts/{id}`
- `POST /api/admin/blog/posts/{id}/review`: `{version, decision: "APPROVE"|"REJECT", reason}`

Only pending posts can be reviewed. Rejection requires a nonblank reason <=2000
characters. Approval publishes the post; rejection leaves it private and editable
by its author. Resubmitting clears the previous review metadata.

All mutations require the current version. Stale revisions or invalid state
transitions return 409, missing/invalid revisions return 400. Database row locks
serialize reviews, withdrawal, edits, archive, restore and deletion. Private/admin access rechecks
current account status and role in the database, not just the JWT claim.

## Verification

`BlogApiTest` covers guest reads, private protection, owner isolation, forged fields,
submission/withdrawal/rejection/resubmission/publication, archive/restore and permanent deletion, revision conflicts,
filters/pagination, invalid cover URLs, and inactive/demoted accounts, using H2
and rollback transactions. No test posts are added to the running database.
