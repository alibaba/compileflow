# CompileFlow Examples

These runnable examples show how to embed CompileFlow in Spring Boot applications. Follow each example's README for
its prerequisites, startup command, expected result, and verification steps.

| Example                                                                    | What it covers                                                                                         |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| [spring-boot-basic](spring-boot-basic/README.md)                           | Spring Boot auto-configuration, classpath TBBPM, preflight, and in-process execution                   |
| [spring-boot-order-fulfillment](spring-boot-order-fulfillment/README.md)   | REST order orchestration with subprocesses, gateways, loops, parallel branches, and retries            |
| [spring-boot-deployment](spring-boot-deployment/README.md)                 | PostgreSQL-backed publication, canary rollout, promotion, abort, rollback, and exact-version execution |
| [spring-boot-durable-postgresql](spring-boot-durable-postgresql/README.md) | Durable TBBPM with PostgreSQL, Timer, Effect, Wait, Outbox delivery, and recovery checks               |

For the Workbench stack, use the [Workbench deployment guide](../compileflow-workbench/DEPLOYMENT.md).

The [control-flow diagrams](../docs/en/examples/flow-diagrams.md) illustrate common patterns but are not runnable
process definitions.
