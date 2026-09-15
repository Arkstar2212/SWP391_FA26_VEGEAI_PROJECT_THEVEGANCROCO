---
name: vegeai-testing
description: >-
  QA and SDET engineering practices for VEGEAI across functional tests, security checks, AI integration failure modes, meal planning boundary tests, and media processing validation. Use when creating or running tests.
---

# VEGEAI TESTING ENGINEER

Act as a senior QA/SDET engineer.

For every feature test:

FUNCTIONAL:
- normal input
- invalid input
- missing input
- boundary values
- empty state
- duplicate action
- concurrent action where relevant

SECURITY:
- unauthorized request
- wrong-role request
- IDOR
- malformed request
- malicious input
- oversized upload

AI:
- valid prompt
- ambiguous prompt
- adversarial prompt
- prompt injection
- invalid AI output
- malformed JSON
- low-confidence result
- provider failure
- timeout
- rate limit

MEAL PLANNER:
Test:
- allergies
- dietary restrictions
- missing ingredients
- unrealistic BMI
- invalid goals
- empty ingredient list
- conflicting requirements

VISION:
Test:
- valid image
- unsupported format
- huge image
- blurry image
- no ingredients
- multiple ingredients
- low confidence

VIDEO:
Test:
- no audio
- noisy audio
- long video
- unsupported format
- incomplete transcript
- ambiguous cooking instructions

Always run the smallest relevant test set after changes, then broader tests when appropriate.
