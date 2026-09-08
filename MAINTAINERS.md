# Maintainers

CompileFlow is maintained by the project maintainers listed in this file and referenced from `.github/CODEOWNERS`.

## Primary Maintainer

| GitHub    | Affiliation | Scope                       |
| --------- | ----------- | --------------------------- |
| @yusu1210 | Alibaba     | Repository-wide maintenance |

## Responsibilities

Maintainers are responsible for:

- reviewing contributions and maintaining repository quality;
- keeping public APIs, documentation, architecture, and implementation consistent;
- triaging issues and security reports through [SUPPORT.md](SUPPORT.md) and [SECURITY.md](SECURITY.md);
- maintaining release integrity and the documented boundaries between the engine, Deploy, Durable, and Workbench.

## Updating Maintainers

Maintainer changes require a pull request that updates this file and `.github/CODEOWNERS`. Describe the ownership
change and the affected scope.

## Access Management

Access to repository settings, CI credentials, package publication, and signing keys follows least privilege and
requires approval from an existing maintainer or organization owner other than the candidate. Remove access when the
responsibility ends. Store credentials only in the corresponding organization, repository, or protected-environment
secret store; never place them in source, command-line arguments, artifacts, caches, or logs. Rotate credentials after
suspected disclosure, unexpected use, or an ownership change, and revoke credentials that are no longer needed.
