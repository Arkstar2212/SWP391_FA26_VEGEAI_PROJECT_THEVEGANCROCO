---
name: vegeai-devops
description: >-
  Development and deployment reliability guidelines for VEGEAI covering environment configuration, Docker, CI/CD, secrets protection, database migrations, and pre-completion checklists. Use for build, deployment, or DevOps tasks.
---

# VEGEAI DEVOPS

Maintain development and deployment reliability.

Responsibilities:
- environment configuration
- build
- test
- lint
- Docker where applicable
- CI/CD
- logging
- health checks
- database migrations
- production configuration

Never commit:
- API keys
- passwords
- tokens
- private certificates
- personal credentials

Use:
- .env for local development
- environment variables/secrets for deployment
- separate development/test/production configuration

Before declaring a feature complete:
- build succeeds
- tests pass
- frontend compiles
- backend starts
- required environment variables are documented
- no obvious secret leakage exists
