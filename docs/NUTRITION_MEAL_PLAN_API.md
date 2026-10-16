# Shared Nutrition Profile And Meal Plans

## Profile

`PUT /api/nutrition/profile` now also accepts optional `phoneNumber`,
`dietaryPreferences` and `allergies` (comma-separated allergens / foods to avoid).
The existing Vietnamese mobile validation and diet codes apply. Missing fields
preserve account values; empty phone clears it and empty allergies becomes `NONE`.
These fields are stored on the existing user, not duplicated in another profile.
Body metrics, targets and account fields save in one transaction. GET returns the
same fields. The stateless guest calculator does not save them.

Dashboard profile is read-only. Its update link opens `/nutrition`, which loads
saved values before permitting writes. The member name/avatar opens
`/dashboard?tab=profile`. No other user's ID is accepted for these actions.

## Chat Scope

Chat system instructions restrict answers to vegetarian food, nutrition and VEGEAI
features. Gemini must include a boolean `inScope` in its structured reply.
Missing scope or invalid JSON fails visibly, rather than accepting an unchecked
reply. When false, the backend replaces all model wording with exactly:

> Mình hỗ trợ các chủ đề về ăn chay, công thức và tính năng của VEGEAI. Bạn có muốn tìm một món chay phù hợp hôm nay không?

Suggested recipes are cleared for out-of-scope replies. Classification itself is
model-based, not a guarantee that arbitrary adversarial inputs cannot be misclassified.
Chat includes saved goal, activity level and macro targets in addition to the
existing dietary/body context. Phone numbers are not sent in the AI context.

## Member-Only Meal Plans

- `POST /api/meal-plans/generate`: `{period: "WEEK"|"MONTH", startDate: "YYYY-MM-DD", mealsPerDay: 1..6}` -> owned DRAFT.
- `GET /api/meal-plans?page=0&size=5`: paginated SAVED summaries belonging to the caller.
- `GET /api/meal-plans/{id}`: owned draft or saved plan with days, meals, portions, targets, warnings and daily totals.
- `GET /api/meal-plans/{id}/options`: suitable database recipes for swapping a draft meal.
- `PUT /api/meal-plans/{id}/days/{day}/meals/{slot}`: `{recipeId}`; indexes start at zero; recomputes portions and daily totals.
- `POST /api/meal-plans/{id}/save`: revalidates profile and recipes, then marks SAVED; idempotent on an already saved plan.

An active MEMBER account, saved nutrition profile and dietary preference are
required. Guests get 401, admins/inactive accounts get 403. Unknown or someone
else's plan ID gets 404. All writes use the authenticated owner, never a client userId.

A week is seven days. A month ends on the day before `startDate.plusMonths(1)`.
Saved plans are historical snapshots; changing the profile does not silently
rewrite them. A changed nutrition/diet profile invalidates an unsaved draft with
409. Saved plans cannot be swapped; create a new plan instead.

## Data And Safety Boundaries

Only published recipes with positive servings/calories, finite nonnegative macros,
quantified ingredients and cooking instructions are considered. The backend uses
ingredient names and allergen metadata with common Vietnamese/English aliases to
exclude declared allergies/avoidances and incompatible vegetarian ingredients.
This is conservative filtering, not a certification of ingredients or cross-contact safety.

At most 60 vetted candidates, targets, goal and dietary restrictions are sent to
Gemini for ranking. No phone, email or name is sent for meal planning. Unknown
model-generated recipe IDs are rejected. Provider errors remain errors, not fake
successful plans. The backend, not the model, computes portions (0.5..3 per meal,
quarter-serving steps), macro totals and warnings. Nutrition is per serving;
ingredient quantities in the UI scale by portions / recipe.servings.

Candidate scarcity, unavailable videos and calorie/macro deviations are reported.
This is not a clinical optimizer and does not guarantee every target is met. The
user reviews daily totals and may swap meals before saving. Values are planned
intake, never claimed to be actual consumption.

Recipes include published linked videos only; the UI uses the existing player.
No unrelated video or invented recipe is silently substituted. Admins attach the
correct video in `/admin/recipes`.

`meal_plans` is an additive table with owned JSON snapshots and a hashed profile
signature. Existing accounts, recipes, posts and videos are preserved.

## Verification

`MealPlanApiTest` covers shared profile writes, validation, owner isolation,
week/month durations, macros, swaps, saved state, stale profiles, missing recipe
data, archived recipes, diet/allergen aliases, private videos and hallucinated IDs.
AI integration tests use a loopback provider, not Google or live user data.
Frontend service tests and browser fixture checks cover the end-to-end UI.
