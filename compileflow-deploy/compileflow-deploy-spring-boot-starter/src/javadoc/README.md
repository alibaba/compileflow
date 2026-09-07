# CompileFlow Deploy Spring Boot Starter

This format- and provider-neutral starter composes the deployment control plane and node-local runtime. It defines no
public Java types of its own. Use it when an application supplies a complete `DeployStore`; for first-party PostgreSQL or
MySQL Store wiring, use `compileflow-deploy-spring-boot-starter-postgresql` or
`compileflow-deploy-spring-boot-starter-mysql`. Add each required process-format frontend explicitly.
