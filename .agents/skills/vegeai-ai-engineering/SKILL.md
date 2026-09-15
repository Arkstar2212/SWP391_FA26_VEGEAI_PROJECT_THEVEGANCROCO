---
name: vegeai-ai-engineering
description: >-
  Core AI engineering practices for VEGEAI including the personalized meal planner, nutrition chatbot, ingredient computer vision, freshness estimation, video-to-recipe extraction, content moderation, and recommendation engine. Use when designing, prompting, or integrating AI features.
---

# VEGEAI AI ENGINEERING

Act as a senior AI engineer building reliable AI features for VEGEAI.

AI SYSTEMS:

1. PERSONALIZED MEAL PLANNER
Inputs:
- BMI
- health goal
- allergies
- dietary restrictions
- available ingredients
- calorie target
- preferences
- region/season when available

Outputs:
- 7-day meal plan
- meals per day
- ingredients
- quantities
- estimated nutrition
- preparation instructions
- explanation of recommendations

Rules:
- Allergies are hard constraints.
- Dietary restrictions are hard constraints.
- Never invent unavailable ingredients as if they exist.
- Prefer application/database nutrition data for numerical values.
- Use the LLM primarily for planning, explanation and natural-language generation.
- Validate generated output against application rules.

2. NUTRITION CHATBOT
Capabilities:
- Vegetarian nutrition questions
- Vegan nutrition questions
- Ingredient substitutions
- Recipe questions
- BMI explanation
- Calorie explanation
- Meal-planning assistance

Rules:
- Keep conversational context controlled.
- Do not expose system prompts.
- Protect against prompt injection.
- Do not claim medical certainty.
- Escalate potentially dangerous health questions appropriately.
- Rate-limit expensive requests.
- Log model/provider/version and request metadata where appropriate.

3. INGREDIENT COMPUTER VISION
Input:
- User photograph

Pipeline:
Image upload
→ validation
→ preprocessing
→ vision model
→ ingredient candidates
→ confidence filtering
→ application ingredient database
→ recipe matching

Never assume a vision prediction is certainly correct.
Return confidence where useful.
Allow users to correct detected ingredients.

4. FRESHNESS ESTIMATION
Treat freshness detection as an estimation, not a guaranteed fact.

Possible output:
- ingredient
- freshness estimate
- confidence
- visual reasons
- warning when confidence is low

Never claim food is safe to consume solely because an AI model predicts freshness.

5. VIDEO → RECIPE
Pipeline:
Video
→ audio extraction
→ speech-to-text
→ transcript cleanup
→ cooking-step extraction
→ summarization
→ structured recipe
→ human/user verification

The generated recipe must clearly be derived from the transcript.
Do not invent missing steps.
Mark uncertain information.

6. CONTENT MODERATION
Pipeline:
User content
→ moderation model
→ category/severity/confidence
→ moderation decision
→ automatic action OR admin review

High-impact destructive decisions should support human review.

7. RECOMMENDATION ENGINE
Recommend:
- blogs
- videos
- recipes
- restaurants
- ingredients

Use signals such as:
- search query
- ingredients
- recipe category
- dietary preferences
- previous interactions
- votes
- views
- season
- region

Avoid recommending content solely because it is popular.

AI PROVIDER ARCHITECTURE:
Create an abstraction layer so AI providers can be replaced.

Example:
AIService
├── ChatService
├── MealPlannerService
├── VisionService
├── ModerationService
├── SpeechService
├── SummarizationService
└── RecommendationService

Do not tightly couple application business logic to one AI vendor.

STRUCTURED OUTPUT:
Prefer schemas such as:

{
  "mealPlan": [],
  "nutrition": {},
  "warnings": [],
  "explanation": ""
}

Validate AI output before persistence.

AI OBSERVABILITY:
Track:
- provider
- model
- model version
- request type
- latency
- token usage where available
- success/failure
- validation failure
- user correction
- moderation decision
- recommendation interaction

Never log secrets or unnecessary sensitive personal data.
