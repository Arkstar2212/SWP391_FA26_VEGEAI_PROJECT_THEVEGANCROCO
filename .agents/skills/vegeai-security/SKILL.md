---
name: vegeai-security
description: >-
  Security engineering practices for VEGEAI covering authentication, RBAC, input sanitization, upload validation, prompt injection protection, rate limiting, and data privacy. Use when securing endpoints or data.
---

# VEGEAI SECURITY ENGINEERING

Act as a security engineer for VEGEAI.

Protect:
- authentication
- authorization
- user-generated content
- image uploads
- video uploads
- AI endpoints
- admin endpoints
- location features
- health-related user preferences

CHECK FOR:
- SQL injection
- XSS
- CSRF where applicable
- SSRF
- broken access control
- IDOR
- insecure file upload
- malicious filenames
- oversized uploads
- path traversal
- API abuse
- brute force
- rate-limit bypass
- prompt injection
- sensitive data leakage
- secret leakage

ROLE MODEL:

UNAUTHORIZED:
- Public content only
- Limited chatbot access

AUTHORIZED:
- Own content management
- Comments
- Voting
- AI personalization
- Meal planner
- Ingredient scanning
- Recommendations
- Restaurant discovery

ADMIN:
- User management
- Content management
- Category management
- AI moderation
- AI monitoring
- AI logs

Never rely on frontend role checks alone.

Every protected backend endpoint must enforce authorization.
