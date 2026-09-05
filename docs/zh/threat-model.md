# 威胁模型

CompileFlow 安全评估覆盖 Engine、Deploy、Durable 与 Workbench 支持面，明确项目已经缓解的威胁、由嵌入应用或
部署环境负责的控制，以及发布评审不得隐藏的剩余风险。支持级别与发布状态以
[支持面清单](../architecture/06-SUPPORTED_SURFACES.zh.md)为准；[安全指南](security.md)提供具体配置和操作说明。

## 1. 范围与信任边界

| 边界 | 可信输入 | 权限与输出 |
|------|----------|------------|
| Engine parser 与 compiler | 经过评审的 TBBPM/BPMN 定义和显式注册的扩展 | 生成 Java 使用宿主 JVM 的进程、classpath、文件系统、网络和身份权限执行 |
| Deploy control plane 与 runtime | 已认证的发布和路由命令 | 不可变 artifact、alias、rollout 状态与 runtime 选择 |
| Durable kernel 与 Store | 通过校验的严格 profile 流程定义、类型化值和应用提供的 capability | 带同一 Run invocation frame 的持久化 Run 状态、lease、Effect、Wait token digest 与 Outbox 记录；raw Wait token 只会在受保护的 Outbox 投递中短暂存在 |
| Workbench 浏览器、gateway 与 Server | 来自可信边缘的已认证运维请求 | 定义编辑、部署变更、执行、监控和数据库访问 |
| CI 与发布 | 已评审源码、固定依赖、受保护凭据和已授权 tag | JAR、源码包、SBOM、checksum 与签名 provenance |

流程定义、Java Code、Java action、script、Spring bean、plugin 以及 Store/provider 实现都是可信可执行输入；CompileFlow 不承诺
对它们提供 sandbox。认证、最终用户授权、租户策略、TLS 终止、数据库加固、备份和基础设施隔离仍在库边界之外。

## 2. 资产与参与者

安全敏感资产包括流程定义与不可变 artifact digest；部署 alias 与 rollout 状态；执行变量、Durable snapshot、Effect payload、
Wait token 与 Outbox 记录；数据库、API、签名和 CI 凭据；审计记录；以及携带 SBOM 和 provenance 身份的发布制品。

相关参与者包括：

- 拥有仓库或签名权限的 maintainer 与发布人员；
- 可信流程作者和部署运维人员；
- 已认证的应用服务与 Workbench gateway principal；
- 嵌入应用、扩展、Effect adapter、Store provider 和基础设施管理员；
- 未认证网络客户端、恶意或已失陷依赖，以及能够控制未评审定义、浏览器请求、数据库行、artifact channel 或 CI metadata 的攻击者。

## 3. 主要数据流

1. 已评审定义通过 classpath、应用 inline 输入、Deploy 或 Workbench 进入；有界解析与 preflight 在编译并进入宿主
   JVM 执行前生成精确 source digest。
2. 已认证部署命令保存不可变 artifact 身份并修改路由状态；runtime 将 alias 解析为一个精确 Version，并在加载前校验所选内容身份。
3. Durable 命令进入一个 Run-authority 事务；Store 原子提交状态、Effect、Wait occurrence、同一 Run 的 invocation frame 与
   Outbox intent，lease 和 fencing 拒绝过期 worker。
4. 浏览器只能通过可信边缘访问 Workbench Server；边缘删除客户端提供的内部 header，并加入服务端 credential。浏览器 bundle
   不包含 Server API key。
5. CI 使用固定工具和依赖 manifest 构建已评审 commit；tag 发布先以 draft 组装全部制品，将其 hash 绑定到 provenance，最后只发布
   完整集合。

## 4. 威胁分析

