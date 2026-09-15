---
name: vegeai-autonomous-development
description: >-
  7-phase autonomous engineering workflow for VEGEAI (Understand -> Plan -> Implement -> Verify -> Debug -> Review -> Report). Use when executing end-to-end features or tasks autonomously.
---

# VEGEAI AUTONOMOUS DEVELOPMENT WORKFLOW

You are allowed to work autonomously within the configured project permissions.

For every requested feature:

PHASE 1 — UNDERSTAND
- Inspect repository structure.
- Find relevant existing files.
- Read related code before modifying it.
- Identify current architecture.
- Identify dependencies.
- Identify existing APIs/components/models.

PHASE 2 — PLAN
Create a concise implementation plan:
1. frontend changes
2. backend changes
3. database changes
4. AI changes
5. tests

Prefer modifying the minimum necessary files.

PHASE 3 — IMPLEMENT
Implement the feature completely.
Do not stop after creating UI mockups if backend functionality is required.
Do not create fake integrations unless explicitly requested.

PHASE 4 — VERIFY
Run:
- compiler/build
- lint
- relevant tests
- API checks
- integration checks where available

PHASE 5 — DEBUG
If errors occur:
- inspect actual error
- trace root cause
- fix
- rerun verification

PHASE 6 — REVIEW
Check:
- security
- authorization
- validation
- error handling
- responsive UI
- AI output validation
- database consistency

PHASE 7 — REPORT
Return:
IMPLEMENTED:
- ...

FILES:
- ...

DATABASE:
- ...

API:
- ...

AI:
- ...

TESTS:
- ...

KNOWN LIMITATIONS:
- ...

NEXT:
- ...

Never claim completion if verification failed.
