const zhOperate = {
  // 流程管理

  'process.management': '流程管理',

  'process.managementDesc': '创建、编辑和发布流程定义。',

  'process.publish': '发布',

  'process.code': '流程编码',

  'process.name': '流程名称',

  'process.type': '流程类型',

  'process.updatedAt': '更新时间',

  'process.allTypes': '全部类型',

  'process.create': '创建流程',

  'process.import': '导入流程',

  'process.deleteSuccess': '流程已删除',

  'process.duplicateSuccess': '流程已复制',

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

  // 监控

  'monitoring.partialLoadError': '部分监控数据刷新失败',

  'monitoring.partialLoadErrorDescription':
    '不可用的数据源：{{sources}}。其他面板会继续刷新，受影响的面板显示最近可用数据。',

  'monitoring.staleData': '无法刷新，当前显示最近可用数据',

  'monitoring.source.metrics': '执行指标',

  'monitoring.source.trends': '执行趋势',

  'monitoring.source.topProcesses': '流程排行',

  'monitoring.source.errors': '最近错误',

  'monitoring.source.versionDistribution': '版本分布',

  'monitoring.source.deployRuntime': '部署运行时',

  'monitoring.source.routingOutboxControl': '部署任务队列',

  'monitoring.source.asyncHealth': '异步调用队列',

  // 部署管理

  'deployment.processName': '流程',

  'deployment.version': '版本',

  'deployment.alias': '别名',

  'deployment.alias.dev': '开发（DEV）',

  'deployment.alias.staging': '预发（STAGING）',

  'deployment.alias.production': '生产（PRODUCTION）',

  'deployment.loadDetailError': '加载部署详情失败。',

  'deployment.operation': '操作类型',

  'deployment.operation.deploy': '部署',

  'deployment.operation.rollback': '回滚',

  'deployment.strategy': '策略',

  'deployment.strategy.all_at_once': '全量切换',

  'deployment.strategy.canary': '灰度发布',

  'deployment.status': '状态',

  'deployment.detail': '部署详情',

  'deployment.baselineVersion': '基线版本',

  'deployment.baseRouteRevision': '起始路由修订号',

  'deployment.routeRevision': '应用后路由修订号',

  'deployment.deployedAt': '部署时间',

  'deployment.newDeployment': '新建部署',

  'deployment.deploySuccess': '部署成功',

  'deployment.rollback': '回滚',

  'deployment.rollbackSuccess': '已回滚',

  'deployment.rollbackConfirm': '确认回滚',

  'deployment.rollbackWarning': '是否恢复到本次部署的基线版本？',

  'deployment.routeChanged': '当前路由已变更，部署数据已刷新。',

  'deployment.routeSuperseded': '更新的部署已变更当前路由，不能再修改本次部署。',

  'deployment.abortMissingBaseline': '无法中止灰度：基线版本不可用。',

  'deployment.rollbackMissingBaseline': '无法回滚：基线版本不可用。',

  'deployment.routeControl': '路由控制',

  'deployment.rollbackHint': '回滚会新增一条部署记录，并恢复基线版本。',

  'deployment.partialRefresh': '操作已完成，但路由或事件数据刷新失败。',

  'deployment.abort': '中止灰度',

  'deployment.abortSuccess': '灰度已中止',

  'deployment.abortConfirm': '中止灰度',

  'deployment.abortWarning': '是否中止灰度，并将全部流量切回稳定版本？',

  'deployment.processCode': '流程编码',

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

  'deployment.event.CANARY_WEIGHT_UPDATED': '灰度权重已更新',

  'deployment.event.PROMOTED': '已全量发布',

  'deployment.event.ABORTED': '灰度已中止',

  'deployment.event.enteredPhase': '状态变更为 {{phase}}',

  'deployment.event.phaseTransition': '{{from}} → {{to}}',

  'deployment.event.actor': '变更人：{{actor}}',

  'deployment.phase.in_progress': '进行中',

  'deployment.phase.completed': '已完成',

  'deployment.phase.aborted': '已中止',

  'deployment.canaryControl': '灰度发布',

  'deployment.canaryControlHint':
    '查看流量指标，调整灰度权重，将候选版本全量发布，或恢复稳定版本。',

  'deployment.updateCanary': '调整权重',

  'deployment.canaryUpdated': '灰度权重已更新',

  'deployment.canaryUpdateError': '更新灰度权重失败。',

  'deployment.evaluateHealth': '健康评估',

  'deployment.healthEvaluationError': '灰度健康评估失败。',

  'deployment.health.healthy': '灰度版本健康',

  'deployment.health.unhealthy': '灰度版本不健康',

  'deployment.health.insufficient_data': '灰度样本不足',

  'deployment.health.candidateSamples': '候选版本样本数',

  'deployment.health.baselineSamples': '基线版本样本数',

  'deployment.health.candidateErrorRate': '候选错误率',

  'deployment.health.candidateP95': '候选版本 P95 延迟',

  'deployment.promoteCanary': '全量发布',

  'deployment.canaryPromoted': '候选版本已承接全部流量',

  'deployment.canaryPromoteError': '候选版本全量发布失败。',

  'deployment.promoteConfirm': '确认全量发布',

  'deployment.promoteWarning.healthy': '最近一次健康评估已通过，是否将全部流量切换到候选版本？',

  'deployment.promoteWarning.unhealthy': '最近一次健康评估未通过，是否仍将全部流量切换到候选版本？',

  'deployment.promoteWarning.insufficient_data': '当前样本不足，是否仍要全量发布候选版本？',

  'deployment.promoteWarning.notEvaluated': '当前候选版本尚未执行健康评估，是否仍要全量发布？',

  // 部署向导

  'deployment.wizard.title': '部署',

  'deployment.wizard.subtitle': '通过三个步骤，将别名指向已发布的流程版本。',

  'deployment.wizard.selectProcess': '选择流程',

  'deployment.wizard.selectAlias': '选择别名',

  'deployment.wizard.confirm': '核对信息',

  'deployment.wizard.step1.desc': '选择要部署的流程',

  'deployment.wizard.step2.desc': '选择路由别名',

  'deployment.wizard.step3.desc': '核对信息并部署',

  'deployment.wizard.selectProcessLabel': '流程',

  'deployment.wizard.selectProcessRequired': '请选择要部署的流程',

  'deployment.wizard.versionRequired': '请选择已发布版本',

  'deployment.wizard.versionPlaceholder': '选择已发布版本',

  'deployment.wizard.noPublishedVersions': '暂无已发布版本',

  'deployment.wizard.versionLoadFailed': '加载已发布版本失败',

  'deployment.wizard.selectAliasRequired': '请输入路由别名',

  'deployment.wizard.selectAliasPlaceholder': '别名',

  'deployment.wizard.aliasInvalid':
    '长度为 1–64，只能使用字母、数字、点、下划线或连字符，且必须以字母或数字开头。',

  'deployment.wizard.step1.hint': '部署前请确认流程已完成测试。',

  'deployment.wizard.step2.warningTitle': '变更正在使用的别名前',

  'deployment.wizard.step2.warningDesc': '请确认所选版本已可承接该别名的流量。',

  'deployment.wizard.confirmTitle': '确认部署',

  'deployment.wizard.processType': '流程类型',

  'deployment.wizard.readyTitle': '准备部署',

  'deployment.wizard.readyDesc': '请核对以上信息，然后开始部署。',

  'deployment.wizard.deployFailed': '部署失败，请重试。',

  'deployment.wizard.deploySuccess': '部署成功',

  'deployment.wizard.deploySuccessDesc': '流程「{{process}}」已部署到 {{alias}}',

  'deployment.wizard.viewDeployment': '查看部署',

  'deployment.wizard.continueDeploy': '继续部署',

  'deployment.wizard.deploying': '部署中…',

  // 监控

  'monitoring.title': '监控',

  'monitoring.subtitle': '当前 Workbench 服务的执行指标和运行状态。',

  'monitoring.totalExecutions': '总执行次数',

  'monitoring.successExecutions': '成功次数',

  'monitoring.failedExecutions': '失败次数',

  'monitoring.successRate': '成功率',

  'monitoring.executionTrends': '执行趋势',

  'monitoring.topProcesses': '流程排行',

  'monitoring.recentErrors': '最近错误',

  'monitoring.noData': '暂无数据',

  'monitoring.noErrors': '暂无错误',

  'monitoring.success': '成功',

  'monitoring.failed': '失败',

  'monitoring.opsControlPlane': '运维状态',

  'monitoring.opsActionFailed': '操作失败',

  'monitoring.opsRefreshFailed': '操作已完成，但状态刷新失败',

  'monitoring.deploymentOutbox': '部署任务队列',

  'monitoring.deploymentRunning': '部署任务投递中',

  'monitoring.deploymentStopped': '部署任务投递已停止',

  'monitoring.outboxPending': '待处理',

  'monitoring.outboxProcessing': '处理中',

  'monitoring.outboxExpiredClaims': '租约已过期',

  'monitoring.outboxDeadLetters': '死信',

  'monitoring.dispatcherRunning': '投递服务',

  'monitoring.deploymentDeadLettersRequeued': '部署死信任务已重新入队',

  'monitoring.requeueDeploymentDeadLetters': '重新入队部署死信任务',

  'monitoring.asyncInvocationQueue': '异步调用队列',

  'monitoring.asyncInvocationQueueTitle': '异步调用的持久化重试队列',

  'monitoring.asyncReady': '待执行',

  'monitoring.asyncOldestReady': '最长等待时间',

  'monitoring.asyncDelayed': '延迟中',

  'monitoring.asyncRunning': '执行中',

  'monitoring.asyncExpired': '租约已过期',

  'monitoring.asyncDeadLetters': '死信',

  'monitoring.asyncWorker': '工作节点',

  'monitoring.asyncLease': '租约',

  'monitoring.asyncConcurrency': '并发上限',

  'monitoring.asyncDeadLettersRequeued': '异步调用死信已重新入队',

  'monitoring.requeueAsyncInvocationDeadLetters': '重新入队异步调用死信',

  'monitoring.invocationLedgerEyebrow': '持久化异步执行',

  'monitoring.invocationLedger': '异步调用记录',

  'monitoring.invocationLedgerDescription': '查看每次调用及其执行尝试、重试决策和引擎调用链路。',

  'monitoring.invocationLedgerStale': '调用记录刷新失败，当前显示最近可用数据',

  'monitoring.invocationProcessFilter': '流程编码',

  'monitoring.invocationStatusFilter': '调用状态',

  'monitoring.invocationProcess': '流程',

  'monitoring.invocationId': '调用 ID',

  'monitoring.invocationTraceId': '追踪 ID',

  'monitoring.invocationStatus': '状态',

  'monitoring.invocationStatus.queued': '排队中',

  'monitoring.invocationStatus.running': '执行中',

  'monitoring.invocationStatus.succeeded': '成功',

  'monitoring.invocationStatus.dead_letter': '死信',

  'monitoring.invocationAttempts': '执行尝试',

  'monitoring.invocationAttemptSummary':
    '累计 {{total}} 次 · 本轮 {{current}}/{{maximum}} 次 · 重新入队 {{redrives}} 次',

  'monitoring.invocationVersion': '实际版本',

  'monitoring.invocationUpdatedAt': '更新时间',

  'monitoring.invocationDetail': '异步调用详情',

  'monitoring.invocationDetailFailed': '加载调用详情失败',

  'monitoring.invocationAttemptHistory': '执行历史',

  'monitoring.invocationAttemptGeneration': '重新入队：{{redrive}} 次 · 执行尝试：{{attempt}} 次',

  'monitoring.invocationWorker': '工作节点',

  'monitoring.invocationStartedAt': '开始',

  'monitoring.invocationFinishedAt': '结束',

  'monitoring.invocationNextAttemptAt': '下次尝试',

  'monitoring.invocationDuration': '耗时',

  'monitoring.invocationAttemptFailed': '执行尝试失败',

  'monitoring.invocationOutcome.running': '执行中',

  'monitoring.invocationOutcome.succeeded': '成功',

  'monitoring.invocationOutcome.failed': '失败',

  'monitoring.invocationOutcome.lease_expired': '租约过期',

  'monitoring.invocationDisposition.succeeded': '已完成',

  'monitoring.invocationDisposition.retry_scheduled': '已安排重试',

  'monitoring.invocationDisposition.dead_lettered': '已移入死信队列',

  'monitoring.noInvocations': '暂无异步调用',

  'monitoring.noInvocationAttempts': '暂无执行尝试',

  'monitoring.requeueInvocation': '重新入队',

  'monitoring.invocationRequeued': '调用已重新入队',

  'monitoring.invocationRequeueConfirm': '是否重新入队这条死信调用？',

  'monitoring.invocationRequeueWarning':
    '请先解决导致失败的问题。重新入队采用至少一次投递，可能导致外部操作重复执行。',

  'monitoring.statusUnavailable': '状态不可用',

  'monitoring.unavailable': '不可用',

  'monitoring.deployRuntime': '部署运行时',

  'monitoring.deployRuntimeLocalNode': '当前节点诊断',

  'monitoring.deployRuntimeAvailable': '运行时热部署可用',

  'monitoring.deployRuntimeUnavailable': '运行时热部署不可用',

  'monitoring.runtimeStarted': '已启动',

  'monitoring.runtimeInflight': '并发安装',

  'monitoring.runtimeDesiredAliases': '目标别名',

  'monitoring.runtimeLocalReadyAliases': '本地就绪的别名',

  'monitoring.runtimePendingAliases': '同步中的别名',

  'monitoring.runtimeFailedAliases': '同步失败的别名',

  'monitoring.runtimeDemanded': '所需版本',

  'monitoring.runtimeBackedOff': '等待重试的版本',

  'monitoring.runtimeAliases': '别名同步状态',

  'monitoring.runtimeAliasState.local_ready': '本地就绪',

  'monitoring.runtimeAliasState.pending': '同步中',

  'monitoring.runtimeAliasState.failed': '失败',

  'monitoring.runtimeAliasRevisions': '目标 r{{desired}}，本地就绪 r{{localReady}}',

  'monitoring.runtimeDemandedList': '所需版本',

  'monitoring.runtimeBackedOffList': '等待重试',

  'monitoring.runtimeDeployedList': '本地已安装',

  'monitoring.versionDistribution': '版本分布',

  'monitoring.version': '版本',

  'monitoring.timeRange.1h': '近 1 小时',
  'monitoring.timeRangeLabel': '时间范围',

  'monitoring.timeRange.6h': '近 6 小时',

  'monitoring.timeRange.24h': '近 24 小时',

  'monitoring.timeRange.7d': '近 7 天',

  'monitoring.timeRange.30d': '近 30 天',

  // 日志

  'logs.title': '日志',

  'logs.subtitle': '搜索并查看流程执行历史。',

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

  'logs.traceId': '链路 ID',

  'logs.status': '状态',

  'logs.duration': '耗时',

  'logs.startTime': '开始时间',

  'logs.searchPlaceholder': '搜索日志…',

  'logs.selectStatus': '选择状态',

  'logs.export': '导出',

  'logs.noData': '暂无执行日志',

  // 通用筛选

  'filters.clear': '清空筛选',

  'filters.active': '当前筛选：',

  'filters.difficulty': '难度',

  'filters.category': '分类',

  'filters.search': '搜索',

  'filters.resultCount': '共找到 {{count}} 个示例',

  // 排序

  'sort.name': '按名称',

  'sort.difficulty': '按难度',

  'sort.duration': '按时长',

  // 日志详情

  'logs.detail': '日志详情',

  'logs.errorCode': '错误码',

  'logs.errorMessage': '错误信息',

  'logs.exportSuccess': '导出成功',

  'logs.status.success': '成功',

  'logs.status.failed': '失败',

  // 流程操作

  'process.searchPlaceholder': '搜索流程…',

  'process.deleteConfirm': '确定删除该流程吗？',

  // 部署筛选与校验

  'deployment.management': '部署管理',

  'deployment.managementDesc': '管理已发布版本及其路由别名。',

  'deployment.canaryRange': '灰度权重必须介于 1–9,999 个基点。',
  'deployment.canaryInteger': '灰度权重必须为整数基点值。',

  'deployment.canaryRequired': '请输入灰度流量权重。',

  'deployment.health.reason.withinThresholds': '灰度指标在配置阈值范围内。',

  'deployment.health.metricsScope': '指标范围',

  'deployment.health.metricsScope.workbench_server': '当前 Workbench 服务',

  'deployment.health.metricsSource': '指标来源',

  'deployment.health.metricsSource.execution_logs': '执行日志',

  'deployment.deploy': '部署',

  // 部署列表

  'deployment.searchPlaceholder': '按流程编码或部署 ID 搜索',

  'deployment.filterStatus': '选择状态',

  'deployment.filterAlias': '选择别名',

  // 运维概览

  'operate.title': '运维',

  'operate.subtitle': '管理流程版本与发布，监控执行状态，并查看运行日志。',

  'operate.deploy': '部署流程',

  'operate.monitor': '监控',

  'operate.explore': '快捷入口',

  'monitoring.runtimeTopology': '运行时拓扑',

  'operate.processMgmt': '流程管理',

  'operate.processMgmtDesc': '查看并管理流程定义。',

  'operate.deployMgmt': '部署管理',

  'operate.deployMgmtDesc': '管理已发布版本和别名路由。',

  'operate.realtimeMonitor': '监控',

  'operate.realtimeMonitorDesc': '执行状态和性能指标。',

  'operate.logQuery': '日志',

  'operate.logQueryDesc': '搜索并查看流程执行日志。',

  'operate.processesWithAliases': '已配置别名的流程',

  'operate.unit.process': '个',

  'operate.unit.items': '次',

  'operate.unit.times': '次',

  'operate.processExecutions': '启动后执行次数',

  'operate.successRate': '成功率',

  'operate.failedExecutions': '失败次数',

  'operate.recentDeploys': '最近部署',

  'operate.noRecentDeployments': '暂无部署记录',

  'operate.recentDeploymentsUnavailable': '最近部署暂不可用',

  'operate.viewAll': '查看全部',

  'operate.deploying': '部署中',

  'operate.failed': '失败',

  'operate.pending': '等待中',

  // 监控错误详情

  'monitoring.errorCount': '次数',

  'monitoring.lastOccurred': '最近发生',

  // 设计器

  'process.editInDesigner': '在设计器中编辑',
} as const

export default zhOperate
