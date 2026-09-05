const zhOperate = {
  // 错误信息

  'process.management': '流程管理',

  'process.managementDesc': '管理、编辑并发布流程定义。',

  'process.publish': '发布',

  'process.code': '流程编码',

  'process.name': '流程名称',

  'process.type': '流程类型',

  'process.updatedAt': '更新时间',

  'process.allTypes': '全部类型',

  'process.create': '创建流程',

  'process.import': '导入流程',

  'process.deleteSuccess': '已删除',

  'process.duplicateSuccess': '已复制',

  'process.createdBy': '创建人',

  'process.deleteError': '删除流程失败，请重试。',

  'process.duplicateError': '复制流程失败，请重试。',

  'process.createError': '创建流程失败，请重试。',

  'process.newProcessDefaultName': '未命名流程',

  'process.duplicateName': '{{name}}（副本）',

  'process.publishSuccess': '流程已发布',

  'process.publishError': '发布失败，请重试。',

  'process.importSuccess': '流程已导入',

  'process.importError': '导入失败，请检查 XML 后重试。',

  // Operate模块 - 监控

  'monitoring.partialLoadError': '部分监控数据刷新失败',

  'monitoring.partialLoadErrorDescription':
    '不可用数据源：{{sources}}。其他面板仍会继续刷新，异常面板保留最近一次成功值。',

  'monitoring.staleData': '刷新失败，当前显示最近一次成功值',

  'monitoring.source.metrics': '执行指标',

  'monitoring.source.trends': '执行趋势',

  'monitoring.source.topProcesses': '热门流程',

  'monitoring.source.errors': '最近错误',

  'monitoring.source.versionDistribution': '版本分布',

  'monitoring.source.deployRuntime': '部署运行时',

  'monitoring.source.routingOutboxControl': '部署投递队列',

  'monitoring.source.asyncHealth': '异步调用队列',

  // Operate模块 - 部署管理

  'deployment.processName': '流程名称',

  'deployment.version': '版本',

  'deployment.alias': '别名',

  'deployment.alias.dev': '开发别名 (DEV)',

  'deployment.alias.staging': '预发别名 (STAGING)',

  'deployment.alias.production': '生产别名 (PRODUCTION)',

  'deployment.loadDetailError': '加载部署详情失败。',

  'deployment.operation': '操作类型',

  'deployment.operation.deploy': '部署',

  'deployment.operation.rollback': '回滚',

  'deployment.strategy': '策略',

  'deployment.strategy.all_at_once': '全量切换',

  'deployment.strategy.canary': '灰度发布',

  'deployment.status': '状态',

  'deployment.detail': '部署详情',

  'deployment.baselineVersion': '已捕获基线',

  'deployment.baseRouteRevision': '起始路由修订号',

  'deployment.routeRevision': '已提交路由修订号',

  'deployment.deployedAt': '部署时间',

  'deployment.newDeployment': '新建部署',

  'deployment.deploySuccess': '部署成功',

  'deployment.rollback': '回滚',

  'deployment.rollbackSuccess': '已回滚',

  'deployment.rollbackConfirm': '确认回滚',

  'deployment.rollbackWarning': '将恢复到先前版本，是否继续？',

  'deployment.routeChanged': '当前路由已被其他部署更新，部署列表已刷新。',

  'deployment.routeSuperseded': '当前路由已由更新的部署接管，变更操作已禁用。',

  'deployment.abortMissingBaseline': '无法中止灰度：缺少已捕获的基线版本。',

  'deployment.rollbackMissingBaseline': '无法回滚：缺少已捕获的基线版本。',

  'deployment.routeControl': '路由控制',

  'deployment.rollbackHint': '回滚会创建一条新的审计部署记录，并恢复本次部署捕获的基线版本。',

  'deployment.partialRefresh': '操作已成功，但路由或事件信息暂时无法刷新。',

  'deployment.abort': '中止灰度',

  'deployment.abortSuccess': '灰度已中止',

  'deployment.abortConfirm': '中止灰度',

  'deployment.abortWarning': '将中止灰度并把全部流量切回稳定版本，是否继续？',

  'deployment.processCode': '流程代码',

  'deployment.selectProcessPlaceholder': '选择要部署的流程',

  'deployment.canaryWeightBps': '灰度流量权重',

  'deployment.notes': '备注',

  'deployment.notesPlaceholder': '填写部署备注…',

  'deployment.status.in_progress': '进行中',

  'deployment.status.completed': '已完成',

  'deployment.status.aborted': '已中止',

  'deployment.history': '部署历史',

  'deployment.noEvents': '暂无部署事件',

  'deployment.event.CANARY_STARTED': '开始灰度',

  'deployment.event.COMPLETED': '部署完成',

  'deployment.event.CANARY_WEIGHT_UPDATED': '更新灰度比例',

  'deployment.event.PROMOTED': '灰度转全量',

  'deployment.event.ABORTED': '灰度已中止',

  'deployment.event.enteredPhase': '进入{{phase}}阶段',

  'deployment.event.phaseTransition': '{{from}} -> {{to}}',

  'deployment.event.actor': '操作人：{{actor}}',

  'deployment.phase.in_progress': '进行中',

  'deployment.phase.completed': '已完成',

  'deployment.phase.aborted': '已中止',

  'deployment.canaryControl': '灰度发布',

  'deployment.canaryControlHint': '评估真实流量指标、调整灰度比例、提升候选版本，或恢复稳定版本。',

  'deployment.updateCanary': '更新权重',

  'deployment.canaryUpdated': '灰度权重已更新',

  'deployment.canaryUpdateError': '更新灰度权重失败。',

  'deployment.evaluateHealth': '评估健康度',

  'deployment.healthEvaluationError': '灰度健康评估失败。',

  'deployment.health.healthy': '灰度版本健康',

  'deployment.health.unhealthy': '灰度版本不健康',

  'deployment.health.insufficient_data': '灰度样本不足',

  'deployment.health.candidateSamples': '候选版本样本数',

  'deployment.health.baselineSamples': '基线版本样本数',

  'deployment.health.candidateErrorRate': '候选错误率',

  'deployment.health.candidateP95': '候选版本 p95',

  'deployment.promoteCanary': '全量放量',

  'deployment.canaryPromoted': '候选版本已提升至全量流量',

  'deployment.canaryPromoteError': '灰度提升失败。',

  'deployment.promoteConfirm': '确认放量',

  'deployment.promoteWarning.healthy': '最近一次健康评估已通过，是否将全部流量切换到候选版本？',

  'deployment.promoteWarning.unhealthy':
    '最近一次健康评估未通过，继续操作仍会将全部流量切换到候选版本。',

  'deployment.promoteWarning.insufficient_data': '当前样本不足，是否仍要提升候选版本？',

  'deployment.promoteWarning.notEvaluated': '当前灰度修订尚未执行健康评估，是否仍要提升？',

  // Operate模块 - 部署向导

  'deployment.wizard.title': '部署向导',

  'deployment.wizard.subtitle': '分三步将别名路由到已发布的流程版本。',

  'deployment.wizard.selectProcess': '选择流程',

  'deployment.wizard.selectAlias': '选择别名',

  'deployment.wizard.confirm': '确认部署',

  'deployment.wizard.step1.desc': '选择要部署的流程',

  'deployment.wizard.step2.desc': '配置路由别名',

  'deployment.wizard.step3.desc': '核对信息并部署',

  'deployment.wizard.selectProcessLabel': '选择流程',

  'deployment.wizard.selectProcessRequired': '请选择要部署的流程',

  'deployment.wizard.versionRequired': '请选择已发布版本',

  'deployment.wizard.versionPlaceholder': '选择已发布版本',

  'deployment.wizard.noPublishedVersions': '暂无已发布版本',

  'deployment.wizard.versionLoadFailed': '加载已发布版本失败',

  'deployment.wizard.selectAliasRequired': '请输入路由别名',

  'deployment.wizard.selectAliasPlaceholder': '别名',

  'deployment.wizard.aliasInvalid':
    '长度为 1–64，只能使用字母、数字、点、下划线或连字符，且必须以字母或数字开头。',

  'deployment.wizard.step1.hint': '部署前请确认流程已充分验证。',

  'deployment.wizard.step2.warningTitle': '变更线上别名前',

  'deployment.wizard.step2.warningDesc': '请确认所选版本已可承接该别名的流量。',

  'deployment.wizard.confirmTitle': '确认部署',

  'deployment.wizard.processType': '流程类型',

  'deployment.wizard.readyTitle': '可以开始部署',

  'deployment.wizard.readyDesc': '请核对以上信息，然后开始部署。',

  'deployment.wizard.deployFailed': '部署失败，请重试',

  'deployment.wizard.deploySuccess': '部署成功',

  'deployment.wizard.deploySuccessDesc': '流程「{{process}}」已通过 {{alias}} 提供服务',

  'deployment.wizard.viewDeployment': '查看部署',

  'deployment.wizard.continueDeploy': '继续部署',

  'deployment.wizard.deploying': '部署中…',

  // Operate模块 - 监控大盘

  'monitoring.title': '监控',

  'monitoring.subtitle': '当前 Workbench Server 观测到的执行指标与运行状态。',

  'monitoring.totalExecutions': '总执行次数',

  'monitoring.successExecutions': '成功次数',

  'monitoring.failedExecutions': '失败次数',

  'monitoring.successRate': '成功率',

  'monitoring.executionTrends': '执行趋势',

  'monitoring.topProcesses': '热门流程',

  'monitoring.recentErrors': '最近错误',

  'monitoring.noData': '暂无数据',

  'monitoring.noErrors': '暂无错误',

  'monitoring.success': '成功',

  'monitoring.failed': '失败',

  'monitoring.opsControlPlane': '运维控制面',

  'monitoring.opsActionFailed': '操作失败',

  'monitoring.opsRefreshFailed': '操作成功，但状态刷新失败',

  'monitoring.deploymentOutbox': '部署投递队列',

  'monitoring.deploymentRunning': '路由投递中',

  'monitoring.deploymentStopped': '路由投递已停止',

  'monitoring.outboxPending': '待处理',

  'monitoring.outboxProcessing': '处理中',

  'monitoring.outboxExpiredClaims': '租约已过期',

  'monitoring.outboxDeadLetters': '死信',

  'monitoring.dispatcherRunning': '调度器',

  'monitoring.deploymentDeadLettersRequeued': '部署死信已重新入队',

  'monitoring.requeueDeploymentDeadLetters': '重新入队部署死信',

  'monitoring.asyncInvocationQueue': '异步调用队列',

  'monitoring.asyncInvocationQueueTitle': '持久化调用重试管道',

  'monitoring.asyncReady': '待执行',

  'monitoring.asyncOldestReady': '最长待执行',

  'monitoring.asyncDelayed': '延迟中',

  'monitoring.asyncRunning': '执行中',

  'monitoring.asyncExpired': '租约已过期',

  'monitoring.asyncDeadLetters': '死信',

  'monitoring.asyncWorker': '工作节点',

  'monitoring.asyncLease': '租约',

  'monitoring.asyncBatch': '调度批量',

  'monitoring.asyncDeadLettersRequeued': '异步调用死信已重新入队',

  'monitoring.requeueAsyncInvocationDeadLetters': '重新入队异步调用死信',

  'monitoring.invocationLedgerEyebrow': '持久化运行任务',

  'monitoring.invocationLedger': '异步调用台账',

  'monitoring.invocationLedgerDescription':
    '查看逻辑调用、物理尝试、重试决策以及实际进入引擎后产生的链路。',

  'monitoring.invocationLedgerStale': '调用记录刷新失败，当前显示最近一次成功结果',

  'monitoring.invocationProcessFilter': '流程编码',

  'monitoring.invocationStatusFilter': '调用状态',

  'monitoring.invocationProcess': '流程',

  'monitoring.invocationId': '调用 ID',

  'monitoring.invocationStatus': '状态',

  'monitoring.invocationStatus.queued': '排队中',

  'monitoring.invocationStatus.running': '执行中',

  'monitoring.invocationStatus.succeeded': '成功',

  'monitoring.invocationStatus.dead_letter': '死信',

  'monitoring.invocationAttempts': '尝试',

  'monitoring.invocationAttemptSummary':
    '累计 {{total}} 次，本轮 {{current}}/{{maximum}} 次，人工重驱 {{redrives}} 次',

  'monitoring.invocationVersion': '实际版本',

  'monitoring.invocationUpdatedAt': '更新时间',

  'monitoring.invocationDetail': '异步调用详情',

  'monitoring.invocationDetailFailed': '无法加载调用详情',

  'monitoring.invocationAttemptHistory': '尝试历史',

  'monitoring.invocationAttemptGeneration': '第 {{redrive}} 次重驱，第 {{attempt}} 次尝试',

  'monitoring.invocationWorker': '工作节点',

  'monitoring.invocationStartedAt': '开始',

  'monitoring.invocationFinishedAt': '结束',

  'monitoring.invocationNextAttemptAt': '下次尝试',

  'monitoring.invocationDuration': '耗时',

  'monitoring.invocationAttemptFailed': '尝试失败',

  'monitoring.invocationOutcome.running': '执行中',

  'monitoring.invocationOutcome.succeeded': '成功',

  'monitoring.invocationOutcome.failed': '失败',

  'monitoring.invocationOutcome.lease_expired': '租约过期',

  'monitoring.invocationDisposition.succeeded': '已完成',

  'monitoring.invocationDisposition.retry_scheduled': '已安排重试',

  'monitoring.invocationDisposition.dead_lettered': '已转死信',

  'monitoring.noInvocations': '暂无异步调用',

  'monitoring.noInvocationAttempts': '暂无尝试记录',

  'monitoring.requeueInvocation': '重新入队',

  'monitoring.invocationRequeued': '异步调用已重新入队',

  'monitoring.invocationRequeueConfirm': '重新入队这条死信调用？',

  'monitoring.invocationRequeueWarning':
    '请先修复根因。重驱采用至少一次语义，可能重复产生外部副作用。',

  'monitoring.statusUnavailable': '状态不可用',

  'monitoring.unavailable': '不可用',

  'monitoring.deployRuntime': '部署运行时数据面',

  'monitoring.deployRuntimeLocalNode': '当前节点诊断',

  'monitoring.deployRuntimeAvailable': '运行时热部署可用',

  'monitoring.deployRuntimeUnavailable': '运行时热部署不可用',

  'monitoring.runtimeStarted': '已启动',

  'monitoring.runtimeInflight': '安装中',

  'monitoring.runtimeDesiredAliases': '目标别名',

  'monitoring.runtimeLocalReadyAliases': '本地就绪别名',

  'monitoring.runtimePendingAliases': '收敛中别名',

  'monitoring.runtimeFailedAliases': '收敛失败别名',

  'monitoring.runtimeDemanded': '目标版本',

  'monitoring.runtimeBackedOff': '重试退避中的版本',

  'monitoring.runtimeAliases': '别名收敛',

  'monitoring.runtimeAliasState.local_ready': '本地就绪',

  'monitoring.runtimeAliasState.pending': '收敛中',

  'monitoring.runtimeAliasState.failed': '失败',

  'monitoring.runtimeAliasRevisions': '目标修订 r{{desired}}，本地就绪修订 r{{localReady}}',

  'monitoring.runtimeDemandedList': '目标版本',

  'monitoring.runtimeBackedOffList': '重试退避',

  'monitoring.runtimeDeployedList': '本地已安装',

  'monitoring.versionDistribution': '版本分布',

  'monitoring.version': '版本',

  'monitoring.timeRange.1h': '近 1 小时',
  'monitoring.timeRangeLabel': '时间范围',

  'monitoring.timeRange.6h': '近 6 小时',

  'monitoring.timeRange.24h': '近 24 小时',

  'monitoring.timeRange.7d': '近 7 天',

  'monitoring.timeRange.30d': '近 30 天',

  // Operate模块 - 日志查询

  'logs.title': '日志',

  'logs.subtitle': '搜索并检查流程执行历史。',

  'logs.namespace': '命名空间',

  'logs.requestedVersion': '请求版本',

  'logs.effectiveVersion': '实际版本',

  'logs.routingSource': '路由来源',

  'logs.routeAlias': '路由别名',

  'logs.routeRevision': '路由修订号',

  'logs.endTime': '结束时间',

  'logs.modelType': '模型类型',

  'logs.sourceDigest': '源码摘要',

  'logs.dateRange': '执行时间范围',

  'logs.startDatePlaceholder': '开始日期',

  'logs.endDatePlaceholder': '结束日期',

  'logs.processCode': '流程编码',

  'logs.invocationId': '调用 ID',

  'logs.parentInvocationId': '父调用 ID',

  'logs.callDepth': '调用深度',

  'logs.traceId': 'Trace ID',

  'logs.status': '状态',

  'logs.duration': '耗时',

  'logs.startTime': '开始时间',

  'logs.searchPlaceholder': '搜索日志…',

  'logs.selectStatus': '选择状态',

  'logs.export': '导出',

  'logs.noData': '暂无日志',

  // 通用

  'filters.clear': '清空筛选',

  'filters.active': '当前筛选：',

  'filters.difficulty': '难度',

  'filters.category': '分类',

  'filters.search': '搜索',

  'filters.resultCount': '共找到 {{count}} 个示例',

  // 分类（示例列表筛选项）

  'sort.name': '按名称',

  'sort.difficulty': '按难度',

  'sort.duration': '按时长',

  // 流程类型筛选

  'logs.detail': '日志详情',

  'logs.errorCode': '错误码',

  'logs.errorMessage': '错误信息',

  'logs.exportSuccess': '导出成功',

  'logs.status.success': '成功',

  'logs.status.failed': '失败',

  // 错误扩展

  'process.searchPlaceholder': '搜索流程…',

  'process.deleteConfirm': '确定删除该流程吗？',

  // 部署扩展

  'deployment.management': '部署管理',

  'deployment.managementDesc': '跟踪并管理通过流程别名路由的已发布版本。',

  'deployment.canaryRange': '灰度比例必须在 1 到 9999 个基点之间。',
  'deployment.canaryInteger': '灰度比例必须是整数基点。',

  'deployment.canaryRequired': '请输入灰度流量比例。',

  'deployment.health.reason.withinThresholds': '灰度指标处于配置的健康阈值内。',

  'deployment.health.metricsScope': '指标范围',

  'deployment.health.metricsScope.workbench_server': '当前 Workbench Server',

  'deployment.health.metricsSource': '指标来源',

  'deployment.health.metricsSource.execution_logs': '执行日志',

  'deployment.deploy': '部署',

  // 详情页扩展（新增）

  'deployment.searchPlaceholder': '搜索流程编码 / 部署 ID',

  'deployment.filterStatus': '按状态筛选',

  'deployment.filterAlias': '按别名筛选',

  // 反馈

  'operate.title': '运维',

  'operate.subtitle': '发布不可变版本、安全灰度切流，并将每次执行追溯到精确源码。',

  'operate.deploy': '部署流程',

  'operate.monitor': '监控',

  'operate.explore': '运维入口',

  'monitoring.runtimeTopology': '运行时拓扑',

  'operate.processMgmt': '流程管理',

  'operate.processMgmtDesc': '查看并管理流程定义。',

  'operate.deployMgmt': '部署管理',

  'operate.deployMgmtDesc': '管理已发布版本与别名发布过程。',

  'operate.realtimeMonitor': '实时监控',

  'operate.realtimeMonitorDesc': '观察执行健康与性能指标。',

  'operate.logQuery': '日志',

  'operate.logQueryDesc': '检索并分析流程执行日志。',

  'operate.processesWithAliases': '已配置 Alias 的流程',

  'operate.unit.process': '个',

  'operate.unit.items': '次',

  'operate.unit.times': '次',

  'operate.processExecutions': '启动后执行次数',

  'operate.successRate': '成功率',

  'operate.failedExecutions': '失败次数',

  'operate.recentDeploys': '最近部署',

  'operate.noRecentDeployments': '暂无最近部署',

  'operate.recentDeploymentsUnavailable': '最近部署暂不可用',

  'operate.viewAll': '查看全部',

  'operate.deploying': '部署中',

  'operate.failed': '失败',

  'operate.pending': '等待中',

  // 监控 - 错误详情

  'monitoring.errorCount': '次数',

  'monitoring.lastOccurred': '最近',

  // 学习进度

  'process.editInDesigner': '在设计器中编辑',

  // 设计器
} as const

export default zhOperate
