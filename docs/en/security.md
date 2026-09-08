# Security Guide

CompileFlow runs process definitions inside the host JVM; compiled mode generates Java code before execution. Treat
every process definition as executable code.

The [Threat Model](threat-model.md) records assets, actors, trust boundaries, mitigations, and residual deployment risks.
This guide turns those boundaries into configuration and operating requirements.

## Security Boundary

CompileFlow is safe for trusted, reviewed flow definitions. It is not a sandbox for arbitrary user-submitted XML, inline
Java, scripts, Spring bean calls, or deployment requests.

Use application-level controls for authentication, authorization, tenant isolation, approval, and audit logging.

## Built-In Protections

### XML Parser Hardening

The shared XML stream parser enables secure processing and disables DTDs, external entities, and external schema access
before parsing BPMN/TBBPM definitions. Mandatory security controls are fail-closed: if the active JAXP provider cannot
apply one of them, engine configuration fails instead of parsing with weaker settings.

Inline and classpath definitions share `compileflow.engine.definition.max-size` (4 MiB by default). The loader reads
each source once into a bounded immutable byte snapshot, so schema validation and model parsing see the same bytes.
Known network URL protocols returned by an application ClassLoader are rejected.

The embedded engine also performs no remote URL fetches for process definitions. Remote artifact retrieval belongs to
the application or deployment resolver, where authentication, network allowlists, timeouts, size limits, and digest
verification can be enforced before trusted content reaches the compiler.

### Java Identifier Normalization

Generated Java package, class, and engine-helper method names are normalized through `JavaIdentifiers` and
`GeneratedProcessNames`. Process variable names are instead validated as legal, non-reserved Java identifiers during
semantic validation and preserved in generated source, keeping the model, expressions, source mapping, and runtime
state aligned. These constraints prevent invalid generated identifiers and reduce injection risk in generated source
names. Compiler disk output resolves generated source and class files under the configured compile directory after
validating Java fully-qualified names and normalizing paths. In-memory compiled artifacts expose defensive snapshots of
class bytes.

### Server Authentication Guard

`compileflow-workbench-server` supports `X-API-Key` authentication through
`compileflow.workbench.server.authentication.api-key` or
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY`. The key maps to the explicit
`compileflow.workbench.server.authentication.service-principal`; that stable service principal is the audited actor for
control-plane mutations.

Authentication defaults to `API_KEY`, and startup fails when its key is missing or weak. The `DISABLED` mode is accepted
only with an explicit `dev` or `test` profile and never together with `prod`. Vite exposes every `VITE_*` value to
browser clients, so the Web configuration schema accepts no credential. Use an authentication-capable gateway to
authenticate and authorize users, remove client-supplied internal headers, and inject the private Workbench Server
credential only on its protected upstream hop. One shared server key identifies one service principal, not an end user.
Do not accept an unsigned browser actor header. Per-user audit identity is outside the API-key contract; a trusted
authentication gateway must verify and propagate the user principal.

The server compares the configured API key using a constant-time digest comparison. Only `/actuator/health`,
`/actuator/health/liveness`, and `/actuator/health/readiness` are anonymous; do not expose other Actuator, deployment,
or control endpoints through broad path rules.

Do not trust browser-supplied `X-API-Key`, internal identity, forwarding, or client-IP headers. The production gateway
must remove those values before adding its own service credential. Proxy connection failures belong in protected logs;
browser responses should use a safe Problem Detail or a generic gateway error.

### Trusted Draft Execution

`POST /api/executions/preview` compiles and executes the submitted definition with Workbench Server privileges. It is
not a sandbox, and preflight success does not make execution side-effect free. The endpoint is absent by default and is
registered only when `compileflow.workbench.server.preview-execution.enabled=true`.

Keep it disabled in production unless the identity gateway authorizes a dedicated draft-execution capability for trusted
flow authors. Run the Server with least-privilege filesystem, network, database, and container permissions, and expose
only narrowly reviewed Spring action beans. Validation-only workflows must call preflight instead.

## Required Production Controls

### Use Trusted Flow Sources

Prefer classpath, reviewed repository, or approved database sources:

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.BPMN, "order.process", "flows/order.bpmn");
engine.runtime().warmUp(definition);
```

Avoid executing raw XML submitted directly by end users. If external content must be accepted, validate and review it
before deployment:

```java
ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "order.process", xmlContent);
ProcessPreflightOptions options = ProcessPreflightOptions.strict();
ProcessPreflightReport report = engine.tooling().preflight(definition, options);
if (report.getOverallStatus() == ProcessPreflightReport.OverallStatus.FAIL) {
    String reason = report.getItems().stream()
            .filter(item -> item.getStatus() != ProcessPreflightReport.ItemStatus.PASS)
            .map(ProcessPreflightReport.Item::getMessage)
            .findFirst()
            .orElse("Unknown preflight failure");
    throw new SecurityException("Flow preflight failed: " + reason);
}
```

### Restrict Executable Actions

