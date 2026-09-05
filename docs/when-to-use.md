# When to Use CompileFlow / 何时使用 CompileFlow

> **English version first; Chinese version follows.**
> **英文版在前，中文版在后。**

---

## English: When to use CompileFlow

CompileFlow compiles supported process definitions into Java bytecode and offers explicit execution surfaces. The
`ProcessEngine` provides ProcessEngine execution; it does not own crash-safe continuation recovery. Workbench
Server can persist and retry a whole request. The
opt-in PostgreSQL-backed `DurableProcessEngine` instead persists one Run at supported Wait, Timer, governed Effect, and
terminal boundaries. The deployment system separately owns immutable versions, routes, rollout history, and delivery
state. Use this guide to choose the right surface rather than assuming that one switch changes their semantics.

### 1. Use CompileFlow when

- **Hot business logic with high call rate.** Pricing, inventory checks, marketing rule evaluation, order validation,
  fraud signals — workloads where the definition changes less often than it executes.
- **Business and engineering teams need one reviewable process model.** Workbench edits the same supported definition
  that the Java engine validates and executes.
- **You need version routing and canary release for flows.** Immutable versions, revision-checked alias routes,
  deterministic cohort assignment from a caller-supplied routing key, and auditable rollout history support controlled
  promotion, abort, and rollback.
- **You need a typed application boundary.** `ProcessEngine.execute(ref, request, MyResponse.class, options)` maps
  through the configured `ProcessDataMapper` and returns `ProcessResult<MyResponse>`.
- **Your definitions are TBBPM-friendly.** `while` loops, `break`/`continue`, and first-class actions (Java,
  Spring bean, QL) match your expressiveness needs. Deployment identity stays outside XML in
  `ProcessRef` and deploy metadata.
- **You need persisted whole-process async invocation in Workbench Operate.** `compileflow-workbench-server` provides
  a DB-backed state machine (`queued`/`running`/`succeeded`/`dead_letter`) with lease fencing. It retries a
  complete Engine call and is distinct from the CompileFlow Durable kernel.
- **You need a resumable supported TBBPM or BPMN Run.** CompileFlow Durable persists supported structured control flow,
  loops, Waits, Timers, governed Effects, exact child calls, cancellation, and audited recovery in PostgreSQL.
  Unsupported or non-portable contracts fail during compatibility checking or preparation.
- **You are on Spring Boot and want an application-scoped engine.** `compileflow-spring-boot-starter` auto-configures a
  long-lived engine and bounded observability.

### 2. Do NOT use CompileFlow when

- **You need a complete human-task product.** Durable can persist a Wait and resume its exact Run, but it does not
  provide assignee, candidate group, claim/complete, form, escalation, delegation, or approval-history products. Use
  Activiti, Camunda, Flowable, or a dedicated external task system for those capabilities.
- **You need BPMN semantics outside CompileFlow's subset or vendor-neutral execution.** CompileFlow supports start/end,
  script/service/receive tasks, exclusive/parallel/inclusive gateways, subprocesses, call activities, and supported
  loops. Human tasks, transactions, choreographies, and event-based gateways fail `preflight`; `cf:` action extensions
  are CompileFlow-specific.
- **Your definitions change as often as they execute.** The compile-then-execute model amortizes compilation across many
  executions. If each execution uses a unique definition (e.g., ad-hoc user-defined rules), the compilation cost
  dominates.
- **You need DMN decision tables.** CompileFlow does not ship a DMN engine. Use a dedicated DMN library or Camunda.
- **Your team is not on the JVM.** CompileFlow requires Java 17+ at runtime and the `jdk.compiler` module in the runtime
  image.

### 3. Mixed scenarios

- **CompileFlow + Activiti/Camunda**: CompileFlow orchestrates hot in-process logic and calls a Spring bean that starts
  a long-running Activiti/Camunda process for the human-task portion.
- **CompileFlow ProcessEngine + an external durable workflow/task system**: the external system owns task identity,
  persistence, correlation, and variables. It may call `ProcessEngine.trigger(...)` with an explicit `ProcessTrigger`
  to start a new execution at a trigger-entry node ID. This ProcessEngine path does not resume a stored CompileFlow Run.
