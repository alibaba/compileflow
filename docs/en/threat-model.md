# Threat Model

CompileFlow's security assessment covers the supported Engine, Deploy, Durable, and Workbench surfaces. It identifies
the threats the project mitigates, the controls owned by an embedding application or deployment, and the remaining
risks. [Supported Surfaces](architecture/supported-surfaces.md) defines support levels, while the
[Security Guide](security.md) provides configuration and operating guidance.

## 1. Scope And Trust Boundaries

| Boundary                               | Trusted input                                                                                     | Authority and output                                                                                                                                                             |
| -------------------------------------- | ------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Engine parser and compiler             | Reviewed TBBPM/BPMN definitions and explicitly registered extensions                              | Generated Java executes with the host JVM's process, classpath, filesystem, network, and identity privileges                                                                     |
| Deploy control plane and runtime       | Authenticated publication and routing commands                                                    | Immutable artifacts, aliases, rollout state, and runtime selection                                                                                                               |
| Durable kernel and Store               | Validated strict-profile process definitions, typed values, and application-provided capabilities | Persisted Run state with same-Run invocation frames, leases, Effects, Wait-token digests, and Outbox records; raw Wait tokens may exist temporarily in protected Outbox delivery |
| Workbench browser, gateway, and Server | Authenticated operator requests from a trusted edge                                               | Definition editing, deployment mutation, execution, monitoring, and database access                                                                                              |
| CI and release                         | Reviewed source, pinned dependencies, protected credentials, and an authorized tag                | JARs, source archive, SBOMs, checksums, and signed provenance                                                                                                                    |

Process definitions, Java Code, Java actions, scripts, Spring beans, plugins, and Store/provider implementations are
trusted executable inputs. CompileFlow does not claim to sandbox them. Authentication, end-user authorization, tenant
policy, TLS termination, database hardening, backup, and infrastructure isolation remain outside the library boundary.

## 2. Assets And Actors

Security-sensitive assets are process definitions and immutable artifact digests; deployment aliases and rollout state;
execution variables, Durable snapshots, Effect payloads, Wait tokens, and Outbox records; database, API, signing, and CI
credentials; audit records; and published release assets with their SBOM and provenance identity.

Relevant actors are:

- maintainers and release operators with repository or signing authority;
- trusted flow authors and deployment operators;
- authenticated application services and Workbench gateway principals;
- embedding applications, extensions, Effect adapters, Store providers, and infrastructure administrators;
- unauthenticated network clients, malicious or compromised dependencies, and attackers controlling an unreviewed
  definition, browser request, database row, artifact projection store, or CI metadata.

## 3. Principal Data Flows

1. A reviewed definition enters through classpath, inline application input, Deploy, or Workbench;
   bounded parsing and preflight produce an exact source digest before compilation and execution in the host JVM.
2. An authenticated publication stores immutable Version identity without changing traffic. A separate revision-checked
   rollout changes routing state; a runtime resolves
   an alias to one exact Version and verifies the selected content identity before loading it.
3. A Durable command enters one Run-authority transaction; the Store commits state, Effects, Wait occurrences,
   same-Run invocation frames, and Outbox intent, while leases and fencing reject stale workers.
4. A browser reaches Workbench Server only through a trusted edge that removes client-supplied internal headers and adds
   a server-side credential. The browser bundle contains no Server API key.
5. CI builds an authorized commit with pinned tools and dependency manifests. A tag release assembles the complete
   asset set, binds its hashes to provenance, and publishes it atomically.

## 4. Threat Analysis

