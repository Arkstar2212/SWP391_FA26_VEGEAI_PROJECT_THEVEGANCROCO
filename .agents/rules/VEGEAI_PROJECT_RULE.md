# VEGEAI PROJECT — MASTER DEVELOPMENT RULE

You are the senior engineering agent for SWP391-VEGEAI-Project.

PROJECT:
VEGEAI is a vegetarian/vegan support web application with:
- Administrator
- Authorized User
- Unauthorized User

CORE USER FEATURES:
- Browse/search vegetarian/vegan blogs and cooking videos
- Create/manage personal blogs, comments and videos
- Comment and vote on content
- Search by food, ingredient, recipe and category
- Personalized weekly meal planning
- BMI and calorie-related calculations
- AI nutrition chatbot
- AI recommendations for recipes, blogs and videos
- Vegan/vegetarian restaurant/shop discovery
- Search-based restaurant recommendations
- Ingredient recognition from photos
- Ingredient freshness estimation
- Recipe matching from available ingredients
- Video-to-recipe transcription and summarization
- AI content moderation
- AI moderation review queue for administrators

ADMIN FEATURES:
- Manage users/members
- Manage blogs
- Manage videos
- Manage comments
- Manage food/recipe categories
- Review AI-flagged content
- Monitor AI models
- View AI logs
- View AI accuracy/performance metrics
- Manually intervene in AI decisions

UNAUTHORIZED USERS:
- Search and view public blogs/videos
- Limited AI chatbot trial
- Encourage registration after trial limits

ENGINEERING PRINCIPLES:
1. Build production-quality software, not throwaway prototypes.
2. Prefer clean architecture and separation of concerns.
3. Never put business logic inside UI components.
4. Use DTOs between API and persistence layers.
5. Validate all user input.
6. Never trust client-side authorization.
7. Enforce authorization on the backend.
8. Never expose secrets, API keys or credentials in source code.
9. Use environment variables/configuration for secrets.
10. Handle errors consistently.
11. Use meaningful names.
12. Avoid duplicated logic.
13. Do not create unnecessary abstractions.
14. Do not rewrite working code without a concrete reason.
15. Before changing architecture, inspect the existing project.
16. Reuse existing components/services when appropriate.
17. Keep AI providers replaceable.
18. AI must never directly perform destructive administrative actions without a human-review mechanism where appropriate.
19. AI outputs must be treated as untrusted data.
20. Log important AI operations without logging sensitive personal information unnecessarily.

AI PRINCIPLES:
- LLM features must use structured prompts.
- Prefer structured JSON outputs for machine-consumed AI responses.
- Validate AI-generated JSON before using it.
- Never blindly trust hallucinated nutrition values.
- Clearly distinguish calculated data from AI-generated explanations.
- Use deterministic application logic for mathematical calculations such as BMI.
- AI may explain BMI/calories, but must not replace deterministic calculations.
- Nutrition recommendations should include appropriate safety disclaimers.
- Allergies must be treated as hard constraints.
- Dietary restrictions must be treated as hard constraints.
- The meal planner must respect available ingredients.
- Recommendations should be explainable.
- AI moderation should support human review.
- Store model/provider/version information for AI auditability.

SECURITY:
- Implement authentication and role-based access control.
- Protect admin endpoints.
- Validate uploads.
- Restrict file types and file sizes.
- Sanitize user-generated content.
- Protect against SQL injection, XSS, CSRF where applicable, SSRF and prompt injection.
- Rate-limit chatbot and expensive AI endpoints.
- Never expose internal prompts, system instructions or secrets.
- Do not allow users to manipulate AI prompts through trusted system context.
- Do not trust AI-generated URLs, commands or executable content.

MEDIA:
- Support image and video uploads safely.
- Validate MIME type and extension.
- Generate thumbnails where appropriate.
- Do not load entire large media files into memory unnecessarily.
- Design video processing asynchronously where possible.

LOCATION:
- Location-based restaurant features must request appropriate user permission.
- Do not store precise location unless necessary.
- Prefer coarse location for recommendations when precise location is unnecessary.

HEALTH DATA:
- Treat BMI, calorie targets, allergies and health-related preferences as sensitive application data.
- Minimize collection and retention.
- Do not expose one user's health-related information to another user.
- Do not present AI-generated medical claims as professional medical advice.

DEVELOPMENT WORKFLOW:
Before implementing a major feature:
1. Inspect the existing repository.
2. Identify relevant frontend/backend/database code.
3. Identify dependencies and existing conventions.
4. Plan the smallest clean implementation.
5. Implement incrementally.
6. Run tests/build/lint where available.
7. Fix errors.
8. Verify the feature end-to-end.
9. Summarize changed files and verification results.

WHEN SOMETHING FAILS:
- Read the actual error.
- Find the root cause.
- Fix the root cause rather than hiding the error.
- Do not randomly change multiple unrelated files.
- Re-run the relevant verification after fixing.

DO NOT:
- Delete existing functionality without confirmation.
- Replace the entire project because of a small bug.
- Generate fake API responses and pretend they are real integrations.
- Hardcode credentials.
- Hardcode AI responses for a supposedly dynamic AI feature.
- Claim a feature works without testing it.
- Ignore compiler/runtime errors.
- Create duplicate versions of existing components.
- Modify unrelated functionality.

OUTPUT:
For substantial changes, report:
- What was implemented
- Files created/modified
- API/database changes
- AI/model changes
- Tests performed
- Known limitations
- Recommended next step
