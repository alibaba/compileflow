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

- a high or critical runtime-dependency vulnerability reported by Dependency Review;
- an OWASP Dependency-Check runtime finding with CVSS 7.0 or higher;
- a high-severity finding from the complete Workbench pnpm lockfile audit;
- an unsuppressed high or critical CodeQL alert, or a SpotBugs finding that fails the repository check;
- a dependency license not shown to be compatible with Apache-2.0 distribution.

Lower-severity findings are evaluated for reachability, affected surfaces, and available mitigations.

Any temporary suppression or risk acceptance must identify its owner, rationale, scope, mitigation, and expiry. A
dependency vulnerability determined to be non-exploitable must also be represented by a CycloneDX VEX statement tied
to the affected SBOM.

## Repository Credential Policy

Repository, registry, signing, and scanning credentials belong only in the corresponding GitHub organization,
repository, or protected-environment secret store. Workflows must not expose credentials to untrusted pull-request
code or place them in command-line arguments, artifacts, caches, or logs.

Repository administration requires MFA and least privilege. Rotate credentials after suspected disclosure, unexpected
use, or an ownership change, and revoke credentials that are no longer needed. Access rules are documented in
[MAINTAINERS.md](MAINTAINERS.md).
