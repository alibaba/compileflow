# Contributing to CompileFlow

Thank you for contributing to CompileFlow. This guide applies across the repository. Workbench changes also follow the
[Workbench contribution guide](compileflow-workbench/CONTRIBUTING.md).

By participating, you agree to the [Code of Conduct](CODE_OF_CONDUCT.md). Report suspected vulnerabilities privately as
described in [SECURITY.md](SECURITY.md).

## Before You Start

- Search the documentation and existing issues before opening a new issue.
- Use the bug report form for reproducible defects and the feature request form for capability proposals.
- Open a pull request directly for a small, self-contained fix.
- Discuss changes to public APIs, persisted data, protocols, security boundaries, or module boundaries before writing a
  large implementation.
- Use the Durable Kernel Change form for changes to Durable APIs, SPIs, state-machine semantics, persisted schema, or
  security boundaries.

See [SUPPORT.md](SUPPORT.md) for support channels and response expectations.

## Development Environment

Java artifacts target Java 17. Use the checked-in Maven Wrapper so local builds use the repository's Maven version.

```bash
git clone https://github.com/alibaba/compileflow.git
cd compileflow
./mvnw compile -pl compileflow-core -am
```

Workbench development requires the Node.js and pnpm versions pinned in `compileflow-workbench/`. Use pnpm for all
workspace commands.

## Repository Boundaries

- `compileflow-api` contains the supported engine API and SPI. Public implementation classes in other modules are not
  automatically supported application APIs.
- Keep deployment control-plane state separate from node-local runtime state.
- Keep browser credentials out of `VITE_*` variables and browser storage.
- Parse external configuration at module boundaries and pass immutable values into runtime code.
- Keep Durable kernel behavior independent of database and platform adapters.
- Update the relevant specification, architecture page, or Supported Surfaces entry when a public contract changes.

See the [module map](docs/en/architecture/module-map.md) and
[Supported Surfaces](docs/en/architecture/supported-surfaces.md) for the complete boundaries.

## Code and Documentation

Keep each change focused and include tests for changed behavior.

For Java changes:

- write English Javadoc for supported public APIs and SPIs;
- add comments only when they explain a non-obvious constraint;
- do not add an unresolved `TODO` or `FIXME` without a linked issue;
- use the repository Checkstyle and Spotless rules;
- keep compiler warnings at zero rather than suppressing broad categories;
- make concurrency tests deterministic with latches, barriers, or controlled executors instead of timing assumptions.

Apply or verify Java formatting with a supported JDK:

```bash
./mvnw -Pexamples,benchmarks -pl '!compileflow-bom' com.diffplug.spotless:spotless-maven-plugin:3.9.0:apply
./mvnw -Pexamples,benchmarks -pl '!compileflow-bom' com.diffplug.spotless:spotless-maven-plugin:3.9.0:check
```

For documentation changes:

- update corresponding English and Chinese documentation together;
- verify commands, configuration names, defaults, versions, API signatures, and links against the implementation;
- distinguish supported behavior from unsupported behavior;
- keep examples aligned with supported APIs;
- use direct, task-oriented language and relative repository links;
- keep compatibility commitments in specifications, the compatibility policy, or Supported Surfaces.

Keep credentials, editor state, generated build output, and local planning notes out of the repository.

## Verification

Run the narrowest test that proves the change, then expand to the affected modules.

```bash
# One Java test class
./mvnw test -pl compileflow-core -am \
  -Dtest=ProcessEventPublisherTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Module checks
./mvnw checkstyle:check -pl compileflow-core -am
./mvnw spotbugs:check -pl compileflow-core -am

# Documentation contracts
python3 scripts/check_architecture_boundaries.py
python3 scripts/check_bilingual_parity.py
python3 scripts/check_internal_links.py
```

Build current reactor artifacts before running SpotBugs if local Maven artifacts may be stale:

```bash
./mvnw install -pl compileflow-core,compileflow-tbbpm,compileflow-bpmn \
  -am -DskipTests
```

Run Workbench checks from `compileflow-workbench/`:

```bash
pnpm type-check
pnpm lint
pnpm test:web
pnpm --filter @compileflow/workbench-dev-gateway test
pnpm verify:delivery
```

CI runs the complete test matrix. Do not claim coverage, performance, security, or compatibility guarantees that are
not enforced by a repository check.

## Pull Requests

Use a clear, imperative title. Conventional Commit prefixes such as `fix(core):`, `feat(api):`, and `docs:` are
recommended but optional.

Describe:

- the problem and the chosen behavior;
- the validation commands and results;
- effects on public APIs, configuration, persistence, security, or operations;
- any changed contracts and their user impact.

Do not include credentials, generated build output, editor state, or unrelated formatting changes.

## Developer Certificate of Origin

Every contribution commit must include a `Signed-off-by` trailer matching its author. The trailer certifies the
[Developer Certificate of Origin 1.1](https://developercertificate.org/): you created the contribution or have the right
to submit it under this project's license.

Create the trailer with `git commit --signoff` or `git commit -s`. Add it to an existing commit with
`git commit --amend --signoff`. Contributions are licensed under the repository's
[Apache License 2.0](LICENSE).
