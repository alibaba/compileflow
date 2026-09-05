# Maintainers

CompileFlow is maintained by the project maintainers listed in this file and referenced from `.github/CODEOWNERS`.

## Primary Maintainer

| GitHub        | Affiliation | Scope                                          |
|---------------|-------------|------------------------------------------------|
| @kangzhiqiang | Alibaba     | Repository-wide maintenance and release review |

## Responsibilities

Maintainers are responsible for:

- reviewing code, documentation, CI, dependency, and release changes;
- keeping architecture decisions and public API documentation consistent with the implementation;
- triaging issues and security reports through the channels in
  `SUPPORT.md` and `SECURITY.md`;
- protecting the control-plane/data-plane boundary, fail-fast deploy behavior, public API contracts, and
  Workbench/server contract alignment.

## Updating Maintainers

Maintainer changes require a pull request that updates both `MAINTAINERS.md`
and `.github/CODEOWNERS`. The pull request should explain the ownership change, the affected scope, and the review
expectations for future changes.

## Access Management

Granting or expanding access to repository settings, CI credentials, package publication, signing keys, or other
sensitive resources requires review by an existing maintainer or organization owner other than the candidate. Assign
the narrowest repository and environment role that covers the documented responsibility; contributor activity alone
does not imply administrative access.

Review sensitive access when a role changes and during release preparation. Remove access promptly when the documented
responsibility ends, and update this file and `.github/CODEOWNERS` when ownership changes. Credential storage and
rotation follow `SECURITY.md`; the public maintainer list must not disclose secret values or private recovery material.
