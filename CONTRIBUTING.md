# Contributing to CompileFlow

Thank you for contributing. This guide is the repository-wide contribution contract. Workbench changes also follow the
[Workbench addendum](compileflow-workbench/CONTRIBUTING.md).

By participating, you agree to the [Code of Conduct](CODE_OF_CONDUCT.md). Report suspected vulnerabilities privately
through the process in
[SECURITY.md](SECURITY.md), not through a public issue.

## Choose the Right Channel

- Use the bug report form for reproducible defects.
- Use the feature request form for a concrete problem or capability proposal.
- Use the dedicated Durable Kernel Change form before changing Durable public API/SPI, state-machine semantics,
  persisted schema, security boundaries, or module surface. Complete the linked fifteen-section ADR template before
  implementation.
- For usage questions, search the documentation and existing issues first. The project does not currently provide a
  dedicated Q&A channel.
- Open a pull request directly for a small, self-contained fix.
- Discuss changes to public APIs, persisted data, protocols, security, or module boundaries before investing in a large
  implementation.

See [SUPPORT.md](SUPPORT.md) for links and support expectations.

## Development Baseline

Java artifacts target Java 17. CI builds on Java 17, 21, and 25; the full suite runs once on Java 17, while newer JDKs
run focused compatibility checks. Use the checked-in Maven Wrapper instead of a system Maven installation.

```bash
git clone https://github.com/alibaba/compileflow.git
cd compileflow
./mvnw compile -pl compileflow-core -am
```

Workbench development requires the Node.js and pnpm versions pinned under
`compileflow-workbench/`. Use pnpm only.

## Repository Boundaries

- `compileflow-api` contains the supported engine API and SPI.
- Implementation packages in core, parser, deployment, Spring, server, and Workbench modules are not public merely
  because a type is Java `public`.
- Keep deployment control-plane state separate from node-local runtime state.
- Keep browser credentials out of `VITE_*` variables and browser storage.
- Keep external configuration parsing at module boundaries; pass immutable values into runtime code.
- Update the relevant architecture decision when changing an invariant.
- Keep Durable changes inside the documented product boundary unless an accepted ADR proves an independently useful
  responsibility and dependency lifecycle. Do not make platform adapters a prerequisite for kernel correctness.

The detailed module and supported-surface maps are in
[docs/architecture/03-MODULE_MAP.en.md](docs/architecture/03-MODULE_MAP.en.md) and
[docs/architecture/06-SUPPORTED_SURFACES.en.md](docs/architecture/06-SUPPORTED_SURFACES.en.md).

## Implement the Change

Prefer the existing module boundary and local design patterns. Keep the change focused, remove code and documentation
made obsolete by it, and avoid compatibility shims unless the current compatibility policy requires one.

For Java:

- write English Javadoc for supported public API and SPI;
- write comments only when they explain a non-obvious invariant;
- add no unresolved `TODO` or `FIXME` without a linked issue;
- format sources with Spotless + Palantir Java Format (4-space indentation, 120-column limit, and lambda-friendly
  wrapping); extract well-named local variables or methods when an expression remains deeply nested after formatting;
- use natural camel-case acronym segments in owned identifiers (`Jdbc`, `Hmac`, `Bpmn`, `Tbbpm`, `Xml`, `Ui`,
  `Url`); preserve uppercase spellings only for protocol constants, serialized values, and external API names;
- name runtime types by stable facts and capabilities, not vague workflow phases: prefer `Resolved`, `Compiled`,
  `Loaded`, `Bound`, `Executable`, `Plan`, `Graph`, `Program`, or `Cache` when those facts are exact; do not use
  `Prepare`/`Prepared` as a catch-all for compilation, loading, resolution, validation, or binding;
- use `Compiler` for representation transforms, `Lowerer` for lowering to a target IR, `Loader` for making an exact
  runtime locally available, `Factory` for construction, `Resolver` for selecting an exact answer, `Executor` for
  execution, and `Manager` only when the type owns a subordinate lifecycle;
- use `Graph` only for explicit nodes and edges, `Scope` for enter/close lifetime, `View` for read models, and `Worker`
  for autonomous bounded work loops;
