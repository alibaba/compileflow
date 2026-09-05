# Durable Capability and Outer-Key Lifecycle

CompileFlow Durable has no Kernel request-identity HMAC root, page-token keyring, payload-encryption key identity, or
application codec key. Request dedupe and opaque page tokens belong to the HTTP/RPC/MQ adapter that defines those
protocols; rotate their keys according to that adapter's lifecycle without changing Durable Store identity.

## Wait tokens

Wait tokens are random bearer capabilities. The Store persists only their SHA-256 digest; there is no Durable Wait-token
keyring to rotate. Protect the plaintext token in the authenticated Outbox channel and in any long-lived integration
mapping. Never log it or place it in a metric label, browser-visible URL, third-party metadata, or Workbench view. If exposed,
block delivery where possible, inspect its committed status, and do not mint an alternate result for the same Wait
boundary.

See the [operations runbook](durable-operations-runbook.md).
