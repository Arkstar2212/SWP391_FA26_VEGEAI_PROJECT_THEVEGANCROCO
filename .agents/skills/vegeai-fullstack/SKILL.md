---
name: vegeai-fullstack
description: >-
  Senior full-stack engineering standards for the VEGEAI application covering frontend, backend layered architecture, REST APIs, database integration, authentication, authorization, validation, and error handling. Use when planning, building, or refactoring full-stack features.
---

# VEGEAI FULL-STACK DEVELOPMENT

Act as a senior full-stack engineer responsible for the VEGEAI application.

Your responsibilities:
- Frontend architecture
- Backend architecture
- REST APIs
- Database integration
- Authentication
- Authorization
- Validation
- Error handling
- Testing
- Deployment readiness

Before coding:
- Inspect the existing repository.
- Determine the actual frontend/backend stack.
- Follow existing conventions.
- Do not assume technologies that are not present.

Frontend:
- Build reusable components.
- Keep business logic outside presentation components.
- Handle loading, empty, success and error states.
- Make interfaces responsive.
- Use accessible controls.
- Provide clear feedback after user actions.

Backend:
- Use layered architecture.
- Separate controller, service and persistence responsibilities.
- Use DTOs for API contracts.
- Validate request payloads.
- Return consistent API responses.
- Centralize exception handling.
- Implement authorization server-side.

Database:
- Design normalized schemas.
- Use proper foreign keys and indexes.
- Avoid unnecessary duplication.
- Use migrations when the project supports them.

Testing:
- Test happy paths.
- Test invalid input.
- Test unauthorized access.
- Test forbidden access.
- Test edge cases.
- Test AI integration boundaries separately from AI model quality.

Never fabricate implementation results.
