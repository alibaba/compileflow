# Security Policy

## Supported Versions

Security fixes are provided for the current `2.x` line:

| Line | Supported |
| ---- | --------- |
| 2.x  | Yes       |

## Reporting a Vulnerability

Please report vulnerabilities through GitHub Security Advisories:

1. Open [a private security advisory](https://github.com/alibaba/compileflow/security/advisories/new).
2. Include affected versions or commits, reproduction steps, impact, and any available mitigation.
3. Do not open a public issue for a suspected vulnerability.

If the advisory form is unavailable, email
[yusu1210@126.com](mailto:yusu1210@126.com) with the subject
`CompileFlow security report` and include enough detail to reproduce the issue.

## Response Process

We aim to:

- acknowledge a report within 48 hours;
- triage severity using CVSS or an equivalent impact assessment;
- coordinate a fix on a private branch when public disclosure would increase user risk;
- publish a GitHub Security Advisory and release notes after the fix is available;
- credit reporters unless they prefer to remain anonymous.

## Security Automation

Automated security checks include CodeQL, SpotBugs, dependency review, OWASP Dependency-Check, pnpm audit, Dependabot,
and OpenSSF Scorecard. They cover Java, JavaScript/TypeScript, GitHub Actions, container definitions, and the dependency
lockfiles used to build release artifacts.

Release artifacts include a CycloneDX SBOM, checksums, and provenance. Security findings are evaluated against the
source revision and dependency inventory that produced the affected artifacts.

## Finding And Exception Policy

The following findings block merge or release until fixed or covered by an approved, time-bounded exception:

- a high or critical runtime-dependency vulnerability reported by Dependency Review, or an OWASP Dependency-Check
  runtime finding with CVSS 7.0 or higher;
- a high-severity finding from the complete Workbench pnpm lockfile audit;
- an unsuppressed CodeQL high or critical security alert, or a SpotBugs finding at Medium confidence/severity or higher;
- a dependency license that has not been shown compatible with Apache-2.0 distribution.

Lower-severity findings are triaged for reachability, affected support surface, and compensating controls. A
suppression or risk acceptance must name an owner, rationale, affected versions, mitigation, and
expiry in a private advisory or reviewable issue. A dependency vulnerability judged non-exploitable must also be
represented by a CycloneDX VEX statement tied to the affected SBOM; a comment or scanner ignore entry alone is not
sufficient. Expired exceptions are release-blocking.

## Repository Credential Policy

Repository, registry, signing, and scanning credentials are stored only in the corresponding GitHub organization,
repository, or protected-environment secret store. Workflows grant them only to the job that needs them, never expose
them to untrusted pull-request code, and must not pass them in command-line arguments, artifacts, caches, or logs.
Long-lived release signing keys do not enter the tag workflow.

Maintainers review credential owners, consumers, and unused entries whenever access changes and during release
preparation. Rotate a credential immediately after suspected disclosure, maintainer or service-account removal,
unexpected use, or a provider-mandated event; otherwise follow the credential provider's rotation lifetime. Revoke
unused credentials instead of retaining fallback access. Repository administration must require MFA, least privilege,
and approval from an existing maintainer or organization owner other than the access candidate before collaborator
permissions are elevated, as described in `MAINTAINERS.md`.