- **CompileFlow Durable + an external task UI/service**: Durable owns the Run and Wait occurrence; the external system
  owns assignment and authorization, preserves and protects the one-time Wait token, and calls
  `DurableProcessEngine.completeWait(...)` for that exact Wait. A controlled RPC/MQ can carry the token opaquely; an
  integration can protect an `externalJobId -> WaitToken` mapping when only an external job ID returns. Mature
  order/payment domains should keep using domain identity and domain state instead of copying that identity into the
  Kernel. Never place a raw Wait token in logs, metric labels, browser-visible URLs, or third-party metadata.
- **CompileFlow + a decision-table library**: use a separate DMN boundary without turning CompileFlow into a
  decision-table engine.
- **CompileFlow + a saga framework**: CompileFlow handles local orchestration; the saga framework handles distributed
  compensation.

### 4. Decision checklist

| Question                                                                     | Yes → consider                        | No → consider                                   |
|------------------------------------------------------------------------------|---------------------------------------|-------------------------------------------------|
| Is the call rate much higher than the definition change rate?                | CompileFlow                           | Activiti/Camunda                                |
| Must supported execution resume between process steps after a restart?       | CompileFlow Durable                   | `ProcessEngine`                        |
| Is retrying a whole execution request sufficient?                            | CompileFlow Workbench persisted async | CompileFlow Durable or another resumable engine |
| Do you need human tasks?                                                     | Activiti/Camunda                      | CompileFlow                                     |
| Do you need DMN?                                                             | Camunda                               | CompileFlow                                     |
| Do you need BPMN semantics outside the CompileFlow subset?                   | A broader BPM platform                | CompileFlow                                     |
| Do you need version routing + canary for flows?                              | CompileFlow                           | Activiti/Camunda (with custom work)             |
| Is Java 17+ acceptable at runtime?                                           | CompileFlow                           | other engines                                   |

---

## 中文：何时使用 CompileFlow

CompileFlow 把受支持的流程定义编译为 Java 字节码，并提供语义明确的多个执行面。普通
`ProcessEngine` 提供 ProcessEngine 执行，不拥有跨崩溃 continuation 恢复；Workbench Server 可以持久化并重试整次请求；可选的 PostgreSQL
`DurableProcessEngine` 则在受支持的 Wait、Timer、受治理 Effect 与终态边界持久化一个 Run。部署系统另行持有不可变版本、路由、rollout
历史与交付状态。本指南帮助你选择正确执行面，而不是假设一个开关会改变所有调用的语义。

### 1. 适用场景

- **高调用频的热业务逻辑**。定价、库存校验、营销规则评估、订单校验、风控信号——定义变化远少于执行的负载。
- **业务与工程团队需要共同审查一份流程模型**。Workbench 编辑的就是 Java Engine 校验和执行的受支持定义。
- **需要流程的版本路由与灰度发布**。不可变版本、带 revision 前置条件的 alias route、按调用方 routing key 确定性分配
  cohort，以及可审计 rollout 历史，共同支持受控提升、中止与回滚。
- **需要类型化应用边界**。`ProcessEngine.execute(ref, request, MyResponse.class, options)` 通过配置的 `ProcessDataMapper`
  映射并返回 `ProcessResult<MyResponse>`。
- **定义适合 TBBPM 表达**。`while` 循环、`break`/`continue` 和一等公民 action（Java、Spring bean、QL）满足表达需求；部署身份由
  `ProcessRef` 与部署元数据承载，不混入 XML。
- **需要 Workbench Operate 的跨重启异步调用**。`compileflow-workbench-server` 提供 DB 状态机（`queued`/
  `running`/`succeeded`/`dead_letter`）与 lease fencing；这是 Server API，不是嵌入式 Engine 或 deploy 模块承诺。
- **需要可恢复的受支持 TBBPM 或 BPMN Run**。CompileFlow Durable 通过 PostgreSQL 持久化结构化控制流、循环、
  Parallel/Inclusive、Wait、Timer、受治理 Effect、同 Run exact-bound Process call、取消与审计恢复。不能证明
  branch-local 的并发 process call，以及不符合 portable state、脚本 provider 或 Effect 契约的模型，会在检查或 prepare 阶段失败。
