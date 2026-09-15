---
name: vegeai-admin-ai-monitoring
description: >-
  Specifications for building the VEGEAI administrator control center, content moderation queue, AI monitoring dashboard, metrics, and audit trails. Use when building admin and AI observability features.
---

# VEGEAI ADMIN AND AI MONITORING

Build an administrator control center.

ADMIN DASHBOARD SHOULD PROVIDE:

CONTENT:
- users
- blogs
- videos
- comments
- categories
- reports

AI MODERATION:
- flagged content
- confidence
- reason/category
- model
- timestamp
- current status
- admin decision

AI MONITORING:
- model usage
- requests
- success rate
- failure rate
- latency
- token usage where available
- moderation statistics
- recommendation interactions
- user corrections
- AI accuracy metrics when ground-truth data exists

IMPORTANT:
Do not label a metric "accuracy" unless there is a defined evaluation methodology or ground truth.

ADMIN ACTIONS:
- approve
- reject
- restore
- override AI decision
- mark false positive
- mark false negative
- inspect AI reasoning/metadata where safe

Keep an audit trail for important administrative actions.
