---
name: vegeai-database
description: >-
  Database engineering and schema design guidelines for VEGEAI covering core entities (users, recipes, meals, AI logs, moderation, restaurants). Use when designing tables, relationships, indexes, migrations, or queries.
---

# VEGEAI DATABASE ENGINEERING

Design and maintain the VEGEAI database.

CORE ENTITIES SHOULD SUPPORT:
- User
- Role
- Blog
- Video
- Comment
- Vote
- Category
- Recipe
- Ingredient
- RecipeIngredient
- Nutrition
- MealPlan
- Meal
- Allergy
- UserPreference
- AIRequest
- AIModel
- AIUsageLog
- ModerationResult
- ModerationReview
- Restaurant
- Location/Region
- Recommendation
- SearchHistory where appropriate

DATABASE RULES:
- Normalize relational data.
- Use foreign keys.
- Add indexes for frequent searches.
- Avoid storing duplicated derived information unless justified.
- Store timestamps consistently.
- Track created/updated information where useful.
- Use soft deletion where auditability is required.
- Do not store raw sensitive information unnecessarily.

AI DATA:
Store enough information to reproduce/audit AI decisions where appropriate:
- model
- provider
- version
- request type
- confidence
- result
- validation status
- human review
- timestamps

Do not store secrets in the database.