- **基于 Spring Boot，需要应用级引擎**。`compileflow-spring-boot-starter` 自动配置可长期复用的引擎与有界可观测性。

### 2. 不适用场景

- **需要完整人工任务产品**。Durable 可以持久化 Wait 并恢复精确 Run，但不提供 assignee、candidate
  group、claim/complete、表单、升级、委派或审批历史产品。需要这些能力时请用 Activiti、Camunda、Flowable 或专用外部任务系统。
- **需要 CompileFlow 子集之外的 BPMN 语义或跨厂商直接执行**。CompileFlow 支持 start/end、script/service/receive
  task、exclusive/parallel/inclusive 网关、subprocess、call activity 与受支持 loop。Human
  task、transaction、choreography、event-based gateway 会在 `preflight` 失败；`cf:` action extension 只属于 CompileFlow。
- **定义变化频率与执行频率相当**。编译执行模型将编译成本摊销到多次执行。若每次执行都用唯一定义（如临时用户规则），编译成本成为瓶颈。
- **需要 DMN 决策表**。CompileFlow 不内置 DMN 引擎。请用专用 DMN 库或 Camunda。
- **团队不在 JVM 上**。CompileFlow 运行时需 Java 17+ 与 `jdk.compiler` 模块。

### 3. 混合场景

- **CompileFlow + Activiti/Camunda**：CompileFlow 编排进程内热逻辑，调用 Spring bean 启动长周期 Activiti/Camunda
  流程实例处理人工任务部分。
- **普通 CompileFlow + 外部持久工作流/任务系统**：外部系统拥有任务 identity、持久化、correlation 与 variables；它可以携带显式
  `ProcessTrigger` 调用 `ProcessEngine.trigger(...)`，从触发入口节点 ID 启动一次新执行。这个 ProcessEngine
  路径不会恢复 CompileFlow Run。
- **CompileFlow Durable + 外部任务 UI/服务**：Durable 持有 Run 与 Wait occurrence；外部系统持有分派与授权、保护一次性 Wait
  token，并保证它能跨越异步过程，针对该精确 Wait 调用 `DurableProcessEngine.completeWait(...)`。可控 RPC/MQ 可 opaque
  透传；外部只返回自身 job ID 时，Integration 可保护 `externalJobId -> WaitToken` 映射。成熟订单/支付领域应继续使用
  domain identity 与 domain state，不把这些身份复制进 Kernel。Raw token 不得进入日志、metric label、普通 URL 或第三方
  metadata。
- **CompileFlow + 决策表库**：把 DMN 保持为独立边界，不把 CompileFlow 扩展成决策表引擎。
- **CompileFlow + saga 框架**：CompileFlow 处理本地编排；saga 框架处理分布式补偿。

### 4. 决策清单

| 问题                                              | 是 → 考虑                            | 否 → 考虑                            |
|---------------------------------------------------|--------------------------------------|--------------------------------------|
| 调用频远高于定义变化频？                          | CompileFlow                          | Activiti/Camunda                     |
| 受支持执行是否必须在重启后从流程中间步骤恢复？     | CompileFlow Durable                  | `ProcessEngine`                 |
| 重试整次调用是否足够？                            | CompileFlow Workbench 持久化异步调用 | CompileFlow Durable 或其他可恢复引擎 |
| 需要人工任务？                                    | Activiti/Camunda                     | CompileFlow                          |
| 需要 DMN？                                        | Camunda                              | CompileFlow                          |
| 需要 CompileFlow 子集之外的 BPMN 语义？           | 更完整的 BPM 平台                    | CompileFlow                          |
| 需要流程版本路由 + 灰度？                         | CompileFlow                          | Activiti/Camunda（需自建）           |
| 运行时可接受 Java 17+？                           | CompileFlow                          | 其他引擎                             |

---

## 参考 / References

- [TBBPM 规范 / TBBPM Specification](specs/tbbpm-specification.en.md)
- [Durable Process / Durable Process 使用指南](en/durable-process.md)
- [TBBPM vs BPMN](specs/tbbpm-vs-bpmn.md)
- [支持面清单 / Supported Surfaces](architecture/06-SUPPORTED_SURFACES.en.md)
