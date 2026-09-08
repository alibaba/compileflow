# Durable wait-token security

Durable wait tokens are bearer credentials for completing a specific wait occurrence. Request-deduplication keys,
page-token signing keys, payload-encryption keys, and application codecs belong to the HTTP, RPC, or messaging adapter
that defines those protocols; rotating them does not change Durable Store identity.

## Wait tokens

The Store keeps only a SHA-256 digest in the wait record, so Durable does not maintain a wait-token keyring. Crash-safe
delivery may temporarily retain the plaintext token in an active outbox record until delivery succeeds or the wait is
completed, cancelled, or expired. Protect the outbox and any long-lived integration mapping as credential storage.

Never log a wait token or place it in a metric label, browser-visible URL, third-party metadata, or Workbench view. If
a token is exposed, block delivery where possible, inspect the committed wait status, and do not issue another result
for the same wait occurrence.

See the [operations runbook](durable-operations-runbook.md).
