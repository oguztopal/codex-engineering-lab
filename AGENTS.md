
# Codex Engineering Lab

## Project Context
Java 25 / Spring Boot 4 payment processing application.

Architecture:
Controller → Service → Repository → Entity

DTOs define public API contracts.
MapStruct handles entity/DTO transformations.

## Engineering Rules
- Preserve existing business behavior and REST contracts.
- Keep business logic inside services.
- Use BigDecimal for monetary calculations.
- Validate incoming requests.
- Use transactions for multi-step state changes.
- Follow existing exception handling conventions.
- Avoid unnecessary refactoring.

## Context Efficiency
- Start with a targeted search.
- Use Serena for symbol and reference analysis when useful.
- Use Repomix only when a repository overview is needed.
- Use Context7 only for uncertain framework APIs.
- Avoid loading unrelated source files.
- Prefer small, focused tool responses.

## Verification
- Add tests for changed business rules.
- Run relevant tests before the full suite.
- Run `./mvnw clean test` before completing.
- Review the final Git diff.
- Report remaining risks and failed checks.
