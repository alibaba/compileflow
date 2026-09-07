# Durable Capability and Outer-Key Lifecycle

CompileFlow Durable has no Kernel request-identity HMAC root, page-token keyring, payload-encryption key identity, or
application codec key. Request dedupe and opaque page tokens belong to the HTTP/RPC/MQ adapter that defines those
protocols; rotate their keys according to that adapter's lifecycle without changing Durable Store identity.

## Wait tokens

Wait tokens are random bearer capabilities. The Wait authority row stores only the SHA-256 digest, so there is no Durable
Wait-token keyring to rotate. Crash-safe delivery temporarily retains the plaintext token in an active Outbox record until
delivery succeeds or the authority is completed, cancelled, or expired. Protect that Outbox and any long-lived integration
mapping as credential storage. Never log the token or place it in a metric label, browser-visible URL, third-party metadata,
or Workbench view. If exposed, block delivery where possible, inspect its committed status, and do not mint an alternate
result for the same Wait boundary.

See the [operations runbook](durable-operations-runbook.md).
