# Ducan Kitchen video references

`seed-ducan-videos.sql` adds 10 reviewed YouTube references from
https://ducankitchen.com/category/video/ to the existing video database.
All YouTube oEmbed responses returned HTTP 200 and author `Ducan Kitchen`
when checked on 2026-10-15. Playback remains subject to YouTube availability,
regional restrictions and the owner's embed settings.

The import uses the original YouTube URLs and YouTube thumbnails. Descriptions
are short, independently written summaries with the original article URL and
creator attribution. No media files, full recipes, user comments, view counts
or ratings are copied. Duration is left unset rather than guessed.

## Import into the local Docker database

From the backend directory in PowerShell 7:

```powershell
Get-Content -Raw -Encoding utf8 .\docs\seed-ducan-videos.sql | docker exec -i vegeai-postgres psql -v ON_ERROR_STOP=1 -U postgres -d vegeai
```

An existing active admin is required and recorded as the local uploader, not
as the creator of the original video. The import is transactional, serializes
concurrent imports and skips existing YouTube IDs, including common URL forms.
It does not modify existing titles, descriptions, draft states or comments.
It runs only when explicitly invoked, never during application startup.

The published records appear at `/videos`. Search, category filters, pagination,
comments and favorites continue to use the existing database APIs. Admins can
edit, hide or delete imported records at `/admin/videos`. Re-running the import
will add back a deleted reference; it will not republish an existing draft.
