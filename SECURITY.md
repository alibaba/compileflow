# Security Policy

## Supported Versions

CompileFlow currently accepts vulnerability reports for:

| Line                  | Status                                                     |
|-----------------------|------------------------------------------------------------|
| Default branch        | Supported for fixes that have not shipped in a release yet |
| Latest stable release | Supported once a stable release is tagged                  |
| Older release lines   | Not supported unless explicitly listed here                |

Snapshot builds and unreleased changelog entries are development artifacts. Do not treat them as security-supported
stable releases until a GitHub Release is published.

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

The repository runs OpenSSF Scorecard through
`.github/workflows/security-scorecard.yml` and uploads SARIF results to GitHub code scanning. Dependabot tracks Maven,
pnpm, GitHub Actions, Dockerfile, and Docker Compose dependencies.

`.github/workflows/codeql.yml` analyzes Java and JavaScript/TypeScript on pull requests, default-branch changes, and a
weekly schedule. Java SpotBugs remains the faster implementation-level static-analysis gate.

`.github/workflows/dependency-review.yml` rejects pull requests that introduce high-severity vulnerabilities into
runtime dependency scopes. Workbench CI also audits the complete pnpm lockfile at high severity, including build-time
dependencies that execute while producing browser and development-tool artifacts.

`.github/workflows/supply-chain.yml` runs OWASP Dependency-Check on a weekly schedule and rejects Java runtime
dependencies with CVSS 7.0 or higher. Maintainers must configure the repository Actions secret `NVD_API_KEY`; the Maven
profile retrieves it through the environment rather than exposing the value as a command line property.

`.github/workflows/supply-chain.yml` generates a CycloneDX Maven aggregate SBOM for dependency inventory, release
review, and vulnerability impact analysis. The tag release workflow regenerates that SBOM from the tagged source and
attaches it with the release checksum and provenance set.

## Finding And Exception Policy

The following findings block merge or release until fixed or covered by an approved, time-bounded exception:

- a high or critical runtime-dependency vulnerability reported by Dependency Review, or an OWASP Dependency-Check
  runtime finding with CVSS 7.0 or higher;
- a high-severity finding from the complete Workbench pnpm lockfile audit;
- an unsuppressed CodeQL high or critical security alert, or a SpotBugs finding at Medium confidence/severity or higher;
- a dependency license that has not been shown compatible with Apache-2.0 distribution.

Lower-severity findings are triaged for reachability, affected support surface, and compensating controls before a
stable release. A suppression or risk acceptance must name an owner, rationale, affected versions, mitigation, and
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
and a non-candidate review before collaborator permissions are elevated, as described in `MAINTAINERS.md`.
