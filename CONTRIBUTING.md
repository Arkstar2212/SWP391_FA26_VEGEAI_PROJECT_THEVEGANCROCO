# Contributing to VEGEAI Project 🌿

Thank you for contributing to **VEGEAI**! We maintain high engineering standards to ensure clean architecture, reliable AI integration, and code quality.

## 1. Branching Strategy (Git Flow)
- `main`: Production-ready, stable codebase.
- `develop`: Primary integration branch for sprint deliverables.
- `feature/<feature-name>`: Feature branch branched off `develop`.
- `fix/<bug-name>`: Bug fix branch.

## 2. Commit Message Convention
We adhere to **Conventional Commits**:
- `feat: <description>` - A new feature
- `fix: <description>` - A bug fix
- `docs: <description>` - Documentation changes
- `refactor: <description>` - Code refactoring without behavior change
- `test: <description>` - Adding or updating test suites
- `chore: <description>` - Build tools, dependencies, or configuration

## 3. Architecture Rules
- **Modular Monolith**: Keep domain logic inside designated packages (`modules/`).
- **Separation of Concerns**: Controllers only handle HTTP requests and responses; Services encapsulate business logic; Repositories handle data persistence.
- **DTOs**: Never expose persistence Entities directly through API endpoints. Always use DTOs and `ApiResponse<T>`.
- **Deterministic Math**: Calculations (BMI, daily calories, macronutrient ratios) must run through deterministic Java services, NOT through generative LLM text.
