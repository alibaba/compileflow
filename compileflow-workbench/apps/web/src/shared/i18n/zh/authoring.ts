const zhAuthoring = {
  'code.copy': '复制代码',

  'code.download': '下载代码',

  'code.copySuccess': '已复制到剪贴板',

  'code.copyFailed': '复制失败',

  'code.downloadSuccess': '已开始下载',

  // 导航

  'exec.title': '服务器执行',

  'exec.desc':
    '将在服务器上真实执行该草稿。动作可能调用外部系统并产生不可逆副作用，请在隔离环境中使用安全参数。',

  'exec.params': '执行参数（JSON）',

  'exec.paramsPlaceholder': '{"参数1": "值1", "参数2": 123}',

  'exec.execute': '执行流程',

  'exec.executing': '执行中…',

  'exec.success': '执行成功',

  'exec.failed': '执行失败',

  'exec.result': '执行结果',

  'exec.error': '执行出错：{{error}}',

  'exec.paramError': '参数不是有效 JSON，请检查格式。',

  'exec.noCode': '没有可执行的代码',

  // 主题

  'workspace.title': '构建',

  'workspace.subtitle': '在设计器中编排流程，从模板起步，快速迭代。',

  'workspace.newBpmn': '新建 BPMN',

  'workspace.newTbbpm': '新建 TBBPM',

  'workspace.browseExamples': '浏览示例',

  'workspace.newBpmnAction': '新建 BPMN 流程',

  'workspace.newBpmnDesc': '从 BPMN 建模画布开始。',

  'workspace.newTbbpmAction': '新建 TBBPM 流程',

  'workspace.newTbbpmDesc': '面向高吞吐的编译型进程内编排。',

  'workspace.browseExamplesAction': '浏览示例',

  'workspace.browseExamplesDesc': '从精选示例学习常见模式。',

  'workspace.apiIntegration': 'API 集成',

  'workspace.apiIntegrationDesc': '把 CompileFlow 接到你的业务系统。',

  'workspace.exportAll': '全部导出',

  'workspace.importData': '导入',

  'workspace.myProcesses': '我的流程',

  'workspace.availableTemplates': '可用模板',

  'workspace.thisMonth': '本月',

  'workspace.recentProjects': '最近项目',

  'workspace.noRecentProjects': '暂无最近项目',

  'workspace.open': '打开',

  'workspace.quickStart': '快速开始',

  'workspace.myWork': '我的工作',

  'workspace.quickTemplates': '快速模板',

  'workspace.use': '使用',

  'workspace.importFileLabel': '选择要导入的工作台 JSON 文件',

  'workspace.template.tpl-1.name': 'BPMN 入门模板',

  'workspace.template.tpl-1.description': '从开始到结束的最小可执行流程',

  'workspace.template.tpl-2.name': 'TBBPM 问候示例',

  'workspace.template.tpl-2.description': '包含输入、输出映射的自包含 Java 内联动作',

  'workspace.template.tpl-3.name': '空白 BPMN',

  'workspace.template.tpl-3.description': '用于可视化建模的空白 BPMN 流程',

  'workspace.template.tpl-4.name': 'TBBPM 入门模板',

  'workspace.template.tpl-4.description': '最小可执行 TBBPM 流程',

  // 运维首页

  'designer.view.visual': '可视化',

  'designer.view.tabsLabel': '视图模式',

  'designer.view.code': '代码',

  'designer.view.preview': '分栏',

  'designer.view.previewEmpty': '暂无内容',

  'designer.header.backToWorkspace': '返回构建',

  'designer.header.workspace': '构建',

  'designer.header.editName': '点击修改名称',

  'designer.header.unsavedChanges': '有未保存的更改',

  'designer.header.saving': '保存中…',

  'designer.header.saveDirty': '保存 *',

  'designer.header.saved': '已保存',

  'designer.status.draft': '草稿',

  'designer.status.local': '本地',

  'designer.xmlEditor.dirtyHint': '编辑内容仅在本地，点击「应用到画布」后才会更新可视化视图。',

  'designer.xmlEditor.parseError': 'XML 解析错误',

  'designer.xmlEditor.applyFailed': '应用 XML 失败',

  'designer.xmlEditor.applySuccess': 'XML 已应用到画布',

  'designer.xmlEditor.reset': '放弃修改',

  'designer.xmlEditor.apply': '应用到画布',

  'designer.xmlEditor.discardTitle': '放弃 XML 修改？',

  'designer.xmlEditor.discardMessage': 'XML 编辑器中的修改尚未应用到画布。',

  'designer.xmlEditor.discardConfirm': '放弃',

  'designer.xmlEditor.editorRegion': 'XML 代码编辑器',

  'designer.debug.simulationBanner':
    '仅限浏览器预览：条件按失败关闭的安全 Java 子集求值；Action 为模拟执行，完整 javac、重试、等待、子流程及并发语义请使用「引擎执行」。',

  'designer.debug.modeSimulation': '模拟调试',

  'designer.debug.modeEngine': '引擎执行',

  'designer.debug.engineBanner': '使用开发态 Mock Gateway 或 Workbench Server 托管执行。',

  'designer.debug.engineUnavailable': '执行后端不可用，请检查本地开发进程或同源网关路由。',

  'designer.debug.engineTitle': '引擎执行',

  'designer.debug.engineOnline': '在线',

  'designer.debug.engineOffline': '离线',

  'designer.debug.engineRefreshStatus': '刷新状态',

  'designer.debug.engineRun': '在服务器运行草稿',

  'designer.debug.engineExecutionWarningTitle': '服务器执行',

  'designer.debug.engineExecutionWarning':
    '此操作会在 Workbench Server 上真实执行当前草稿。脚本、Java Action 和 Spring Bean 可能以服务器权限运行并产生外部副作用。',

  'designer.debug.engineReset': '清空日志',

  'designer.debug.engineProcessCode': '流程代码',

  'designer.debug.engineParams': '输入参数（JSON）',

  'designer.debug.engineMissingCode': '引擎执行需要有效的流程 code',

  'designer.debug.engineInvalidParams': '参数 JSON 格式错误',

  'designer.debug.engineStart': '正在通过引擎执行: {{code}}',

  'designer.debug.engineSuccess': '引擎执行完成',

  'designer.debug.engineFailed': '引擎执行失败',

  'designer.save.workspaceSuccess': '已保存',

  'designer.save.operateSuccess': '已保存到运维流程库',

  'designer.save.failed': '保存失败',

  'designer.delete.operateSuccess': '运维流程已删除',

  'designer.autoSave.failed': '自动保存失败',

  'designer.autoSave.pending': '未保存 · 即将自动保存',

  'designer.toolbar.label': '画布工具栏',

  'designer.toolbar.alignGroup': '对齐工具',

  'designer.toolbar.distributeGroup': '分布工具',

  'designer.toolbar.batchGroup': '批量操作',

  'designer.toolbar.viewGroup': '视图控制',

  'designer.toolbar.alignLeft': '左对齐',

  'designer.toolbar.alignCenterV': '垂直居中对齐',

  'designer.toolbar.alignRight': '右对齐',

  'designer.toolbar.alignTop': '顶部对齐',

  'designer.toolbar.alignCenterH': '水平居中对齐',

  'designer.toolbar.alignBottom': '底部对齐',

  'designer.toolbar.distributeH': '水平分布',

  'designer.toolbar.distributeV': '垂直分布',

  'designer.toolbar.copySelected': '复制选中 (Ctrl+D)',

  'designer.toolbar.deleteSelected': '删除选中 (Delete)',

  'designer.toolbar.selectAll': '全选 (Ctrl+A)',

  'designer.toolbar.zoomIn': '放大 (Ctrl+滚轮)',

  'designer.toolbar.zoomOut': '缩小 (Ctrl+滚轮)',

  'designer.toolbar.zoomFit': '适应画布',

  'designer.toolbar.zoomReset': '重置视图',

  'designer.toolbar.loadExample': '加载示例',

  'designer.toolbar.loadExampleHint': '加载示例流程模板',

  'designer.toolbar.loadExampleSuccess': '示例流程已加载',

  'designer.toolbar.loadExampleFailed': '加载示例失败：{{message}}',

  'designer.layout.tabProperties': '属性',

  'designer.layout.rightPanelTabs': '右侧面板分区',

  'designer.layout.tabValidation': '验证',

  'designer.layout.tabDebug': '调试',

  'designer.layout.expandPalette': '展开节点面板',

  'designer.layout.collapsePalette': '收起节点面板',

  'designer.layout.expandProperties': '展开属性面板',

  'designer.layout.collapseProperties': '收起属性面板',

  'designer.layout.emptyTitle': '选择节点或边',

  'designer.layout.emptySub': '在画布上点击元素以查看其属性',

  'designer.layout.emptyHint': '使用工具栏「验证」和「调试」按钮检查流程',

  'designer.statusBar.zoomOut': '缩小',

  'designer.statusBar.zoomReset': '重置缩放',

  'designer.statusBar.zoomIn': '放大',

  'designer.statusBar.nodeCount': '节点数',

  'designer.statusBar.edgeCount': '边数',

  'designer.statusBar.nodes': '{{count}} 节点',

  'designer.statusBar.edges': '{{count}} 连线',

  'designer.statusBar.saving': '保存中…',

  'designer.statusBar.unsaved': '未保存',

  'designer.statusBar.saved': '已保存',

  'designer.contextMenu.editProps': '编辑属性',

  'designer.contextMenu.copy': '复制',

  'designer.contextMenu.delete': '删除',

  'designer.contextMenu.breakpoint': '设置断点',

  'designer.contextMenu.editCondition': '编辑条件',

  'designer.contextMenu.deleteEdge': '删除连接',

  'designer.contextMenu.paste': '粘贴',

  'designer.contextMenu.selectAll': '全选',

  'designer.palette.searchPlaceholder': '搜索节点...',

  'designer.palette.dragToAdd': '拖拽添加节点：{{label}}',

  'designer.palette.empty': '未找到匹配的节点',

  'designer.palette.emptyHint': '试试其他关键词，如"开始"、"决策"',

  'designer.palette.tbbpmTitle': '节点工具箱',

  'designer.palette.bpmnTitle': 'BPMN 节点',

  'designer.header.undo': '撤销 (Ctrl+Z)',

  'designer.header.redo': '重做 (Ctrl+Shift+Z)',

  'designer.header.copy': '复制 (Ctrl+C)',

  'designer.header.paste': '粘贴 (Ctrl+V)',

  'designer.header.toggleGrid': '切换网格 (Ctrl+G)',

  'designer.header.searchNodes': '搜索节点 (Ctrl+F)',

  'designer.header.validate': '验证流程',

  'designer.header.debug': '调试流程',

  'designer.header.variables': '变量管理',

  'designer.header.shortcuts': '快捷键 (Ctrl+/)',

  'designer.header.help': '帮助文档',

  'designer.header.menu.exportGroup': '导入 / 导出',

  'designer.header.menu.moreActions': '更多操作',

  'designer.header.menu.editingGroup': '编辑',

  'designer.header.menu.toolsGroup': '工具',

  'designer.header.menu.exportXml': '导出 XML',

  'designer.header.menu.exportImage': '导出图片',

  'designer.header.menu.importXml': '导入 XML',

  'designer.header.menu.xmlEditor': 'XML 源码编辑器',

  'designer.header.menu.flowGroup': '流程操作',

  'designer.header.menu.duplicate': '创建副本',

  'designer.header.menu.deleteProcess': '删除流程',

  'designer.header.deleteConfirmTitle': '确认删除',

  'designer.header.deleteConfirmContent': '确定要删除流程"{{name}}"？此操作不可恢复。',

  'designer.layout.breakpointSet': '已在节点 {{nodeId}} 设置断点',

  'designer.debug.sim.runComplete': '执行完成',

  'designer.debug.sim.runFailed': '执行失败: {{message}}',

  'designer.debug.sim.resetDone': '已重置',

  'designer.debug.sim.addBreakpointTitle': '添加断点',

  'designer.debug.sim.selectBreakpointNode': '选择要添加断点的节点',

  'designer.debug.sim.breakpointCondition': '断点条件',

  'designer.debug.sim.breakpointConditionPh': '可选 Java 条件，例如 hit == true（留空则始终命中）',

  'designer.debug.sim.breakpointUnconditional': '始终',

  'designer.debug.sim.selectNodeWarning': '请先选择节点',

  'designer.debug.sim.breakpointAdded': '已在节点 "{{name}}" 添加断点',

  'designer.debug.sim.breakpointRemoved': '断点已删除',

  'designer.debug.sim.panelTitle': '流程调试器',

  'designer.debug.sim.start': '开始',

  'designer.debug.sim.step': '单步',

  'designer.debug.sim.continue': '继续',

  'designer.debug.sim.reset': '重置',

  'designer.debug.sim.startTooltip': '开始执行',

  'designer.debug.sim.stepTooltip': '单步执行',

  'designer.debug.sim.continueTooltip': '继续执行',

  'designer.debug.sim.resetTooltip': '重置',

  'designer.debug.sim.initialVars': '初始变量（JSON格式）',

  'designer.debug.sim.currentNode': '当前节点：',

  'designer.debug.sim.breakpoints': '断点',

  'designer.debug.sim.variables': '变量',

  'designer.debug.sim.eventLog': '执行日志',

  'designer.debug.sim.noVariables': '暂无变量',

  'designer.debug.sim.add': '添加',

  'designer.debug.state.ready': '准备就绪',

  'designer.debug.state.running': '运行中',

  'designer.debug.state.paused': '已暂停',

  'designer.debug.state.completed': '已完成',

  'designer.debug.state.error': '错误',

  'designer.debug.event.nodeEnter': '进入节点',

  'designer.debug.event.nodeExit': '退出节点',

  'designer.debug.event.edgeTraverse': '遍历连线',

  'designer.debug.event.variableChange': '变量变化',

  'designer.debug.event.breakpointHit': '断点命中',

  'designer.debug.event.error': '错误',

  'designer.debug.col.nodeId': '节点ID',

  'designer.debug.col.condition': '条件',

  'designer.debug.col.status': '状态',

  'designer.debug.col.hitCount': '命中次数',

  'designer.debug.col.actions': '操作',

  'designer.debug.col.time': '时间',

  'designer.debug.col.type': '类型',

  'designer.debug.col.details': '详情',

  'designer.debug.enabled': '启用',

  'designer.debug.disabled': '禁用',

  'designer.debug.detail.node': '节点: {{id}}',

  'designer.debug.detail.edge': '连线: {{id}}',

  'designer.debug.detail.error': '错误: {{message}}',

  'designer.props.exampleTitle': '配置示例',

  'designer.props.nodeId': '节点ID',

  'designer.props.nodeName': '节点名称',

  'designer.props.nodeNameRequired': '请输入节点名称',

  'designer.props.nodeNamePlaceholder': '请输入节点名称',

  'designer.props.nodeType': '节点类型',

  'designer.props.documentation': '说明文档',

  'designer.props.documentationPlaceholder': '请输入节点说明（可选）',

  'designer.palette.tbbpm.cat.flow': '流程控制',

  'designer.palette.tbbpm.cat.task': '任务节点',

  'designer.palette.tbbpm.cat.gateway': '网关节点',

  'designer.palette.tbbpm.cat.subprocess': '子流程',

  'designer.palette.tbbpm.cat.loop': '循环',

  'designer.palette.tbbpm.cat.control': '循环控制',

  'designer.palette.tbbpm.cat.annotation': '注释',

  'designer.palette.tbbpm.node.start': '开始',

  'designer.palette.tbbpm.node.startDesc': '流程起点',

  'designer.palette.tbbpm.node.end': '结束',

  'designer.palette.tbbpm.node.endDesc': '流程终点',

  'designer.palette.tbbpm.node.autoTask': '自动任务',

  'designer.palette.tbbpm.node.autoTaskDesc': 'Java 方法或 Spring Bean',

  'designer.palette.tbbpm.node.waitTask': '等待任务',

  'designer.palette.tbbpm.node.waitTaskDesc': '等待外部信号',

  'designer.palette.tbbpm.node.waitEventTask': '等待事件',

  'designer.palette.tbbpm.node.waitEventTaskDesc': '等待特定事件',

  'designer.palette.tbbpm.node.timerTask': '定时任务',

  'designer.palette.tbbpm.node.timerTaskDesc': 'Durable 等待固定时长或指定时刻',

  'designer.palette.tbbpm.node.scriptTask': '脚本任务',

  'designer.palette.tbbpm.node.scriptTaskDesc': '内联代码 · Java / QLExpress',

  'designer.palette.tbbpm.node.exclusive': '排他网关',

  'designer.palette.tbbpm.node.exclusiveDesc': '条件分支',

  'designer.palette.tbbpm.node.parallel': '并行网关',

  'designer.palette.tbbpm.node.parallelDesc': '并行执行',

  'designer.palette.tbbpm.node.inclusive': '包容网关',

  'designer.palette.tbbpm.node.inclusiveDesc': '多条件分支',

  'designer.palette.tbbpm.node.subBpm': '内嵌 BPM',

  'designer.palette.tbbpm.node.subBpmDesc': '定义当前 BPM 内的嵌套作用域',

  'designer.palette.tbbpm.node.bpmCall': 'BPM 调用',

  'designer.palette.tbbpm.node.bpmCallDesc': '调用其他流程',

  'designer.palette.tbbpm.node.while': '条件循环',

  'designer.palette.tbbpm.node.whileDesc': '条件成立时重复执行循环体',

  'designer.palette.tbbpm.node.foreach': '集合遍历',

  'designer.palette.tbbpm.node.foreachDesc': '依次或并行处理集合中的每个元素',

  'designer.palette.tbbpm.node.continue': '继续循环',

  'designer.palette.tbbpm.node.continueDesc': '跳过当前迭代',

  'designer.palette.tbbpm.node.break': '中断循环',

  'designer.palette.tbbpm.node.breakDesc': '退出循环',

  'designer.palette.tbbpm.node.note': '注释',

  'designer.palette.tbbpm.node.noteDesc': '流程注释说明',

  'designer.palette.bpmn.cat.events': '事件',

  'designer.palette.bpmn.cat.tasks': '任务',

  'designer.palette.bpmn.cat.gateways': '网关',

  'designer.palette.bpmn.cat.composition': '组合',

  'designer.palette.bpmn.node.startEvent': '流程开始',

  'designer.palette.bpmn.node.startEventDesc': '流程起点',

  'designer.palette.bpmn.node.endEvent': '流程结束',

  'designer.palette.bpmn.node.endEventDesc': '流程终点',

  'designer.palette.bpmn.node.serviceTask': '服务任务',

  'designer.palette.bpmn.node.serviceTaskDesc': '自动化服务',

  'designer.palette.bpmn.node.scriptTask': '脚本任务',

  'designer.palette.bpmn.node.scriptTaskDesc': '脚本执行',

  'designer.palette.bpmn.node.receiveTask': '接收任务',

  'designer.palette.bpmn.node.receiveTaskDesc': '接收消息',

  'designer.palette.bpmn.node.exclusiveGateway': '排他网关',

  'designer.palette.bpmn.node.exclusiveGatewayDesc': '单一路径选择',

  'designer.palette.bpmn.node.parallelGateway': '并行网关',

  'designer.palette.bpmn.node.parallelGatewayDesc': '并行执行',

  'designer.palette.bpmn.node.inclusiveGateway': '包容网关',

  'designer.palette.bpmn.node.inclusiveGatewayDesc': '多条件分支',

  'designer.palette.bpmn.node.callActivity': '调用活动',

  'designer.palette.bpmn.node.callActivityDesc': '调用其他流程',

  'designer.palette.bpmn.node.subProcess': '嵌入式子流程',

  'designer.palette.bpmn.node.subProcessDesc': '流程内的结构化独立执行作用域',

  'designer.props.common.add': '添加',

  'designer.props.common.close': '关闭',

  'designer.props.common.done': '完成',

  'designer.props.common.optional': '可选',

  'designer.props.common.actionType': '动作类型',

  'designer.props.common.actionTypeJava': 'Java 类方法',

  'designer.props.common.actionTypeSpringBean': 'Spring Bean',

  'designer.props.common.actionTypeScript': '内联脚本',

  'designer.props.common.execution': 'Durable 执行语义',

  'designer.props.common.executionHelp':
    'Replayable 动作可在恢复后重新执行；Effect 动作会先跨越 Durable 提交边界再分发。',

  'designer.props.common.executionReplayable': '可重放（默认）',

  'designer.props.common.executionEffect': 'Effect',

  'designer.props.common.effectPolicyEnabled': '启用 Effect 恢复策略',

  'designer.props.common.effectPolicyMode': '恢复策略来源',

  'designer.props.common.effectPolicyStatic': '静态配置',

  'designer.props.common.effectPolicyDynamic': '流程变量',

  'designer.props.common.effectRecovery': '恢复策略',

  'designer.props.common.effectRecoveryManual': '人工处理',

  'designer.props.common.effectRecoveryRetry': '重试',

  'designer.props.common.effectRecoveryReconcile': '对账',

  'designer.props.common.recoveryPlanVariable': '恢复计划变量',

  'designer.props.common.recoveryDelay': '恢复延迟',

  'designer.props.common.maxRecoveryDuration': '最长恢复时长',

  'designer.props.common.maxReconcileAttempts': '最大对账尝试次数',

  'designer.props.common.reconcileActionEnabled': '配置对账动作',

  'designer.props.common.className': '类名 (Class)',

  'designer.props.common.classNameHelp': '完整 Java 类名',

  'designer.props.common.methodName': '方法名 (Method)',

  'designer.props.common.methodNameOptional': '方法名 (Method)',

  'designer.props.common.methodNameOptionalHelp': '默认调用 execute，仅在调用其他方法时配置',

  'designer.props.common.beanName': 'Bean 名称',

  'designer.props.common.beanNameHelp': 'Spring 容器中的 Bean 名称',

  'designer.props.common.scriptLanguage': '语言',

  'designer.props.common.scriptSource': '源码',

  'designer.props.common.timeout': '超时',

  'designer.props.common.timeoutHelp': 'ISO 8601 格式，例如 PT30S（30秒）',

  'designer.props.common.invocationTimeout': '调用总超时',

  'designer.props.common.invocationTimeoutHelp': '整个调用的最长时间，包含所有尝试和重试退避',

  'designer.props.common.attemptTimeout': '单次尝试超时',

  'designer.props.common.attemptTimeoutHelp': '每次尝试的最长时间',

  'designer.props.common.invocationPolicyEnabled': '启用调用策略',

  'designer.props.common.maxAttempts': '最大调用次数',

  'designer.props.common.maxAttemptsHelp': '包含首次尝试，范围 1 到 100',

  'designer.props.common.initialBackoff': '初始退避',

  'designer.props.common.initialBackoffHelp': '非负 ISO 8601 时长，例如 PT5S',

  'designer.props.common.backoffMultiplier': '退避倍数',

  'designer.props.common.backoffMultiplierHelp': '必须是大于等于 1.0 的有限数',

  'designer.props.common.maxBackoff': '最大退避',

  'designer.props.common.maxBackoffHelp': '不小于初始退避的 ISO 8601 时长',

  'designer.props.common.jitter': '重试抖动',

  'designer.props.common.jitterHelp': '全抖动会在 0 到退避上限间随机分散重试，是生产默认值',

  'designer.props.common.jitterFull': '全抖动（推荐）',

  'designer.props.common.jitterNone': '不使用抖动',

  'designer.props.common.retryOn': '重试条件',

  'designer.props.common.onFailure': '失败处理',

  'designer.props.common.action': '动作',

  'designer.props.common.defaultValueEnabled': '配置默认值',

  'designer.props.common.defaultEdgeId': '默认出向流',

  'designer.props.common.defaultEdgeIdHelp': '无条件命中时使用的已有出向流',

  'designer.props.common.gatewayJoinNoConfig': '汇合网关是纯同步点，不包含路由属性。',

  'designer.props.common.exprBuilderTooltip': '可视化构建表达式',

  'designer.props.section.basic': '基础配置',

  'designer.props.section.actionType': '动作类型',

  'designer.props.section.execControl': '执行控制',

  'designer.props.section.outgoingConditions': '出边条件',

  'designer.props.section.invocationPolicy': '执行策略',

  'designer.props.section.actionConfig': '动作配置（可选）',

  'designer.props.section.invocationPolicyTitle': '调用策略',

  'designer.props.section.effectPolicyTitle': 'Effect 恢复',

  'designer.props.section.reconcileActionTitle': '对账动作',

  'designer.props.section.varParams': '变量参数',

  'designer.props.section.varTransfer': '变量传递',

  'designer.props.col.seq': '序号',

  'designer.props.col.targetNode': '目标节点',

  'designer.props.col.conditionExpr': '条件表达式',

  'designer.props.col.varName': '变量名',

  'designer.props.col.localVarName': '局部变量名',

  'designer.props.col.javaType': 'Java 类型',

  'designer.props.col.direction': '方向',

  'designer.props.col.mappingReference': '来源 / 目标',

  'designer.props.col.defaultValue': '默认值',

  'designer.props.col.description': '说明',

  'designer.props.table.emptyParams': '暂无参数，点击"添加"',

  'designer.props.table.emptySubVars': '暂无变量传递配置',

  'designer.props.alert.noOutgoingEdges': '未发现出边',

  'designer.props.alert.noOutgoingEdgesExclusiveDesc': '请从此排他网关连线到其他节点后再配置条件',

  'designer.props.alert.noOutgoingEdgesGatewayDesc': '请从此网关节点连线到其他节点后再配置条件',

  'designer.props.node.note.title': '注释节点',

  'designer.props.node.note.desc': '设计时注解，不参与流程执行',

  'designer.props.node.note.content': '注释内容',

  'designer.props.node.note.contentPlaceholder': '输入注释内容...',

  'designer.props.node.parallel.title': '并行网关',

  'designer.props.node.parallel.desc': '分叉时激活所有出边并行执行；汇聚时等待所有入边完成后继续',

  'designer.props.node.parallel.noConfig': '并行网关无需额外配置',

  'designer.props.node.parallel.noConfigDesc':
    '所有出边都会被激活并行执行。汇聚时等待所有并行分支全部到达后继续。',

  'designer.props.node.break.title': 'Break（中断循环）',

  'designer.props.node.break.desc': '退出当前循环，可配置执行条件',

  'designer.props.node.continue.title': 'Continue（继续循环）',

  'designer.props.node.continue.desc': '跳过当前迭代，进入下一次循环，可配置执行条件',

  'designer.props.node.conditionOptional': '执行条件（可选）',

  'designer.props.node.conditionBreakHelp': '仅当表达式为 true 时执行中断；留空则无条件中断',

  'designer.props.node.conditionContinueHelp': '仅当表达式为 true 时执行继续；留空则无条件继续',

  'designer.props.node.exclusive.title': '排他网关',

  'designer.props.node.exclusive.desc': '基于条件选择唯一的输出路径',

  'designer.props.node.inclusive.title': '包容网关',

  'designer.props.node.inclusive.desc': '满足条件的所有出边都会被激活并行执行',

  'designer.props.node.autoTask.title': '自动任务节点',

  'designer.props.node.autoTask.desc': '调用应用提供的 Java 方法或 Spring Bean',

  'designer.props.node.subBpm.title': '内嵌 BPM',

  'designer.props.node.subBpm.desc': '在当前 BPM 内部定义的嵌套作用域',

  'designer.props.node.subBpm.boundaryHint':
    '内嵌 BPM 必须包含且仅包含一个 start 和一个 end 节点。',

  'designer.props.node.subBpm.children': '内部节点',

  'designer.props.node.subBpm.childrenHelp': '选择归属于该内嵌 BPM 的节点。',

  'designer.props.node.bpmCall.title': 'BPM 调用',

  'designer.props.node.bpmCall.desc': '调用另一个 BPM 定义',

  'designer.props.node.bpmCall.code': 'BPM 代码',

  'designer.props.node.bpmCall.codeHelp': '被调用 BPM 的唯一标识',

  'designer.props.node.waitTask.title': '等待任务',

  'designer.props.node.waitEventTask.title': '等待事件任务',

  'designer.props.node.waitTask.desc': '外部触发时，以当前状态节点 ID 为入口启动一次新执行',

  'designer.props.node.waitTask.eventName': '事件名称',

  'designer.props.node.waitTask.eventHelp': '等待的事件名称',

  'designer.props.node.timerTask.title': '定时任务',

  'designer.props.node.timerTask.desc': '等待固定时长、动态时长或指定时刻',

  'designer.props.node.timerTask.scheduleType': '定时方式',

  'designer.props.node.timerTask.duration': '固定时长',

  'designer.props.node.timerTask.durationHelp': 'ISO-8601 Duration，例如 PT30S',

  'designer.props.node.timerTask.durationExpression': '时长表达式',

  'designer.props.node.timerTask.durationExpressionHelp': '返回 Duration 的 Java 表达式',

  'designer.props.node.timerTask.wakeAtExpression': '唤醒时刻表达式',

  'designer.props.node.timerTask.wakeAtExpressionHelp': '返回唤醒时刻的 Java 表达式',

  'designer.props.node.scriptTbbpm.title': '脚本任务节点',

  'designer.props.node.scriptTbbpm.desc':
    '执行流程定义内的内联代码；内置 QLExpress 和受信任的进程内 Java 17',

  'designer.props.node.loop.whileTitle': 'While 循环',

  'designer.props.node.loop.whileDesc': 'Java 条件为 true 时重复执行局部循环体',

  'designer.props.node.loop.forEachTitle': 'Foreach 循环',

  'designer.props.node.loop.forEachDesc': '顺序遍历集合，或使用 Durable 有序并行模式',

  'designer.props.node.loop.execution': '执行方式',

  'designer.props.node.loop.executionHelp':
    '顺序模式接受 Iterable 或数组；并行模式仅支持 Durable 执行，且输入必须兼容 List',

  'designer.props.node.loop.sequentialExecution': '顺序执行',

  'designer.props.node.loop.parallelExecution': '并行执行',

  'designer.props.node.loop.whileExpr': '循环条件表达式',

  'designer.props.node.loop.whileExprHelp': '当表达式为true时继续循环，为false时退出',

  'designer.props.node.loop.maxIterations': '最大迭代次数',

  'designer.props.node.loop.collection': '集合变量名',

  'designer.props.node.loop.collectionHelp': '包含输入集合的已声明流程变量或外层循环变量',

  'designer.props.node.loop.item': '元素变量名',

  'designer.props.node.loop.itemHelp': '当前遍历元素的变量名（在循环体内使用）',

  'designer.props.node.loop.itemType': '元素类型',

  'designer.props.node.loop.itemTypeHelp': '与集合元素类型一致的 Java 类名',

  'designer.props.node.loop.index': '索引变量名',

  'designer.props.node.loop.indexHelp': '可选：当前元素的索引变量名（从0开始）',

  'designer.props.node.loop.outputTarget': '输出目标',

  'designer.props.node.loop.outputSource': '输出来源',

  'designer.props.node.loop.outputTargetHelp':
    '用于按输入顺序接收结果的已声明 List 流程变量；两个输出字段必须同时配置或同时留空',

  'designer.props.node.loop.outputSourceHelp':
    '每次迭代前重置为默认值、迭代结束后追加到输出集合的已声明 inner 流程变量',

  'designer.props.node.loop.bodyNodes': '循环体节点',

  'designer.props.node.loop.bodyNodesHelp': '由当前循环直接包含的节点',

  'designer.props.node.loop.bodyNodesPlaceholder': '选择循环体节点',

  'designer.props.bpmnLoop.title': 'BPMN 循环特征',

  'designer.props.bpmnLoop.mode': '循环模式',

  'designer.props.bpmnLoop.none': '不循环',

  'designer.props.bpmnLoop.standard': '标准 while / until 循环',

  'designer.props.bpmnLoop.multiInstance': '多实例',

  'designer.props.bpmnLoop.testBefore': '首次迭代前判断',

  'designer.props.bpmnLoop.testBeforeHelp':
    '开启为 while 语义；关闭时先执行一次再判断（until 语义）',

  'designer.props.bpmnLoop.maximum': '最大迭代次数',

  'designer.props.subProcess.body': '嵌入式流程体',

  'designer.props.subProcess.children': '直属子节点',

  'designer.props.subProcess.childrenHelp':
    '请选择且仅选择一个开始事件、一个结束事件，以及该子流程直接拥有的全部节点',

  'designer.props.subProcess.boundaryHint': '嵌入式子流程是独立图容器，顺序流不能跨越该容器边界。',

  'designer.props.node.exclusiveGateway.title': 'XOR 网关（互斥网关）',

  'designer.props.node.exclusiveGateway.desc':
    '只有一条出边条件为真时执行对应分支。条件在出边（连接线）上配置。',

  'designer.props.node.inclusiveGateway.title': 'Inclusive 网关（包容网关）',

  'designer.props.node.inclusiveGateway.desc':
    '所有条件为真的出边都会被激活并行执行；汇聚时等待所有被激活的入边完成。',

  'designer.props.node.parallelGateway.desc':
    '分叉时激活所有出边并行执行；汇聚时等待所有入边完成后继续。无需配置额外属性。',

  'designer.props.node.edgeConditionHint': '选中连接线后在「边属性」面板编辑条件',

  'designer.validation.title': '流程验证',

  'designer.validation.revalidate': '重新验证',

  'designer.validation.notRun': '未验证',

  'designer.validation.notRunDesc': '点击「验证流程」按钮开始验证',

  'designer.validation.passed': '验证通过',

  'designer.validation.passedDesc': '流程定义没有发现问题 ✓',

  'designer.validation.errors': '错误',

  'designer.validation.warnings': '警告',

  'designer.validation.infos': '信息',

  'designer.validation.suggestion': '建议：',

  'designer.validation.involvedNodes': '涉及节点：',

  'designer.validation.involvedEdges': '涉及连接线：',

  'designer.validation.type.cycle': '环路',

  'designer.validation.type.isolated': '孤岛',

  'designer.validation.type.deadlock': '死锁',

  'designer.validation.type.unreachable': '不可达',

  'designer.validation.type.multiStart': '多起点',

  'designer.validation.type.multiEnd': '多终点',

  'designer.validation.type.noEnd': '无终点',

  'designer.validation.type.orphanEdge': '孤立边',

  'designer.validation.type.property': '节点属性',

  'designer.validation.toast.passed': '验证通过，未发现问题',

  'designer.validation.toast.errors': '发现 {{count}} 个错误，请在验证面板查看详情',

  'designer.validation.toast.warnings': '发现 {{count}} 个警告，请在验证面板查看详情',

  'designer.validation.property.start.mustHaveOutgoing': '开始节点必须有至少一条出边',

  'designer.validation.property.start.shouldNotHaveIncoming': '开始节点不应有入边',

  'designer.validation.property.end.mustHaveIncoming': '结束节点必须有至少一条入边',

  'designer.validation.property.end.shouldNotHaveOutgoing': '结束节点不应有出边',

  'designer.validation.property.gateway.invalidShape':
    '网关必须是分流（1 条入边、至少 2 条出边）或汇聚（至少 2 条入边、1 条出边）',

  'designer.validation.property.gateway.joinConditionUnsupported': '汇聚网关的出边不能配置条件',

  'designer.validation.property.gateway.parallelConditionUnsupported': '并行网关的出边不能配置条件',

  'designer.validation.property.gateway.parallelJoinConditionUnsupported':
    '并行汇聚网关的入边不能配置条件',

  'designer.validation.property.gateway.multipleDefaultBranches':
    '包容网关最多只能配置一条无条件默认分支',

  'designer.validation.property.gateway.nestedConcurrencyUnsupported':
    '循环内暂不支持并行或包容分流',

  'designer.validation.property.node.requiresExplicitGateway': '非网关节点不能分支，请使用显式网关',

  'designer.validation.property.node.conditionRequiresGateway':
    '条件只能配置在排他或包容分流网关的出边上',

  'designer.validation.property.node.inapplicableProperty':
    '属性“{{property}}”不属于 TBBPM 节点类型“{{nodeType}}”',

  'designer.validation.property.condition.directMutation':
    '条件表达式必须无副作用，不允许直接修改状态「{{operator}}」',

  'designer.validation.property.mapping.inapplicableDefault':
    'defaultValue 仅适用于未配置 source 的输入映射（冲突类型：{{reason}}）',

  'designer.validation.property.action.missingType': '必须配置动作类型',

  'designer.validation.property.action.missingClass': '必须配置动作 Java 类',

  'designer.validation.property.action.invalidClass': '动作 Java 类必须是合法的规范类名',

  'designer.validation.property.action.invalidMethod': '动作方法必须是合法的 Java 标识符',

  'designer.validation.property.action.missingBean': '必须配置动作 Spring Bean 名称',

  'designer.validation.property.action.invalidBean':
    '动作 Spring Bean 名称不能包含首尾空白或控制字符',

  'designer.validation.property.action.missingScriptLanguage': '必须配置脚本语言',

  'designer.validation.property.action.missingScriptSource': '必须配置脚本源码',

  'designer.validation.property.action.unsupportedType': '不支持动作类型「{{actionType}}」',

  'designer.validation.property.scriptTask.unsupportedActionType':
    'scriptTask 不支持动作类型「{{actionType}}」，请使用 script',

  'designer.validation.property.autoTask.unsupportedActionType':
    'autoTask 不支持动作类型「{{actionType}}」，请使用 java 或 spring-bean',

  'designer.validation.property.invocationPolicy.invalid': 'InvocationPolicy 配置无效：{{message}}',

  'designer.validation.property.effectPolicy.invalid': 'Effect 恢复策略无效：{{message}}',

  'designer.validation.property.waitEventTask.missingEvent':
    '等待事件任务必须配置 event（事件名称）',

  'designer.validation.property.timerTask.schedule':
    '定时任务必须且只能配置 duration、durationExpression 或 wakeAtExpression 中的一项',

  'designer.validation.property.bpmCall.missingCode': 'BPM 调用必须配置 code',

  'designer.validation.property.processCall.targetRequired':
    '流程调用必须声明 classpath 路径或精确版本',

  'designer.validation.property.processCall.targetConflict':
    '流程调用不能同时声明 classpath 和 version',

  'designer.validation.property.processCall.invalidReference':
    '被调用流程的 code、classpath 或 version 不合法',

  'designer.validation.property.container.unknownParent': '父容器「{{parentId}}」不存在',

  'designer.validation.property.container.invalidParent': '父节点「{{parentId}}」不是 TBBPM 容器',

  'designer.validation.property.container.invalidChildType': '当前容器内不允许 {{nodeType}} 节点',

  'designer.validation.property.container.parentCycle': '容器的包含关系存在环',

  'designer.validation.property.container.crossBoundaryTransition': '连接线不能跨越容器边界',

  'designer.validation.property.loop.endHasOutgoing': '循环体的结束节点不能有出边',

  'designer.validation.property.loop.missingBody': '循环必须包含循环体',

  'designer.validation.property.loop.missingCondition': '条件循环必须配置 condition',

  'designer.validation.property.loop.invalidMaxIterations':
    'maxIterations 必须是 1 到 2,147,483,647 之间的整数',

  'designer.validation.property.loop.missingCollection': '集合遍历必须配置 collection',

  'designer.validation.property.loop.unknownCollection': 'collection「{{name}}」未声明',

  'designer.validation.property.loop.missingItem': '集合遍历必须配置 item',

  'designer.validation.property.loop.invalidLocalVariable':
    '{{property}}「{{value}}」不是合法变量名',

  'designer.validation.property.loop.variableShadowing': '局部变量「{{name}}」不能遮蔽已有变量',

  'designer.validation.property.loop.itemIndexCollision': 'item 和 index 必须不同',

  'designer.validation.property.loop.invalidItemType': 'itemType 必须是合法 Java 类型名',

  'designer.validation.property.loop.unknownOutputReference': '输出引用「{{name}}」未声明',

  'designer.validation.property.loop.outputReferenceCollision': '输出 target 和 source 必须不同',

  'designer.validation.property.loop.outputSourceNotInner': '输出 source 必须是 inner 流程变量',

  'designer.validation.property.loop.parallelBreakUnsupported': '并行集合遍历不支持 break',

  'designer.validation.property.loop.rootOnlyChildType': 'break/continue 必须位于循环体内',

  'designer.validation.property.conn.missingSource': '连接的源节点 "{{nodeId}}" 不存在',

  'designer.validation.property.conn.missingTarget': '连接的目标节点 "{{nodeId}}" 不存在',

  'designer.validation.property.conn.selfLoop': '节点不能连接到自己',

  'designer.validation.property.note.transitionNotAllowed': '注释节点不能参与执行转移',

  'designer.validation.property.bpmn.start.mustHaveOutgoing': '开始事件必须有出边',

  'designer.validation.property.bpmn.start.shouldNotHaveIncoming': '开始事件不应有入边',

  'designer.validation.property.bpmn.end.mustHaveIncoming': '结束事件必须有入边',

  'designer.validation.property.bpmn.end.shouldNotHaveOutgoing': '结束事件不应有出边',

  'designer.validation.property.bpmn.serviceTask.missingAction':
    '服务任务必须配置 CompileFlow 动作',

  'designer.validation.property.bpmn.serviceTask.unsupportedActionType':
    '服务任务仅支持 Java 方法和 Spring Bean 动作',

  'designer.validation.property.bpmn.scriptTask.missingScriptFormat':
    '脚本任务必须配置 scriptFormat',

  'designer.validation.property.bpmn.scriptTask.missingScript': '脚本任务应配置 script 内容',

  'designer.validation.property.bpmn.gateway.minTwoOutgoing': '网关应有至少 2 条出边（分支）',

  'designer.validation.property.bpmn.gateway.invalidShape':
    '网关必须是分流（1 条入边、至少 2 条出边）或汇聚（至少 2 条入边、1 条出边）',

  'designer.validation.property.bpmn.node.inapplicableProperty':
    '属性“{{property}}”不属于 BPMN 节点类型“{{nodeType}}”',

  'designer.validation.property.bpmn.gateway.defaultUnsupported': '并行网关不能配置默认流',

  'designer.validation.property.bpmn.gateway.parallelConditionUnsupported':
    '并行网关的出边不能配置条件',

  'designer.validation.property.bpmn.gateway.parallelJoinConditionUnsupported':
    '并行汇聚网关的入边不能是条件流',

  'designer.validation.property.bpmn.gateway.nestedConcurrencyUnsupported':
    '嵌入式子流程内暂不支持并行或包容分流',

  'designer.validation.property.bpmn.gateway.defaultOnJoin': '汇聚网关不能配置默认流',

  'designer.validation.property.bpmn.gateway.joinConditionUnsupported':
    '汇聚网关的出边不能配置条件',

  'designer.validation.property.bpmn.gateway.invalidDefault':
    '默认流「{{connectionId}}」不是该网关的出边',

  'designer.validation.property.bpmn.gateway.defaultHasCondition':
    '默认流「{{connectionId}}」不能配置条件',

  'designer.validation.property.bpmn.gateway.branchMissingCondition':
    '非默认流「{{connectionId}}」必须配置 Java 条件',

  'designer.validation.property.bpmn.gateway.duplicateCondition': '排他网关的出边条件不能重复',

  'designer.validation.property.bpmn.node.requiresExplicitGateway':
    '非网关节点不能分支，请使用显式网关',

  'designer.validation.property.bpmn.node.conditionRequiresGateway':
    '条件只能配置在排他或包容分流网关的出边上',

  'designer.validation.property.bpmn.receiveTask.missingMessageRef': '接收任务必须配置 messageRef',

  'designer.validation.property.bpmn.receiveTask.unknownMessageRef':
    '接收任务的 messageRef「{{messageRef}}」必须且只能解析到一个 BPMN message',

  'designer.validation.property.bpmn.message.missingId': 'BPMN message 必须配置 ID',

  'designer.validation.property.bpmn.message.duplicateId': 'BPMN message ID「{{messageId}}」重复',

  'designer.validation.property.bpmn.message.idCollision':
    'BPMN message ID「{{messageId}}」与节点或顺序流 ID 冲突',

  'designer.validation.property.bpmn.message.missingName':
    'BPMN message「{{messageId}}」必须配置运行时事件名',

  'designer.validation.property.bpmn.callActivity.missingCalledElement':
    '调用活动必须配置 calledElement',

  'designer.validation.property.bpmn.subProcess.unknownParent':
    '节点引用了不存在的子流程父级「{{parentId}}」',

  'designer.validation.property.bpmn.subProcess.invalidParent':
    '节点父级「{{parentId}}」不是嵌入式子流程',

  'designer.validation.property.bpmn.subProcess.triggerEntryChild':
    '嵌入式子流程内不支持触发入口活动',

  'designer.validation.property.bpmn.subProcess.parentCycle': '嵌入式子流程层级存在父级循环',

  'designer.validation.property.bpmn.subProcess.crossContainerTransition':
    '顺序流不能跨越嵌入式子流程边界',

  'designer.validation.property.bpmn.process.duplicateNodeId':
    '整个 BPMN 文档中的节点 ID 必须全局唯一',

  'designer.validation.property.bpmn.loop.unsupportedNode':
    '循环特征只能配置在同步服务任务、脚本任务、调用活动或嵌入式子流程上',

  'designer.validation.property.bpmn.loop.invalid': '循环配置无效：{{message}}',

  'designer.validation.property.bpmn.loop.unknownCollection':
    '多实例集合「{{name}}」不是已声明的流程变量',

  'designer.validation.property.bpmn.loop.unknownOutputReference':
    '多实例输出「{{name}}」不是已声明的流程变量',

  'designer.validation.property.bpmn.loop.outputSourceNotInner':
    '多实例输出 source 必须是 inner 流程变量',

  'designer.validation.property.bpmn.loop.variableShadowing':
    '多实例变量「{{name}}」不能遮蔽流程状态变量',

  'designer.validation.property.bpmn.invocationPolicy.unsupportedNode':
    'InvocationPolicy 只能配置在服务任务或脚本任务上',

  'designer.validation.property.bpmn.invocationPolicy.invalid':
    'InvocationPolicy 配置无效：{{message}}',

  'designer.validation.property.bpmn.mapping.incomplete': '变量映射缺少必填字段',

  'designer.validation.property.bpmn.mapping.duplicate': '变量映射名称「{{name}}」重复',

  'designer.validation.property.bpmn.mapping.missingOutputTarget': '输出映射必须声明 target',

  'designer.validation.property.bpmn.mapping.unknownOutputTarget':
    '输出目标「{{target}}」不是已声明的流程变量',

  'designer.validation.property.bpmn.mapping.duplicateOutputTarget':
    '输出目标「{{target}}」被重复赋值',

  'designer.validation.property.bpmn.mapping.multipleOutputs': '一个动作最多只能声明一个输出映射',

  'designer.validation.property.tbbpm.mapping.incomplete': '变量映射必须包含名称和 Java 类型',

  'designer.validation.property.tbbpm.mapping.duplicate': '变量映射名称「{{name}}」重复',

  'designer.validation.property.tbbpm.mapping.missingOutputTarget': '子流程输出映射必须声明 target',

  'designer.validation.property.tbbpm.mapping.unknownOutputTarget':
    '输出目标「{{target}}」不是已声明的流程变量',

  'designer.validation.property.tbbpm.mapping.duplicateOutputTarget':
    '输出目标「{{target}}」被重复赋值',

  'designer.validation.property.tbbpm.mapping.multipleOutputs': '一个动作最多只能声明一个输出映射',

  'designer.validation.property.process.duplicateNodeId': '节点 ID「{{nodeId}}」重复',

  'designer.validation.property.process.variable.missingName': '流程变量名不能为空',

  'designer.validation.property.process.variable.invalidName':
    '流程变量「{{name}}」必须是合法的 Java 标识符',

  'designer.validation.property.process.variable.reservedName':
    '流程变量「{{name}}」使用了 CompileFlow 保留前缀',

  'designer.validation.property.process.variable.duplicateName': '流程变量「{{name}}」重复',

  'designer.validation.property.process.variable.missingType':
    '流程变量「{{name}}」必须声明 Java 类型',

  'designer.validation.property.process.variable.invalidDirection':
    '流程变量「{{name}}」的方向必须是 param、return 或 inner',

  'designer.validation.summary.passed': '✓ 流程验证通过',

  'designer.validation.summary.failed': '✗ {{details}}',

  'designer.validation.summary.errors': '{{count}} 个错误',

  'designer.validation.summary.warnings': '{{count}} 个警告',

  'designer.validation.summary.infos': '{{count}} 条信息',

  'designer.validation.issue.multiStart.missing': '流程缺少起点节点',

  'designer.validation.issue.multiStart.missingSuggestion': '添加一个 Start 节点作为流程起点',

  'designer.validation.issue.multiStart.multiple': '流程有 {{count}} 个起点节点',

  'designer.validation.issue.multiStart.multipleSuggestion': '通常一个流程只应该有一个起点',

  'designer.validation.issue.noEnd.message': '流程缺少终点节点',

  'designer.validation.issue.noEnd.suggestion': '添加至少一个 End 节点作为流程终点',

  'designer.validation.issue.multiEnd.message': '流程存在 {{count}} 个终点节点',

  'designer.validation.issue.multiEnd.suggestion': '仅保留一个 End 节点',

  'designer.validation.issue.cycle.message': '检测到环路 #{{index}}：{{path}}',

  'designer.validation.issue.cycle.suggestion': '移除环路中的某条连接线，或添加终止条件',

  'designer.validation.issue.isolated.message': '检测到 {{count}} 个孤岛节点（没有任何连接）',

  'designer.validation.issue.isolated.suggestion': '连接这些节点到流程中，或删除它们',

  'designer.validation.issue.unreachable.message':
    '检测到 {{count}} 个不可达节点（从起点无法到达）',

  'designer.validation.issue.unreachable.suggestion': '添加从起点到这些节点的路径，或删除这些节点',

  'designer.validation.issue.orphanEdge.message':
    '检测到 {{count}} 条孤立连接线（源或目标节点不存在）',

  'designer.validation.issue.orphanEdge.suggestion': '删除这些孤立的连接线',

  'designer.validation.issue.deadlock.ambiguous':
    '并行网关「{{name}}」配置不明确（入度={{inDegree}}，出度={{outDegree}}）',

  'designer.validation.issue.deadlock.ambiguousSuggestion':
    'Fork 网关应有 1 个输入和多个输出，Join 网关应有多个输入和 1 个输出',

  'designer.validation.issue.deadlock.missingJoin':
    '并行 Fork 网关「{{name}}」缺少对应的 Join 网关',

  'designer.validation.issue.deadlock.missingJoinSuggestion':
    '在所有并行分支汇合处添加一个 Join 网关',

  'designer.clipboard.nothingToCopy': '没有可复制的内容',

  'designer.clipboard.selectNodeFirst': '请先选择要复制的节点',

  'designer.clipboard.cannotCopyStartEnd': '不能复制开始/结束节点',

  'designer.clipboard.copied': '已复制 {{count}} 个节点',

  'designer.clipboard.copyFailed': '复制失败',

  'designer.clipboard.openProcessFirst': '请先打开一个流程',

  'designer.clipboard.empty': '剪贴板为空',

  'designer.clipboard.expired': '剪贴板数据已过期',

  'designer.clipboard.noNodes': '剪贴板中没有节点',

  'designer.clipboard.pasted': '已粘贴 {{count}} 个节点',

  'designer.clipboard.pasteFailed': '粘贴失败',

  'designer.clipboard.cleared': '剪贴板已清空',

  'designer.properties.panel.tbbpm': '属性',

  'designer.properties.panel.bpmn': 'BPMN 属性',

  'designer.properties.tab.note': '注释属性',

  'designer.properties.tab.task': '任务属性',

  'designer.properties.tab.script': '脚本属性',

  'designer.properties.tab.parallel': '并行属性',

  'designer.properties.tab.inclusive': '包容属性',

  'designer.properties.tab.subBpm': '内嵌 BPM',

  'designer.properties.tab.bpmCall': 'BPM 调用',

  'designer.properties.tab.wait': '等待属性',

  'designer.properties.tab.timer': '定时属性',

  'designer.properties.tab.loop': '循环属性',

  'designer.properties.tab.break': '中断属性',

  'designer.properties.tab.continue': '继续属性',

  'designer.properties.tab.service': '服务配置',

  'designer.properties.tab.receive': '消息配置',

  'designer.properties.tab.exclusive': 'XOR 网关',

  'designer.properties.tab.parallelGateway': 'AND 网关',

  'designer.properties.tab.inclusiveGateway': 'OR 网关',

  'designer.properties.tab.callActivity': '调用活动',

  'designer.properties.tab.subProcess': '嵌入式子流程',

  'designer.xmlEditor.generationFailed': '无法从当前流程生成 XML',

  'designer.scriptEditor.placeholder': '// 请输入脚本代码…',

  'designer.localSnapshots.title': '本地快照',

  'designer.localSnapshots.empty': '暂无快照，显式保存流程后会创建快照',

  'designer.localSnapshots.loadFailed': '加载本地快照失败',

  'designer.localSnapshots.restore': '恢复',

  'designer.localSnapshots.restored': '已恢复快照',

  'designer.localSnapshots.restoreFailed': '恢复失败：{{message}}',

  'designer.localSnapshots.snapshotSize': '{{chars}} 个字符',

  'designer.debug.sim.errorNoStart': '流程缺少起点节点',

  'designer.debug.sim.errorStepNotPaused': '只能在暂停状态下单步执行',

  'designer.debug.sim.errorContinueNotPaused': '只能在暂停状态下继续执行',

  'designer.debug.sim.errorNodeNotFound': '节点不存在：{{nodeId}}',

  'designer.debug.sim.errorConcurrentGatewayUnsupported':
    '浏览器预览不执行并行或包容分支，请使用「引擎执行」验证真实分支隔离与并发语义。',

  'designer.debug.sim.errorNoBranchMatched': '网关 {{nodeId}} 没有匹配的出边',

  'designer.debug.sim.errorTriggerEntryUnsupported':
    '浏览器预览无法执行触发入口 {{nodeId}}，请使用「引擎执行」。',

  'designer.debug.sim.errorTimerUnsupported':
    '浏览器预览无法推进定时任务 {{nodeId}}，请使用 Durable Runtime 验证持久化定时语义。',

  'designer.debug.sim.errorLoopUnsupported':
    '浏览器预览无法复现节点 {{nodeId}} 的循环语义，请使用「引擎执行」。',

  'designer.debug.sim.errorCalledProcessUnsupported':
    '浏览器预览无法执行节点 {{nodeId}} 调用的子流程，请使用「引擎执行」。',

  'designer.debug.sim.errorEmbeddedProcessUnsupported':
    '浏览器预览无法复现节点 {{nodeId}} 的嵌入式子流程语义，请使用「引擎执行」。',

  'designer.debug.sim.errorExpressionEvaluationFailed':
    '浏览器预览无法安全求值 {{elementId}} 上的表达式',

  'designer.debug.sim.errorDeadEnd': '执行停在没有出边的非结束节点 {{nodeId}}',

  'designer.node.bpmn.start': '开始',

  'designer.node.bpmn.end': '结束',

  'designer.node.bpmn.serviceTask': '服务任务',

  'designer.node.bpmn.scriptTask': '脚本任务',

  'designer.node.bpmn.receiveTask': '接收任务',

  'designer.node.bpmn.exclusiveGateway': '排他网关',

  'designer.node.bpmn.parallelGateway': '并行网关',

  'designer.node.bpmn.inclusiveGateway': '包容网关',

  'designer.node.bpmn.callActivity': '调用活动',

  'designer.node.bpmn.subProcess': '嵌入式子流程',

  'designer.palette.dndFailed': '画布拖放初始化失败',

  'designer.props.common.timeoutPlaceholder': 'PT0S（不超时）',

  'designer.props.common.invocationTimeoutPlaceholder': '例如 PT2M',

  'designer.props.common.attemptTimeoutPlaceholder': '例如 PT30S',

  'designer.props.ph.varName': '例如: order',

  'designer.props.ph.javaType': 'java.lang.String',

  'designer.props.ph.mappingReference': '流程变量',

  'designer.props.ph.defaultValue': '字面量',

  'designer.props.ph.description': '可选：映射说明',

  'designer.props.ph.javaClass': 'com.example.OrderService',

  'designer.props.ph.springExpression': '${orderService.process(order)}',

  'designer.props.ph.condition': '例如: amount > 1000',

  'designer.props.ph.loopWhile': '例如: counter < 10 && !cancelled',

  'designer.props.ph.loopCollection': '例如: orderList',

  'designer.props.ph.loopElement': '例如: order',

  'designer.props.ph.loopElementClass': '例如: com.example.model.Order',

  'designer.props.ph.loopIteration': '例如: iteration',

  'designer.props.ph.loopIndex': '例如: index',

  'designer.props.ph.defaultProcess': 'Flow_default',

  'designer.props.ph.messageId': 'message_order_confirmed',

  'designer.props.ph.callActivity': 'order.approval.flow',

  'designer.props.ph.script': '// 脚本',

  'designer.props.ph.processCode': '例如: user.approval.flow',

  'designer.props.ph.eventName': '例如: approval.completed',

  'designer.props.ph.methodName': 'processOrder',

  'designer.props.ph.methodNotify': 'notify',

  'designer.props.ph.methodExecute': 'execute',

  'designer.props.ph.beanName': 'orderService',

  'designer.props.ph.beanNotify': 'notifyService',

  'designer.props.ph.scriptLanguage': 'java 或 qlexpress',

  'designer.props.ph.scriptSource': 'return value + 1;',

  'designer.props.section.scriptConfig': '脚本配置',

  'designer.props.section.messageConfig': '消息配置',

  'designer.props.section.callConfig': '调用配置',

  'designer.props.section.execConfig': '执行配置',

  'designer.props.section.whileConfig': '条件循环配置',

  'designer.props.section.foreachConfig': '集合遍历配置',

  'designer.props.section.loopBody': '循环体',

  'designer.props.section.edgeConditionNote': '出边条件说明',

  'designer.props.table.emptyActionVars': '暂无变量参数',

  'designer.props.node.parallelGateway.title': 'AND 网关（并行网关）',

  'designer.props.node.waitTask.triggerTip':
    'Tip: 通过 engine.trigger(ProcessDefinition.classpath("processCode", "flows/process.bpm"), ProcessTrigger.at(node.id), data) 从该入口启动一次新执行',

  'designer.props.node.loop.whileExampleTitle': '// 条件循环示例',

  'designer.props.node.loop.forEachExampleTitle': '// 集合遍历示例',

  'designer.props.node.loop.controlTitle': '循环控制',

  'designer.props.node.loop.controlSupported': '支持的控制语句：',

  'designer.props.node.loop.breakDesc': '— 立即退出循环',

  'designer.props.node.loop.continueDesc': '— 跳过当前迭代，继续下一次循环',

  'designer.props.node.loop.limitBehaviorHint':
    '达到最大迭代次数后条件若仍为 true，执行将失败；循环不会被静默截断',

  'designer.props.node.scriptTask.scriptFormat': '脚本格式 (scriptFormat)',

  'designer.props.node.scriptTask.script': '脚本内容 (script)',

  'designer.props.node.receiveTask.messageId': '消息 ID (messageRef)',

  'designer.props.node.receiveTask.messageHelp':
    '引用顶层 BPMN message 定义；缺失时 Workbench 会自动创建',

  'designer.props.node.receiveTask.eventName': '运行时事件名',

  'designer.props.node.receiveTask.eventNameHelp':
    'ProcessTrigger.event 必须等于该 message.name；修改共享消息会影响所有引用它的接收任务',

  'designer.props.node.receiveTask.eventNamePlaceholder': 'payment.received',

  'designer.props.node.callActivity.calledElement': '被调用元素 (calledElement)',

  'designer.props.node.callActivity.calledElementHelp': '被调用的流程 code 或 ID',

  'designer.props.processCall.targetType': '目标类型',

  'designer.props.processCall.targetType.classpath': 'Classpath',

  'designer.props.processCall.targetType.version': '精确版本',

  'designer.props.processCall.classpath': 'Classpath 路径',

  'designer.props.processCall.classpathPlaceholder': '例如 flows/payment.bpmn',

  'designer.props.processCall.version': '子流程固定版本',

  'designer.props.processCall.versionPlaceholder': '例如 2026.07.29-1',

  'designer.actions.selectNodeToCopy': '请先选择要复制的节点',

  'designer.actions.unsavedLeaveTitle': '未保存的更改',

  'designer.actions.unsavedLeaveContent': '您有未保存的更改，确定要离开吗？',

  'designer.actions.saveAndLeave': '保存并离开',

  'designer.actions.leaveWithoutSaving': '直接离开',

  'designer.actions.noProcessToExport': '没有可导出的流程',

  'designer.actions.exportXmlSuccess': 'XML 已导出',

  'designer.actions.exportXmlFailed': '导出 XML 失败',

  'designer.actions.importXmlSuccess': 'XML 已导入',

  'designer.actions.importXmlFailed': '导入失败：{{message}}',

  'designer.actions.importXmlInvalid': '请检查 XML 格式',

  'designer.actions.canvasNotReady': '画布未初始化',

  'designer.actions.duplicateSuccess': '副本已创建，请在流程列表中打开',

  'designer.actions.duplicateFailed': '创建副本失败',

  'designer.actions.duplicateSuffix': '（副本）',

  'designer.actions.deleteFailed': '删除失败',

  'designer.actions.deleteSuccess': '流程已删除',

  'designer.actions.xmlFormatError': 'XML 格式错误：{{message}}',

  'designer.actions.xmlParseFailed': '解析失败',

  'designer.actions.xmlEditorTitle': 'XML 源码编辑器 — {{name}}',

  'designer.actions.xmlEditorTitleDefault': 'XML 源码编辑器',

  'designer.actions.exportImageFailed': '导出图片失败',

  'designer.actions.exportImageSuccess': '已导出 PNG 和 SVG 图片',

  'designer.actions.duplicateOperateUnavailable': '运维流程不支持复制副本',

  'designer.flow.defaultName': '新建{{type}}流程',

  'designer.flow.importedName': '导入的流程',

  'designer.flow.exampleName': '示例流程',

  'designer.header.backToOperate': '返回流程管理',

  'designer.header.operate': '流程管理',

  'designer.warnings.title': '导入警告',

  'designer.xmlParse.warning.NODE_PARSE_WARNING': '无法解析节点（{{location}}）：{{message}}',

  'designer.xmlParse.warning.CONNECTION_PARSE_WARNING': '无法解析连线（{{location}}）：{{message}}',

  'designer.xmlParse.warning.INVALID_SOURCE_REF': '连线源节点无效（{{location}}）：{{message}}',

  'designer.xmlParse.warning.INVALID_TARGET_REF': '连线目标节点无效（{{location}}）：{{message}}',

  'designer.xmlParse.warning.SCHEMA_VALIDATION_WARNING':
    '结构校验警告（{{location}}）：{{message}}',

  'designer.warnings.dismiss': '关闭',

  'designer.split.canvasStaleHint':
    '画布有未同步的更改。编辑 XML 后请点击「应用」，或切换标签页刷新预览。',

  'designer.properties.selectNode': '请选择一个节点',

  'designer.properties.selectNodeHint': '点击画布中的节点查看和编辑属性',

  'designer.properties.selectEdge': '请选择一个连接线',

  'designer.properties.selectEdgeHint': '点击画布中的连接线查看和编辑属性',

  'designer.properties.updateFailed': '更新属性失败',

  'designer.properties.generalTab': '通用',

  'designer.edge.title': '连接线属性',

  'designer.edge.name': '连接名称',

  'designer.edge.nameTooltip': '用于标识连接线，如：同意、拒绝、默认等',

  'designer.edge.namePlaceholder': '请输入连接名称',

  'designer.edge.condition': '条件表达式',

  'designer.edge.advancedEdit': '高级编辑',

  'designer.edge.expressionTooltip': '排他/包容网关的出口需要配置条件表达式',

  'designer.edge.expressionHelp': '使用无副作用的 Java 布尔表达式，例如：amount > 1000',

  'designer.edge.expressionPlaceholder': '例如: amount > 1000',

  'designer.edge.sourceNode': '源节点',

  'designer.edge.targetNode': '目标节点',

  'designer.variableManager.title': '流程变量管理',

  'designer.variableManager.add': '添加变量',

  'designer.variableManager.edit': '编辑变量',

  'designer.variableManager.empty': '暂无变量，点击「添加变量」创建',

  'designer.variableManager.tip':
    '变量对应 .bpm 文件中的 &lt;var&gt; 元素，dataType 需要填写完整的 Java 类名，例如 java.lang.String',

  'designer.variableManager.col.name': '变量名',

  'designer.variableManager.col.dataType': '数据类型',

  'designer.variableManager.col.direction': '方向',

  'designer.variableManager.col.defaultValue': '默认值',

  'designer.variableManager.col.description': '描述',

  'designer.variableManager.col.actions': '操作',

  'designer.variableManager.dir.param': '入参',

  'designer.variableManager.dir.return': '返回',

  'designer.variableManager.dir.inner': '内部',

  'designer.variableManager.deleted': '变量已删除',

  'designer.variableManager.updated': '变量已更新',

  'designer.variableManager.added': '变量已添加',

  'designer.variableManager.deleteConfirm': '确认删除？',

  'designer.variableManager.field.name': '变量名',

  'designer.variableManager.field.nameRequired': '请输入变量名',

  'designer.variableManager.field.nameDuplicate': '变量名必须唯一',

  'designer.variableManager.field.nameInvalid': '请输入合法的 Java 标识符',

  'designer.variableManager.field.nameReserved': '该前缀由 CompileFlow 保留',

  'designer.variableManager.field.namePlaceholder': '例如: orderData',

  'designer.variableManager.field.dataType': '数据类型 (Java 类名)',

  'designer.variableManager.field.dataTypeRequired': '请输入 Java 类名',

  'designer.variableManager.field.dataTypePlaceholder':
    '例如: java.lang.String 或 com.example.OrderData',

  'designer.variableManager.field.direction': '方向',

  'designer.variableManager.field.description': '描述',

  'designer.variableManager.field.descriptionPlaceholder': '可选：变量描述',

  'designer.variableManager.field.defaultValue': '默认值',

  'designer.variableManager.field.defaultValuePlaceholder': '按声明数据类型解析的字面量',

  'designer.nodeSearch.title': '搜索节点',

  'designer.nodeSearch.total': '（共 {{count}} 个节点）',

  'designer.nodeSearch.placeholder': '输入节点名称、ID或类型进行搜索…',

  'designer.nodeSearch.noResults': '未找到匹配的节点',

  'designer.nodeSearch.startHint': '请输入关键词开始搜索',

  'designer.nodeSearch.startSubhint': '支持按节点名称、ID或类型搜索',

  'designer.nodeSearch.position': '位置',

  'designer.nodeSearch.nodeId': 'ID',

  'designer.nodeSearch.tipsTitle': '搜索提示：',

  'designer.nodeSearch.tip1': '支持模糊搜索，不区分大小写',

  'designer.nodeSearch.tip2': '点击搜索结果可快速定位到节点',

  'designer.nodeSearch.tip3': '快捷键：Ctrl/Cmd + F 打开搜索',

  'designer.nodeSearch.tip4': '快捷键：Esc 关闭搜索',

  'designer.nodeSearch.type.start': '开始',

  'designer.nodeSearch.type.end': '结束',

  'designer.nodeSearch.type.autoTask': '自动任务',

  'designer.nodeSearch.type.scriptTask': '脚本任务',

  'designer.nodeSearch.type.exclusive': '排他网关',

  'designer.nodeSearch.type.parallel': '并行网关',

  'designer.nodeSearch.type.inclusive': '包容网关',

  'designer.nodeSearch.type.subBpm': '内嵌 BPM',

  'designer.nodeSearch.type.while': '条件循环',

  'designer.nodeSearch.type.foreach': '集合遍历',

  'designer.nodeSearch.type.bpmCall': 'BPM 调用',

  'designer.nodeSearch.type.waitTask': '等待任务',

  'designer.nodeSearch.type.waitEventTask': '等待事件',

  'designer.nodeSearch.type.timerTask': '定时任务',

  'designer.nodeSearch.type.break': '中断循环',

  'designer.nodeSearch.type.continue': '继续循环',

  'designer.nodeSearch.type.note': '注释',

  'designer.errorBoundary.title': '设计器出现错误',

  'designer.errorBoundary.unknown': '发生了一个未知错误',

  'designer.errorBoundary.devInfo': '开发调试信息',

  'designer.errorBoundary.retry': '重试',

  'designer.errorBoundary.reload': '刷新页面',

  'designer.loading.default': '加载中…',

  'designer.loading.suspense': '加载组件中…',

  'designer.loading.contextMenu': '加载菜单中…',

  'designer.loading.rightPanel': '加载面板中…',

  'designer.loading.monaco': '加载代码编辑器…',

  'designer.flowInit.exampleMissingXml': '示例缺少可导入的流程 XML',

  'designer.flowInit.exampleLoaded': '已加载示例: {{name}}',

  'designer.flowInit.exampleLoadFailed': '加载示例失败：{{message}}',

  'designer.flowInit.exampleCheckData': '请检查示例数据',

  'designer.flowInit.flowNotFound': '流程不存在',

  'designer.flowInit.templateNotFound': '模板不存在: {{id}}',

  'designer.flowInit.templateCreated': '已从模板「{{name}}」创建流程',

  'designer.flowInit.templateFrom': '基于{{name}}',

  'designer.flowInit.templateLoadFailed': '模板加载失败',

  'designer.flowInit.createFailed': '创建流程失败',

  'designer.flowInit.operateLoaded': '已加载运维流程: {{name}}',

  'designer.flowInit.operateLoadFailed': '加载运维流程失败：{{message}}',

  'designer.flowInit.operateLoadFailedGeneric': '流程加载失败',

  'designer.flowInit.errorTitle': '无法打开设计器',

  'designer.flowInit.invalidEntry': '设计器链接不完整或无效。',

  'designer.flowInit.errorGeneric': '流程加载失败，请重试或返回工作区。',

  'designer.flowInit.retry': '重试',

  'designer.flowInit.backToWorkspace': '返回工作区',

  'designer.condition.title': '条件表达式编辑器',

  'designer.condition.tab.editor': '编辑器',

  'designer.condition.tab.templates': '模板',

  'designer.condition.tab.help': '帮助',

  'designer.condition.content': '表达式内容',

  'designer.condition.variables': '可用变量',

  'designer.condition.operators': '运算符',

  'designer.condition.validation.directMutation':
    '条件必须无副作用；不允许直接修改状态的运算符“{{operator}}”',

  'designer.condition.help.syntaxTitle': '表达式语法',

  'designer.condition.help.syntax1': 'BPMN 条件直接填写 <code>Java 布尔表达式</code>正文',

  'designer.condition.help.syntax2': 'TBBPM 条件同样直接填写 <code>Java 布尔表达式</code>正文',

  'designer.condition.help.syntax3': '条件统一使用无副作用的 Java 布尔表达式',

  'designer.condition.help.examplesTitle': '示例',

  'designer.condition.help.notesTitle': '注意事项',

  'designer.condition.help.note1': '表达式必须返回boolean值',

  'designer.condition.help.note2': '变量名区分大小写',

  'designer.condition.help.note3':
    '字符串使用双引号，并采用 <code>"READY".equals(status)</code> 这类 null-safe 值比较',

  'designer.condition.help.note4': '浏览器只支持安全子集，最终以服务端 javac 编译和执行为准',

  'designer.condition.tpl.compare': '数值比较',

  'designer.condition.tpl.compare.amountGt': '金额大于1000',

  'designer.condition.tpl.compare.qtyRange': '数量在范围内',

  'designer.condition.tpl.compare.priceNotEmpty': '价格不为空',

  'designer.condition.tpl.string': '字符串判断',

  'designer.condition.tpl.string.statusPending': '状态等于待审核',

  'designer.condition.tpl.string.vipUser': '类型为VIP用户',

  'designer.condition.tpl.string.nameContains': '名称包含关键字',

  'designer.condition.tpl.logic': '布尔逻辑',

  'designer.condition.tpl.logic.approvedPaid': '已审核且已支付',

  'designer.condition.tpl.logic.urgentOrHigh': '紧急或高优先级',

  'designer.condition.tpl.logic.notCancelled': '未取消',

  'designer.condition.tpl.collection': '集合操作',

  'designer.condition.tpl.collection.itemsNotEmpty': '订单项不为空',

  'designer.condition.tpl.collection.listGt3': '数组长度大于3',

  'designer.condition.tpl.collection.tagsContains': '包含指定元素',

  'designer.shortcuts.copyNode': '复制节点',

  'designer.shortcuts.pasteNode': '粘贴节点',

  'designer.shortcuts.deleteNode': '删除选中节点',

  'designer.shortcuts.searchNodes': '搜索节点',

  'designer.shortcuts.help': '快捷键帮助',

  'designer.loading.propertiesPanel': '加载属性面板...',

  'designer.clipboard.pasteSuffix': '_副本',

  'designer.expr.modeVisual': '可视化',

  'designer.expr.modeText': '文本',

  'designer.expr.insertVariable': '插入变量:',

  'designer.expr.selectVariable': '选择变量...',

  'designer.expr.insertOperator': '插入操作符:',

  'designer.expr.label': '表达式:',

  'designer.expr.placeholder': "请输入表达式，例如: variable > 100 && status == 'active'",

  'designer.expr.panelFunctions': '内置函数参考',

  'designer.expr.panelTemplates': '常用表达式模板',

  'designer.expr.apply': '应用',

  'designer.expr.syntaxHint': '语法提示',

  'designer.expr.syntaxTip1': '变量名使用字母、数字、下划线',

  'designer.expr.syntaxTip2': '字符串使用双引号或单引号',

  'designer.expr.syntaxTip3': 'Guard 使用无副作用的 Java 表达式',

  'designer.expr.syntaxTip4': '按 Ctrl+Space 可触发自动补全（文本模式）',

  'designer.expr.validation.empty': '表达式不能为空',

  'designer.expr.validation.parens': '括号不匹配',

  'designer.expr.validation.illegal': '包含非法字符',

  'designer.expr.validation.ok': '表达式语法正确',

  'designer.expr.op.eq': '等于',

  'designer.expr.op.ne': '不等于',

  'designer.expr.op.gt': '大于',

  'designer.expr.op.lt': '小于',

  'designer.expr.op.gte': '大于等于',

  'designer.expr.op.lte': '小于等于',

  'designer.expr.op.and': '且',

  'designer.expr.op.or': '或',

  'designer.expr.op.not': '非',

  'designer.expr.op.contains': '包含',

  'designer.expr.op.startsWith': '以...开头',

  'designer.expr.op.endsWith': '以...结尾',

  'designer.expr.op.isNull': '为空',

  'designer.expr.op.isNotNull': '不为空',

  'designer.expr.cat.compare': '比较',

  'designer.expr.cat.logic': '逻辑',

  'designer.expr.cat.string': '字符串',

  'designer.expr.cat.null': '判空',

  'designer.expr.fn.isEmpty': '判断字符串是否为空',

  'designer.expr.fn.isNotEmpty': '判断字符串是否非空',

  'designer.expr.fn.contains': '判断是否包含子串',

  'designer.expr.fn.length': '获取字符串长度',

  'designer.expr.fn.toUpperCase': '转换为大写',

  'designer.expr.fn.toLowerCase': '转换为小写',

  'designer.expr.fn.parseInt': '转换为整数',

  'designer.expr.fn.parseDouble': '转换为浮点数',

  'designer.expr.tpl.strEq': '字符串相等',

  'designer.expr.tpl.strEqDesc': '判断字符串相等',

  'designer.expr.tpl.numCmp': '数值比较',

  'designer.expr.tpl.numCmpDesc': '判断数值大小',

  'designer.expr.tpl.range': '范围判断',

  'designer.expr.tpl.rangeDesc': '判断是否在范围内',

  'designer.expr.tpl.notEmpty': '非空判断',

  'designer.expr.tpl.notEmptyDesc': '判断非空且非空字符串',

  'designer.expr.tpl.contains': '包含判断',

  'designer.expr.tpl.containsDesc': '判断是否包含关键字',

  'designer.expr.tpl.multiOr': '多条件或',

  'designer.expr.tpl.multiOrDesc': '多个值的或关系',

  'designer.shortcutsModal.title': '快捷键参考',

  'designer.shortcutsModal.searchPlaceholder': '搜索快捷键或功能...',

  'designer.shortcutsModal.col.category': '分类',

  'designer.shortcutsModal.col.shortcut': '快捷键',

  'designer.shortcutsModal.col.description': '说明',

  'designer.shortcutsModal.tipTitle': '提示：',

  'designer.shortcutsModal.tipMac': '在 Mac 上，使用 Cmd 代替 Ctrl',

  'designer.shortcutsModal.tipConflict': '某些快捷键可能与浏览器或操作系统快捷键冲突',

  'designer.shortcutsModal.tipF1': '按 F1 随时打开此帮助',

  'designer.shortcutsModal.tipHelp': '按 Ctrl+/ 随时打开此帮助',

  'designer.shortcuts.cat.file': '文件操作',

  'designer.shortcuts.cat.edit': '编辑操作',

  'designer.shortcuts.cat.canvas': '画布操作',

  'designer.shortcuts.cat.node': '节点操作',

  'designer.shortcuts.cat.nav': '查找导航',

  'designer.shortcuts.cat.debug': '验证调试',

  'designer.shortcuts.cat.other': '其他',

  'designer.shortcuts.item.save': '保存流程',

  'designer.shortcuts.item.import': '导入XML文件',

  'designer.shortcuts.item.export': '导出XML文件',

  'designer.shortcuts.item.undo': '撤销上一步操作',

  'designer.shortcuts.item.redo': '重做上一步操作',

  'designer.shortcuts.item.copy': '复制选中节点',

  'designer.shortcuts.item.paste': '粘贴节点',

  'designer.shortcuts.item.delete': '删除选中节点',

  'designer.shortcuts.item.selectAll': '全选所有节点',

  'designer.shortcuts.item.deselect': '取消选择',

  'designer.shortcuts.item.zoomIn': '放大画布',

  'designer.shortcuts.item.zoomOut': '缩小画布',

  'designer.shortcuts.item.zoomReset': '重置缩放',

  'designer.shortcuts.item.zoomFit': '适应画布',

  'designer.shortcuts.item.pan': '平移画布',

  'designer.shortcuts.item.rubberband': '框选节点',

  'designer.shortcuts.item.nodeProperties': '编辑节点属性',

  'designer.shortcuts.item.nodeConnect': '连接节点',

  'designer.shortcuts.item.nodeMove': '移动节点',

  'designer.shortcuts.item.search': '搜索节点',

  'designer.shortcuts.item.refresh': '刷新画布',

  'designer.shortcuts.item.validate': '验证流程',

  'designer.shortcuts.item.debug': '打开调试器',

  'designer.shortcuts.item.breakpoint': '在选中节点设置断点',

  'designer.shortcuts.item.help': '显示此帮助',

  'designer.shortcuts.item.fullscreen': '切换全屏',

  'designer.help.title': '帮助文档',

  'designer.help.section.quickStart': '快速开始',

  'designer.help.section.nodeTypes': '节点类型说明',

  'designer.help.section.faq': '常见问题',

  'designer.help.section.bestPractices': '最佳实践',

  'designer.help.section.links': '相关链接',

  'designer.help.quickStart.step1.title': '第一步：创建流程',

  'designer.help.quickStart.step1.li1': '从左侧工具箱中拖拽「开始」节点到画布',

  'designer.help.quickStart.step1.li2': '添加业务节点（如自动任务、排他网关等）',

  'designer.help.quickStart.step1.li3': '添加「结束」节点',

  'designer.help.quickStart.step2.title': '第二步：连接节点',

  'designer.help.quickStart.step2.li1': '从节点的连接点拖动到另一个节点，创建连接线',

  'designer.help.quickStart.step2.li2': '点击连接线可编辑条件表达式',

  'designer.help.quickStart.step3.title': '第三步：配置属性',

  'designer.help.quickStart.step3.li1': '点击节点，在右侧属性面板编辑节点属性',

  'designer.help.quickStart.step3.li2': '自动任务：配置 Java 方法或 Spring Bean Action',

  'designer.help.quickStart.step3.li3': '脚本任务：选择脚本语言并编写源码',

  'designer.help.quickStart.step3.li4': '排他网关：在连接线上配置条件表达式',

  'designer.help.quickStart.step4.title': '第四步：验证和调试',

  'designer.help.quickStart.step4.li1': '点击工具栏的「验证」按钮检查流程错误',

  'designer.help.quickStart.step4.li2': '点击「调试」按钮测试流程执行',

  'designer.help.quickStart.step4.li3': '设置断点，单步调试',

  'designer.help.quickStart.step5.title': '第五步：保存和导出',

  'designer.help.quickStart.step5.li1': '点击「保存」按钮保存流程',

  'designer.help.quickStart.step5.li2': '导出 XML 生成流程定义文件',

  'designer.help.quickStart.step5.li3': '导出图片生成 PNG/SVG',

  'designer.help.nodeTypes.flowControl': '流程控制节点',

  'designer.help.nodeTypes.start': '开始 — 流程起点，每个流程必须有一个开始节点',

  'designer.help.nodeTypes.end': '结束 — 流程终点，每个流程必须恰好有一个结束节点',

  'designer.help.nodeTypes.tasks': '任务节点',

  'designer.help.nodeTypes.autoTask': '自动任务 — 调用 Java 方法或 Spring Bean',

  'designer.help.nodeTypes.waitTask': '等待任务 — 等待外部事件触发',

  'designer.help.nodeTypes.waitEvent': '等待事件 — 等待特定事件发生',

  'designer.help.nodeTypes.timerTask': '定时任务 — Durable 等待固定时长或指定时刻',

  'designer.help.nodeTypes.scriptTask': '脚本任务 — 编写流程定义内的内联代码',

  'designer.help.nodeTypes.gateways': '网关节点',

  'designer.help.nodeTypes.exclusive': '排他网关 - 根据条件选择一个分支（XOR）',

  'designer.help.nodeTypes.parallel': '并行网关 — 同时执行多个分支（AND）',

  'designer.help.nodeTypes.inclusive': '包容网关 — 执行满足条件的所有分支（OR）',

  'designer.help.nodeTypes.subprocess': '子流程节点',

  'designer.help.nodeTypes.subBpm': '内嵌 BPM — 在当前 BPM 内定义嵌套作用域',

  'designer.help.nodeTypes.bpmCall': 'BPM 调用 — 调用另一个 BPM 定义',

  'designer.help.nodeTypes.while': 'While 循环 — Java 条件为 true 时重复执行',

  'designer.help.nodeTypes.foreach': 'Foreach 循环 — 顺序遍历集合或使用 Durable 并行模式',

  'designer.help.nodeTypes.loopControl': '循环控制节点',

  'designer.help.nodeTypes.continue': '继续循环 — 类似编程语言的 continue',

  'designer.help.nodeTypes.break': '中断循环 — 类似编程语言的 break',

  'designer.help.nodeTypes.other': '其他节点',

  'designer.help.nodeTypes.note': '注释 — 添加说明文字，不影响流程执行',

  'designer.help.faq.branch.q': 'Q: 如何创建条件分支？',

  'designer.help.faq.branch.a': 'A: 使用排他网关，然后在每个出口连接线上配置条件表达式。',

  'designer.help.faq.branch.example': '示例：连接线1配置 amount > 1000，连接线2配置 amount <= 1000',

  'designer.help.faq.panel.q': 'Q: 节点点击后属性面板无响应怎么办？',

  'designer.help.faq.panel.a': 'A: 尝试以下解决方法：',

  'designer.help.faq.panel.li1': '刷新页面重新加载流程',

  'designer.help.faq.panel.li2': '检查浏览器控制台是否有错误信息',

  'designer.help.faq.panel.li3': '确保节点已成功添加到画布（查看状态栏节点数）',

  'designer.help.faq.variables.q': 'Q: 如何引用流程变量？',

  'designer.help.faq.variables.a': 'A: 在表达式中直接使用变量名即可。',

  'designer.help.faq.variables.example': '示例：amount > 1000 && status == "active"',

  'designer.help.faq.variables.tip': '建议：使用「变量管理」功能预先定义全局变量',

  'designer.help.faq.validation.q': 'Q: 验证提示发现问题如何解决？',

  'designer.help.faq.validation.a': 'A: 点击验证面板中的错误项，查看详细错误信息：',

  'designer.help.faq.validation.li1': '流程必须有开始和结束节点',

  'designer.help.faq.validation.li2': '自动任务必须配置完整的 Action',

  'designer.help.faq.validation.li3': '排他网关必须有至少2个出口，且每个出口配置条件',

  'designer.help.faq.validation.li4': '检查是否有孤立节点（未连接的节点）',

  'designer.help.faq.debug.q': 'Q: 如何使用调试功能？',

  'designer.help.faq.debug.a': 'A: 调试流程步骤：',

  'designer.help.faq.debug.li1': '点击工具栏的「调试」按钮打开调试面板',

  'designer.help.faq.debug.li2': '在「初始变量」中输入JSON格式的变量值',

  'designer.help.faq.debug.li3': '（可选）在节点上设置断点',

  'designer.help.faq.debug.li4': '点击「开始」按钮执行流程',

  'designer.help.faq.debug.li5': '观察「变量」和「执行日志」面板',

  'designer.help.faq.rubberband.q': 'Q: 框选功能如何使用？',

  'designer.help.faq.rubberband.a': 'A: 按住 Shift 键，然后在画布上拖动鼠标即可框选多个节点。',

  'designer.help.faq.rubberband.tip': '提示：框选后可批量移动、复制或删除节点',

  'designer.help.practices.naming.title': '命名规范',

  'designer.help.practices.naming.li1': '节点名称：使用动词+名词，如「验证订单」、「发送邮件」',

  'designer.help.practices.naming.li2': '变量名称：使用驼峰命名法，如 orderAmount、userName',

  'designer.help.practices.naming.li3': '流程代码：使用小写字母+下划线，如 order_process',

  'designer.help.practices.design.title': '流程设计原则',

  'designer.help.practices.design.li1': '单一职责：一个流程只做一件事',

  'designer.help.practices.design.li2': '避免过度复杂：超过20个节点考虑拆分为子流程',

  'designer.help.practices.design.li3': '错误处理：为关键节点添加异常分支',

  'designer.help.practices.design.li4': '可读性：使用注释节点说明复杂逻辑',

  'designer.help.practices.performance.title': '性能优化',

  'designer.help.practices.performance.li1': '避免在循环中调用耗时操作',

  'designer.help.practices.performance.li2': '使用并行网关提高并发执行效率',

  'designer.help.practices.performance.li3': '合理使用子流程，避免流程定义过大',

  'designer.help.practices.performance.li4': '脚本任务中避免复杂计算，优先使用Java方法',

  'designer.help.practices.security.title': '安全建议',

  'designer.help.practices.security.li1': '敏感信息不要硬编码在脚本中',

  'designer.help.practices.security.li2': '使用变量或配置文件管理敏感数据',

  'designer.help.practices.security.li3': '表达式中避免使用 eval 等危险函数',

  'designer.help.practices.security.li4': '定期备份流程定义',

  'designer.help.links.github': 'CompileFlow GitHub 仓库',

  'designer.help.links.githubDesc': '— 查看源代码和文档',

  'designer.help.links.docsZh': 'CompileFlow 中文文档',

  'designer.help.links.docsZhDesc': '— 完整的使用指南',

  'designer.help.links.nodeSupport': '节点支持列表',

  'designer.help.links.nodeSupportDesc': '— TBBPM 节点详细说明',

  'designer.help.links.issues': '问题反馈',

  'designer.help.links.issuesDesc': '— 报告 Bug 或提出建议',

  // 工作台消息

  'workspace.exportSuccess': '导出成功',

  'workspace.exportFailed': '导出失败',

  'workspace.importSuccess': '已导入 {{count}} 个流程',

  'workspace.importFailed': '导入失败，请检查文件格式',
} as const

export default zhAuthoring