| ID | 威胁 | 项目控制 | 部署环境剩余责任 |
|----|------|----------|------------------|
| TM-01 | 不可信定义或扩展在宿主中执行任意代码 | 文档明确把定义视为可信代码；语义校验、严格类型、精确 component allowlist 和空 Java Code 编译 classpath 降低意外暴露 | 评审并授权定义；用独立 OS/container 边界隔离不可信作者代码；以最小权限运行宿主 |
| TM-02 | XXE、远程 schema、路径穿越或超大定义泄露文件或耗尽资源 | 安全 XML 处理禁用 DTD、外部 entity 与外部 schema；限制 source 大小；classpath 路径拒绝 scheme、父目录跳转与已知网络 ClassLoader URL | 约束自定义 ClassLoader 与 artifact resolver；施加网络和内存限制 |
| TM-03 | 客户端绕过 Workbench 认证或伪造内部身份 | 生产环境缺少强 API key 和 service principal 时启动失败；使用常量时间比较；仅 health endpoint 匿名；随附 gateway 删除客户端 credential 与 forwarding header | 终止 TLS、认证并授权用户、删除不可信 header、保护上游链路、轮换 credential，并在需要时提供逐用户审计身份 |
| TM-04 | principal 读取或修改其他租户的定义或执行 | namespace 与精确 process identity 避免意外 key 冲突；变更 API 记录已认证 service actor | namespace 不是授权；在每个控制、执行和查询请求前强制租户策略，必要时使用数据库隔离 |
| TM-05 | artifact 被替换、接受过期路由或 rollback 指向错误代码 | 不可变 Version 携带内容身份；发布和路由使用显式状态迁移、乐观检查与精确 target；发布制品具有 hash 与 provenance | 限制发布权限、保护 artifact 传输与存储、评审 rollout 并监控 reconcile 失败 |
| TM-06 | retry、crash 或 lease 过期导致不可逆副作用重复或完成丢失 | Durable 使用稳定的 Run、occurrence 和 event identity，以及事务状态迁移、lease、fencing、显式 UNKNOWN Effect 与幂等 Outbox 契约 | Effect provider 必须去重并 reconcile UNKNOWN，认证 Outbox consumer，并保存外部事务证据 |
| TM-07 | 变量、payload、token、credential 或源码经响应、日志、浏览器存储或构建输出泄露 | 错误契约避免原始值；文档禁止敏感日志；Workbench 不接受浏览器 credential 配置；source control 检查拒绝生成物、本地制品与 credential | 加密传输和存储、限制日志与备份、执行保留/脱敏策略，并使用 secret manager 而不是源码或浏览器状态 |
| TM-08 | 高成本解析、编译、执行、队列或 API 调用造成拒绝服务 | definition size、compiler work、worker pool、queue、deadline、lease batch 和 shutdown 都有界且可观测 | 在托管边缘配置 request limit、quota、admission control、CPU/内存限制、容量规划与告警 |
| TM-09 | 数据库失陷、schema drift 或数据丢失破坏控制/Durable 权威 | transaction、版本化 migration、schema validation、乐观并发、lease fencing 与 crash/restart 测试保护应用不变量 | 使用最小权限数据库角色、加密连接与存储、限制管理权限、测试 backup/PITR 并监控数据库 |
| TM-10 | dependency、CI action、基础镜像、wrapper 或发布制品被替换 | lockfile 与受管 manifest、带 checksum 的 Maven Wrapper、commit 固定 Action、digest 固定镜像、Dependency Review、CodeQL、SpotBugs、SBOM、checksum 与 SLSA provenance 保护仓库拥有的路径 | 以 MFA 和评审保护仓库设置与 CI secret；验证发布 provenance；评估 registry、runner 与构建服务信任 |
| TM-11 | 浏览器内容注入或失陷边缘获得 Server 权限 | 随附 nginx profile 设置严格 CSP 与安全 header；React 不接收 Server credential；local gateway 清除敏感客户端 header | 评审自定义 HTML/rendering 和 gateway 策略、限制浏览器出站连接，不把 CORS 或 CSP 当作认证 |

## 5. 风险接受与复核

影响 Supported 支持面的未解决威胁默认阻断发布，除非 maintainer 在私有 advisory 或可评审 issue 中记录明确 owner、影响分析、
mitigation 与到期时间。被判断为不可利用的 dependency finding 必须形成与受影响 SBOM 绑定的 CycloneDX VEX 文档，不能静默
suppress。

每次重大安全边界变更以及每个稳定 minor/major 发布前都要复核本模型。解析、可执行 action、认证、路由权威、Durable transaction、
持久化、CI credential 或发布身份发生变化时，必须同步更新本模型、安全指南及相应边界的自动化测试。
