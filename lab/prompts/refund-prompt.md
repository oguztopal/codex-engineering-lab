# Refund Feature Request

Implement refund processing for the payment API.

A client must be able to refund an existing completed payment by submitting a refund amount. The amount must be positive, have no more than two decimal places, and equal the payment's original amount; partial refunds are not supported. Only completed payments may be refunded, and each payment may be refunded at most once.

When a refund succeeds, change the payment status to `REFUNDED` and add a transaction-history entry containing the previous status, new status, and timestamp. The payment update and history record must be atomic.

Use the application's existing API response and error conventions. Return appropriate HTTP errors for a missing payment, invalid request, non-completed payment, or duplicate refund. Preserve all existing behavior and add meaningful unit and integration tests for the new feature, including successful refunding, validation, invalid transitions, duplicate prevention, transaction history, and consistency.

Do not add external infrastructure or integrate with a real payment provider.

