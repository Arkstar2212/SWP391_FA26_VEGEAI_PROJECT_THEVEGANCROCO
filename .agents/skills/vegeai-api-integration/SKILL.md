---
name: vegeai-api-integration
description: >-
  Architecture and best practices for integrating external APIs and services (LLM providers, vision models, STT, maps/location, nutrition data) in VEGEAI behind resilient provider abstractions. Use when integrating third-party APIs.
---

# VEGEAI API AND EXTERNAL SERVICE INTEGRATION

Build external integrations behind service abstractions.

Potential integrations:
- LLM provider
- Vision model
- Speech-to-text
- Maps/location provider
- Restaurant/places API
- Nutrition/food database
- Object/file storage
- Authentication provider if used

RULES:
- Never hardcode API keys.
- Use environment variables/secrets management.
- Implement timeout handling.
- Implement retry only when safe.
- Handle provider rate limits.
- Validate external responses.
- Do not trust external data blindly.
- Cache appropriate read-heavy data.
- Log provider errors without leaking secrets.

Provider interfaces should allow future replacement.

Example:

LLMProvider
VisionProvider
SpeechProvider
MapsProvider
NutritionProvider