Java actions, Java Code, and Spring bean actions execute with the host application's privileges. The
core-provided QLExpress 4 executor is fixed to `ISOLATED`, a one-second deadline, a per-dimension array limit, and a
fixed safe function set. Compiled QL programs belong to the exact Process runtime that loaded them; there is no
provider-global expression cache. The bundled `qlexpress` provider has no host-access switch. A different function surface or
access policy must use a new semantic language name and own its security and persistence contract.

A Java action is generated as direct Java construction and invocation. Its class and method names are validated, and the
declared class must expose a public no-argument constructor accessible to generated code. It does not fall back to
private reflection or dependency injection. Use a Spring bean action when the component needs injected dependencies.

Java Code is a method body compiled into a generated typed wrapper with `javac --release 17`. Core registers the built-in
Java executor by default. Declared inputs and outputs must use Java platform types. The compiler class path is empty, so
definition-owned code cannot accidentally bind to embedding-application JARs. This limits accidental exposure; it does
not restrict execution authority. Java Code remains trusted in-process computation, not a security sandbox. Do not run
untrusted author code in Workbench Server. If an application accepts such code, execute it outside the CompileFlow
deployment in an environment isolated by operating-system or container controls.

Spring bean actions are denied by default. Expose only exact reviewed names through
`compileflow.engine.components.allowed-beans`, or supply one custom `ProcessComponentResolver`; do not configure both.
An unresolved component fails execution and is never instantiated from the class declared in XML. The bean allowlist
does not create a method sandbox, so expose a narrow adapter/interface containing only operations that flows may call.

Variable defaults are always data literals and are parsed before Java source is generated. `@` has no special meaning.
Invalid literals and unsupported object defaults fail preflight instead of silently becoming `null` or source
fragments.

Runtime type conversion is strict and locale-independent: integral narrowing must be exact, temporal text uses ISO-8601
without an implicit default timezone, and conversion failures do not include the source value. See
[Process Data Types and Conversion](type-system.md) for the complete contract.

Custom script languages are not core-provided executors. Registering one through `ScriptExecutor` explicitly adds that
language and makes its security, timeout, cache, ClassLoader, and lifecycle policy the
application's responsibility. Production deployments should:

- Allow only reviewed flow definitions.
- Treat any definition containing QLExpress or Java Code as executable input and admit only reviewed definitions.
- Keep `compileflow.engine.components.allowed-beans` empty unless a reviewed Spring action requires a bean.
- Restrict which methods are reachable by exposing narrow Spring adapters or resolver-returned interfaces.
- Prefer narrow service adapters over broad application service exposure.
- Run the host application with least-privilege OS, container, database, and network permissions.

### Protect Deployment And Operate APIs

- Require both `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY` and
  `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL` in production.
- Serve APIs only over HTTPS or behind a trusted TLS-terminating gateway.
- Keep Actuator and deployment/control APIs off the public internet. Only `/actuator/health`,
  `/actuator/health/liveness`, and `/actuator/health/readiness` are anonymous.
- Keep Workbench Server and its selected database Provider on private networks behind the same access-control boundary as the trusted edge.
- Never send the Server API key to a browser; CORS and plain Ingress routing are not authentication.
- Configure the edge to strip a browser-supplied `X-API-Key`, internal identity headers, and untrusted
  forwarding/client-IP headers before adding its own service credential.
- Keep edge and upstream failure details in protected logs; browser responses should use safe Problem Details or generic
  gateway errors.
- Workbench Server compares its configured API key with a constant-time digest comparison.

### Isolate Tenants And Environments

- Embedded Engine/Deploy namespaces provide logical scoping, not authorization. Workbench supports only `default`; use separate deployments for isolated tenants.
- Enforce authorization before publishing, creating or changing rollouts, rolling back, executing, or inspecting.
- Use separate databases or row-level isolation where tenant data requires hard boundaries.
- Do not rely on namespace strings alone as an authorization mechanism.

### Limit Resource Exposure

- Put execution endpoints behind rate limits and request-size limits.
- Keep async invocation queue sizes, worker counts, and lease intervals bounded.
- Monitor compilation time, execution duration, memory, and failure rate.
- Keep `compileflow.engine.definition.max-size` close to the largest reviewed production definition; increasing it
  raises the memory available to each concurrent parse.
- Reject structurally invalid flow definitions before deployment.
- In Workbench, treat flow expressions as user input. Client-side simulation must use the allowlisted expression
  interpreter instead of `eval` or
  `new Function`.

### Audit Security-Sensitive Actions

Record who performed:

- Flow creation and update.
- Preflight, publish, rollout create/update/promote/abort, rollback, and delete operations.
- Real execution requests.
- API key and production configuration changes.

Store audit logs in append-only or centrally managed logging infrastructure.

## Reporting Vulnerabilities

Report vulnerabilities privately
through [GitHub Security Advisories](https://github.com/alibaba/compileflow/security/advisories/new).

Do not open public GitHub issues for vulnerabilities.

## References

- [OWASP XXE Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
- [OWASP SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [CompileFlow Resource Management Guide](resource-management.md)
