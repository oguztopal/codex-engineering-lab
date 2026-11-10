# Refund Challenge

Extend the baseline payment API with refund processing while preserving its existing layered architecture and response conventions.

## Acceptance criteria

- A completed payment can be refunded through a public HTTP endpoint.
- A refund request must contain a positive amount with no more than two decimal places.
- The refund amount must equal the original payment amount; partial refunds are outside this challenge.
- Only a payment in `COMPLETED` status can be refunded.
- A payment cannot be refunded more than once.
- A successful refund changes the payment status to `REFUNDED`.
- The status change is recorded in transaction history with `COMPLETED` as the previous status and `REFUNDED` as the new status.
- The payment update and transaction-history insert are atomic.
- Missing payments, invalid requests, and invalid transitions use the API's existing error format and suitable HTTP status codes.
- Unit and integration tests cover success, validation, duplicate refunds, invalid status, history, and rollback-sensitive behavior.
- Existing merchant, payment creation, completion, listing, and history behavior continues to pass unchanged.

## Out of scope

- Partial refunds
- Multiple refunds for one payment
- External payment providers
- Asynchronous processing
- Authentication and authorization
- New infrastructure

