---
name: safe-feature-development
description: Analyze, implement, and verify changes across multiple layers of an existing Java Spring Boot application. Use for new features, business rule changes, and safe refactoring.
---

# Safe Feature Development

Follow this workflow when implementing features in the Codex Engineering Lab.

## 1. Understand the Request

- Identify the business requirements.
- Explore existing implementations before writing code.
- Determine affected controllers, services, repositories, entities, and DTOs.
- Do not assume an implementation is missing without checking.

## 2. Explore Efficiently

Choose tools based on the task:

- **Serena MCP:** Find symbols, implementations, and references.
- **Repomix CLI:** Generate a filtered repository overview only when broader context is needed.
- **Context7 MCP:** Retrieve documentation only when framework behavior is uncertain.
- **Native tools:** Prefer targeted searches and file reads for simple operations.

Avoid unnecessary tool calls and large outputs.

## 3. Plan

Before editing:

- Identify affected files.
- Summarize the implementation approach.
- Identify existing business rules and compatibility risks.

Keep the plan concise.

## 4. Implement

- Make focused, minimal changes.
- Preserve existing architecture and API contracts.
- Keep business logic in services.
- Use BigDecimal for monetary amounts.
- Handle state transitions and transactional consistency.
- Avoid unrelated refactoring.

## 5. Verify

- Add unit and integration tests for the new behavior.
- Run focused tests first.
- Run the complete Maven test suite.
- Review the Git diff.
- Check that existing behavior remains unchanged.

## 6. Report

Provide a short summary containing:

- Files changed.
- Business rules implemented.
- Tests executed and results.
- Remaining risks or limitations.

## Context Efficiency Rules

- Do not read the entire repository by default.
- Prefer symbol-level exploration when useful.
- Avoid repeatedly reading unchanged files.
- Keep tool output focused.
- Load extra documentation only when needed.
- Do not invoke MCP tools without a clear purpose.
