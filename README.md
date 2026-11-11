# Codex Engineering Lab

`codex-engineering-lab` is a small, fully local payment-processing API built as the shared baseline for the Medium article **“Stop Just Prompting Codex. Give It a Better Way to Work.”** Readers can use the same code and feature request to compare plain-prompt Codex with a project-aware environment using MCP servers and Agent Skills.

This repository is an engineering lab, not a production payment gateway. It has no authentication, external payment provider, or persistent production database.

> Medium article: _link will be added after publication_

## Stack

- Java 25 and Spring Boot 4.1
- Spring Web MVC, Spring Data JPA, and Bean Validation
- H2 embedded database
- Lombok and MapStruct
- springdoc OpenAPI / Swagger UI and Spring Boot Actuator
- Maven Wrapper, JUnit 5, and Mockito

## Architecture

The application follows a direct layered flow:

```text
HTTP controller -> service (business rules/transactions) -> repository -> JPA entity
                         |                                  |
                    MapStruct DTOs                   embedded H2
```

It supports merchant creation and lookup, payment creation and pagination, payment completion, and payment transaction history. Every response uses a consistent success/error envelope.

## Quick start

Prerequisite: JDK 25 (`java -version` should report 25).

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8080`. Useful local URLs:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health
- H2 console: http://localhost:8080/h2-console

Run all tests with:

```bash
./mvnw test
```

## Example requests

Create a merchant:

```bash
curl -i -X POST http://localhost:8080/api/merchants \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme Store","email":"payments@acme.test"}'
```

Create a payment (replace `1` with the merchant ID):

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"merchantId":1,"amount":42.50,"currency":"USD"}'
```

Complete it and inspect its history:

```bash
curl -i -X POST http://localhost:8080/api/payments/1/complete
curl http://localhost:8080/api/payments/1/transactions
curl "http://localhost:8080/api/payments?page=0&size=20"
```

## Intentionally missing: refunds

`REFUNDED` is present in the payment-status enum, but no endpoint or business logic can reach it. Refund implementation and tests are intentionally excluded from this clean baseline. The future experiment will give both Codex environments the identical request in [`lab/prompts/refund-prompt.md`](lab/prompts/refund-prompt.md), then record observations in [`lab/results/README.md`](lab/results/README.md).

The full acceptance criteria live in [`lab/tasks/refund-challenge.md`](lab/tasks/refund-challenge.md).

## API summary

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/merchants` | Create a merchant |
| `GET` | `/api/merchants/{id}` | Get a merchant |
| `GET` | `/api/merchants` | List merchants |
| `POST` | `/api/payments` | Create a payment |
| `GET` | `/api/payments/{id}` | Get a payment |
| `GET` | `/api/payments?page=0&size=20` | List payments |
| `POST` | `/api/payments/{id}/complete` | Complete a created payment |
| `GET` | `/api/payments/{id}/transactions` | Get status history |

## License

MIT


### Refund correction on the plain branch

This follow-up adapts the original plain cancellation implementation to full refunds.
`POST /api/payments/{id}/refund` accepts `{"amount":42.50}`. Only COMPLETED
payments may be refunded, and the amount must match the original payment.
Duplicate refunds return 409. Payment and history changes share one transaction;
optimistic locking protects competing requests.

This is a post-run correction, not a fresh benchmark. Earlier timing and token
measurements must not be attributed to this revised implementation.