| ID    | Threat                                                                                                             | Project controls                                                                                                                                                                                                                                                            | Residual deployment responsibility                                                                                                                                        |
| ----- | ------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TM-01 | An untrusted definition or extension executes arbitrary code in the host                                           | Definitions are documented as trusted code; semantic validation, strict typing, exact component allowlists, and an empty Java Code compilation classpath reduce accidental exposure                                                                                         | Review and authorize definitions; isolate untrusted author code in a separate OS/container boundary; run the host with least privilege                                    |
| TM-02 | XXE, remote schema access, path traversal, or oversized definitions disclose files or exhaust resources            | Secure XML processing disables DTDs, external entities, and external schemas; source size is bounded; classpath paths reject schemes, parent traversal, and known network ClassLoader URLs                                                                                  | Constrain custom ClassLoaders and artifact resolvers; apply network and memory limits                                                                                     |
| TM-03 | A client bypasses Workbench authentication or spoofs an internal identity                                          | Production startup fails without a strong API key and service principal; comparison is constant-time; only health endpoints are anonymous; the supplied gateway removes client credentials and forwarding headers                                                           | Terminate TLS, authenticate and authorize users, strip untrusted headers, protect the upstream hop, rotate credentials, and provide per-user audit identity when required |
| TM-04 | A principal reads or mutates another tenant's definitions or executions                                            | Namespaces and exact process identity prevent accidental key collisions; mutation APIs record the authenticated service actor                                                                                                                                               | Namespace is not authorization. Enforce tenant policy before every control, execution, and inspection request; use database isolation where required                      |
| TM-05 | An artifact is substituted, stale routing is accepted, or rollback targets the wrong code                          | Immutable Versions carry content identity; publication and routing use explicit state transitions, optimistic checks, and exact targets; release assets have hashes and provenance                                                                                          | Restrict publication authority, secure artifact transport and storage, review rollouts, and monitor reconciliation failures                                               |
| TM-06 | Retry, crash, or lease expiry duplicates an irreversible side effect or loses completion                           | Durable uses stable Run, occurrence, and event identities; transactional state changes; leases; fencing; explicit UNKNOWN Effect state; and idempotent Outbox delivery contracts                                                                                            | Effect providers must deduplicate, reconcile UNKNOWN outcomes, authenticate Outbox consumers, and preserve their own external transaction evidence                        |
| TM-07 | Variables, payloads, tokens, credentials, or source leak through responses, logs, browser storage, or build output | Error contracts avoid raw values; guidance forbids sensitive logging; Workbench accepts no browser credential configuration; generated/local artifacts and credentials are rejected from source control                                                                     | Encrypt transport and storage, restrict logs and backups, apply retention/redaction, and use a secret manager rather than source or browser state                         |
| TM-08 | Expensive parsing, compilation, execution, queues, or API calls cause denial of service                            | Definition size, compiler work, worker pools, queues, deadlines, lease batches, and shutdown are bounded and observable                                                                                                                                                     | Apply request limits, quotas, admission control, CPU/memory limits, capacity planning, and alerts at the hosting edge                                                     |
| TM-09 | Database compromise, schema drift, or data loss corrupts control/Durable authority                                 | Transactions, versioned migrations, schema validation, optimistic concurrency, lease fencing, and crash/restart tests protect application invariants                                                                                                                        | Use least-privilege database roles, encrypted connections and storage, restricted administration, tested backups, point-in-time recovery, and database monitoring         |
| TM-10 | A dependency, CI action, base image, wrapper, or release asset is replaced                                         | Lockfiles and managed manifests, checksummed Maven Wrapper downloads, commit-pinned Actions, digest-pinned images, dependency review, CodeQL, SpotBugs, SBOMs, checksums, and GitHub artifact attestations carrying SLSA build provenance protect the repository-owned path | Protect repository settings and CI secrets with MFA and review; verify release provenance; assess registry, runner, and build-service trust                               |
| TM-11 | Browser content injection or a compromised edge gains Server authority                                             | The provided nginx profile sets a restrictive CSP and security headers; React does not receive the Server credential; the local gateway clears sensitive client headers                                                                                                     | Review any custom HTML/rendering and gateway policy, constrain outbound browser connections, and do not treat CORS or CSP as authentication                               |

## 5. Risk acceptance and maintenance

Published dependency inventories have distinct scopes: `compileflow-bom.json` covers Maven libraries,
`compileflow-workbench-server-bom.json` covers the distributed Workbench backend, and
`compileflow-workbench-web-bom.json` covers production browser dependencies. The application inventory must match both
the server and all-in-one JARs, including embedded libraries and the Spring Boot loader. All three SBOMs are published
with the release checksums and provenance.

An unresolved threat affecting a Supported surface is release-blocking unless maintainers record an explicit owner,
impact analysis, mitigation, and expiry in a private advisory or reviewable issue. A dependency finding judged
non-exploitable must be represented in a CycloneDX VEX document tied to the affected SBOM rather than silently
suppressed.

Reassess this model for every major security-boundary change and before each stable minor or major release. Changes to
parsing, executable actions, authentication, routing authority, Durable transactions, persistence, CI credentials, or
release identity must update this model, the Security Guide, and automated tests at the affected boundary.
