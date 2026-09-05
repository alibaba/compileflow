# CompileFlow Examples

Runnable samples for the current CompileFlow 2.0 APIs. They use the repository's `2.0.0-SNAPSHOT` artifacts, so run
them from a checkout after installing the required modules.

| Sample                                                                    | Demonstrates                                                                     |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| [spring-boot-basic](spring-boot-basic/README.md)                           | Spring Boot, classpath TBBPM, preflight, and in-process execution               |
| [spring-boot-order-fulfillment](spring-boot-order-fulfillment/README.md)   | Realistic REST order orchestration, gateways, calls, loops, parallelism, retries |
| [spring-boot-durable-postgres](spring-boot-durable-postgres/README.md)     | Durable TBBPM, PostgreSQL, Timer, Wait, Outbox delivery, and completion          |

For the Workbench stack, use the [Workbench deployment guide](../compileflow-workbench/DEPLOYMENT.md).

The [control-flow diagrams](../docs/examples/flow-diagrams/README.md) are explanatory Mermaid sources, not runnable
process definitions.
