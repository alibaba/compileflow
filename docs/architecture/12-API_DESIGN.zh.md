# API 设计原则

## 目的

CompileFlow 根据操作职责选择 API 形态。公开接口应尽量小而稳定：必需信息直接可见，无效请求难以构造，长期存在的概念只有一种规范表达。

## 按职责选择形态

| 职责 | 形态 | 示例 |
|---|---|---|
| 高频嵌入式应用调用 | `verb(required arguments, Options)` | `ProcessEngine.execute`、`DurableProcessEngine.start`、`completeWait` |
| 需要审计或 revision 检查的控制操作 | `verb(Command)` | Deploy rollout 命令；Durable pause、resume 与裁决命令 |
| 包含多个过滤或分页字段的读取 | `verb(Query)` | 流程实例、Timeline、Outbox、版本和 Rollout 查询 |

简单的应用调用不需要为了外观统一而额外包装成 Command。

## 原则

1. 根据操作授权的工作选择方法形态，不追求无关 API 之间的外观统一。
2. 如果使用不同类型或 overload 就能低成本阻止无效请求，应优先在类型层完成约束。例如，精确版本准入和 Alias
   准入不能先接受通用 `ProcessRef`，再到运行时拒绝。
3. 必需的领域信息直接作为方法参数；可选的单次调用行为放入 `Options`。不要把 Options 变成无关字段的集合。
4. `Command` 只用于需要审计、特权或 revision 检查的变更。execute、start、completeWait、cancel 等高频嵌入式调用，
   没有明确语义需要时不包装 Command。
5. 读取操作包含多个过滤条件、排序、边界或游标时，使用不可变 `Query`。
6. 规范身份类型应端到端传递。稳定 API 使用 `ProcessRef.Version` 和 `ProcessRef.Alias`，不要反复拆成字符串。
7. Capability 和 cursor 对应用保持 opaque。应用可以传递其标量值，但不能依赖存储 key、内部流程实例或元素身份、
   排序实现等细节。
8. 绝对时间使用 `Instant`；时间长度使用 `Duration`，或在字段名中明确单位。
9. 复杂公开视图需要保留演进空间。优先使用 Builder 或符合其职责的构造入口，避免很长的 positional constructor。
10. Java API、线协议和数据库 schema 是相互独立的契约。JSON string 或 SQL `bigint` 不能反向决定公开 Java 领域类型。
11. JVM `public` 可见性不代表产品支持。实现指标、repository record、adapter 和装配代码只有被支持面规范明确列出时，
    才属于 Supported surface。
12. 只有当新类型提供必要能力，或能阻止一类有意义的无效请求时，才加入公共 API。

## 稳定的身份与准入规则

- `ProcessDefinition` 提供显式的 `Inline` 与 `Classpath` source；`ProcessRef.Version` 与
  `ProcessRef.Alias` 是两种已发布引用。
- `ProcessExecution` 报告实际执行的流程身份与可选的精确已发布 `ProcessRef.Version`。
- 即时执行接受 `ProcessEngine` 路由所支持的引用形态。
- Durable 精确准入接受 `ProcessRef.Version`；已发布准入接受 `ProcessRef.Alias` 并只解析一次。两条路径最终都物化相同的
  精确 Process 语义。Version 可以保留为准入归因，但恢复只使用已存储的 Process ID，不再通过 Version 或 Alias 路由。
- Alias 是可变的 Deploy 控制面状态，不是 Durable 恢复身份。
- Deploy 领域制品位于 `deploy.api.artifact`；协议 parser、payload 和 key 位于 `deploy.api.protocol`。

## 评审清单

修改 Supported API 时，必须说明操作职责、无效状态处理、规范身份、时间类型、构造方式和兼容等级。Durable 仍处于
Developer Preview，但其核心术语在升级支持等级前也应接受同样的评审。

另见[支持范围](06-SUPPORTED_SURFACES.zh.md)、[版本路由](05-VERSION_ROUTING.zh.md)和
[Durable 架构](10-DURABLE_ARCHITECTURE.zh.md)。
