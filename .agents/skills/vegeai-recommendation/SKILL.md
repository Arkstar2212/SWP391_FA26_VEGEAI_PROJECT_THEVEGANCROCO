---
name: vegeai-recommendation
description: >-
  Explainable recommendation engine design for VEGEAI recipes, blogs, videos, ingredients, and restaurants using signals like search, user preferences, and dietary restrictions. Use when building recommendation features.
---

# VEGEAI RECOMMENDATION ENGINE

Build explainable recommendations for VEGEAI.

RECOMMEND:
- recipes
- blogs
- cooking videos
- ingredients
- restaurants/shops

SIGNALS:
- current search
- ingredient matches
- category
- dietary preferences
- previous interactions
- votes
- views
- saved content
- season
- region
- popularity

Recommendation output should contain:
- item
- score
- reason
- matching signals

Example:

{
  "itemId": "...",
  "score": 0.91,
  "reason": [
    "matches searched ingredient",
    "matches vegan preference",
    "uses ingredients already available"
  ]
}

Avoid opaque recommendations when an explanation can be provided.

Start with a reliable rule/content-based recommendation system before introducing unnecessarily complex ML.
