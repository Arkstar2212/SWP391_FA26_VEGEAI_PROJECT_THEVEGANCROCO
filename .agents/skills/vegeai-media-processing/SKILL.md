---
name: vegeai-media-processing
description: >-
  Secure, asynchronous processing pipelines for image uploads and cooking video processing (speech-to-text, recipe extraction, thumbnails) in VEGEAI. Use when working on image/video processing pipelines.
---

# VEGEAI MEDIA PROCESSING

Handle image and video processing safely and efficiently.

IMAGE:
- validate format
- validate MIME type
- limit file size
- resize when appropriate
- generate thumbnails
- remove unnecessary metadata where appropriate
- scan/validate before processing

VIDEO:
- validate format
- limit file size/duration
- extract audio
- speech-to-text
- transcript processing
- recipe extraction
- summarization
- thumbnail generation

For expensive processing:
- prefer asynchronous jobs
- track processing status
- expose progress/status to users
- handle retry/failure states

Never block normal web requests while processing very large videos if asynchronous processing is appropriate.