- avoid context-free suffixes such as `Support`, `Info`, and `Holder`, and adjectives such as `Managed`; name the
  capability, represented fact, owned object, or lifecycle state instead;
- use the repository Checkstyle rules for naming, imports, and related gates;
- keep compiler warnings at zero instead of suppressing broad categories;
- add deterministic concurrency tests with latches, barriers, or controlled executors rather than timing guesses.

```bash
# Apply or verify Java formatting (requires JDK 17+ to run Maven plugins)
./mvnw -Pexamples,benchmarks spotless:apply
./mvnw -Pexamples,benchmarks spotless:check
```

For documentation:

- update English and Chinese user documentation together;
- keep examples aligned with supported APIs;
- use relative repository links;
- distinguish implemented behavior from future work;
- verify commands, configuration names, defaults, version claims, API signatures, and links against the current code;
- write direct, task-oriented prose and remove duplicated explanations or unsupported claims;
- keep public compatibility commitments in specifications, the compatibility policy, or Supported Surfaces.

Keep the root README focused on project selection and the first successful run. Put detailed procedures in `docs/en`
and `docs/zh`, protocol rules in `docs/specs`, and current component boundaries in `docs/architecture`. Keep
private research, temporary plans, generated review material, credentials, editor state, and local tool sessions
outside the repository. Do not duplicate version, support, or maturity claims across pages.

## Verify the Change

Start with the narrowest test that proves the behavior, then expand to affected module boundaries.

```bash
# One Java test class
./mvnw test -pl compileflow-core -am \
  -Dtest=ProcessEventPublisherTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# One affected Java reactor
./mvnw checkstyle:check -pl compileflow-core -am
./mvnw spotbugs:check -pl compileflow-core -am

# Supported API documentation
./mvnw checkstyle:check \
  -pl compileflow-api,compileflow-deploy/compileflow-deploy-api \
  -Dcheckstyle.config.location=checkstyle-javadoc.xml
./mvnw javadoc:javadoc \
  -pl compileflow-api,compileflow-deploy/compileflow-deploy-api

# Repository contracts and documentation
python3 scripts/check_architecture_boundaries.py
python3 scripts/check_bilingual_parity.py
python3 scripts/check_internal_links.py
```

Build current reactor artifacts before running SpotBugs if local Maven artifacts may be stale:

```bash
./mvnw install -pl compileflow-core,compileflow-tbbpm,compileflow-bpmn \
  -am -DskipTests
```

Run Workbench commands from `compileflow-workbench/`:

```bash
pnpm --filter @compileflow/workbench-web type-check
pnpm --filter @compileflow/workbench-web test
pnpm --filter @compileflow/workbench-dev-gateway test
pnpm verify:delivery
```

CI is the authority for the complete matrix. Do not claim a coverage, performance, security, or compatibility guarantee
that is not enforced by a repository gate.

## Submit a Pull Request

Use a clear, imperative commit and pull request title. Conventional Commit prefixes such as `fix(core):`, `feat(api):`,
and `docs:` are recommended but are not a substitute for a useful description.

The pull request should state:

- the problem and why it matters;
- the chosen behavior and important alternatives;
- exact validation commands and results;
- public API, configuration, persistence, security, or operational impact;
- migration notes for intentional breaking changes.

Generated output, credentials, editor state, and unrelated formatting changes must not be included. A maintainer merges
after the required CI checks and review are complete.

## Developer Certificate Of Origin And License

Every contribution commit must carry a `Signed-off-by` trailer matching its author. The trailer certifies the
[Developer Certificate of Origin 1.1](https://developercertificate.org/): you created the contribution or otherwise
have the right to submit it under this project's license. Create it with `git commit --signoff` (or `git commit -s`);
amend an existing commit with `git commit --amend --signoff`.

The pull-request DCO gate checks every non-merge commit and rejects a trailer copied from a different email address.
Signing off is a legal-origin assertion, not a statement that the change has passed technical review.

Contributions are licensed under the repository's
[Apache License 2.0](LICENSE).
