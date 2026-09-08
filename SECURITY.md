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

## Release Integrity

Release artifacts include CycloneDX SBOMs, checksums, and provenance. A dependency vulnerability determined to be
non-exploitable is documented with a CycloneDX VEX statement tied to the affected SBOM.
