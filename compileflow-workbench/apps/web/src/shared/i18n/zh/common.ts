const zhCommon = {
  'common.loading': '加载中…',
  'common.skipToContent': '跳转到主要内容',

  'common.retry': '重试',

  'common.back': '返回',

  'common.next': '下一步',

  'common.prev': '上一步',

  'common.previousPage': '上一页',

  'common.nextPage': '下一页',

  'common.confirm': '确认',

  'common.cancel': '取消',

  'common.save': '保存',

  'common.delete': '删除',

  'common.edit': '编辑',

  'common.view': '查看',

  'common.refresh': '刷新',

  'common.loadMore': '加载更多',

  'common.search': '搜索',

  'common.filter': '筛选',

  'filters.processType': '流程类型',

  'common.clear': '清除',

  'common.total': '共',

  'common.examples': '个示例',

  'common.getStarted': '开始使用',

  'common.open': '打开',

  'common.close': '关闭',

  'common.breadcrumb': '面包屑导航',

  'nav.home': '首页',

  'nav.learn': '学习',

  'nav.learn.examples': '示例库',

  'nav.learn.tutorials': '教程',

  'nav.workspace': '构建',

  'nav.workspace.designer': '流程设计器',

  'nav.workspace.editor': '代码编辑器',

  'nav.workspace.debugger': '调试器',

  'nav.ops': '运维',

  'nav.build': '构建',

  'nav.operate': '运维',

  'nav.ops.processes': '流程管理',

  'nav.ops.deployment': '部署',

  'nav.ops.monitoring': '监控',

  'nav.ops.logs': '日志',

  'nav.learn.exampleDetail': '示例详情',

  'nav.ops.deploymentDetail': '部署详情',

  // 页面标题（追加 “｜CompileFlow Workbench”）

  'pageTitle.home': '学习',

  'pageTitle.learn': '流程示例库',

  'pageTitle.build': '设计与编译',

  'pageTitle.build.workspace': '设计与编译',

  'pageTitle.operate': '发布与运维',

  'pageTitle.operate.processes': '流程管理',

  'pageTitle.operate.deployments': '部署',

  'pageTitle.operate.monitoring': '运行监控',

  'pageTitle.operate.logs': '执行日志',

  'pageTitle.settings': '设置',

  'pageTitle.learn.detail': '示例详情',

  'pageTitle.operate.deployWizard': '新建部署',

  'pageTitle.operate.deploymentDetail': '部署详情',

  'pageTitle.notFound': '页面不存在',

  'pageTitle.serverError': '服务器错误',

  // 应用壳层

  'navigation.main': '主导航',

  'navigation.mainMenu': '主菜单',

  'navigation.home': '返回首页',

  'navigation.github': 'GitHub 仓库',

  'navigation.toggleToEnglish': '切换到英文',

  'navigation.toggleToChinese': '切换到中文',

  'navigation.openMenu': '打开导航菜单',

  'navigation.drawerTitle': 'CompileFlow 工作台',

  'navigation.expandSidebar': '展开侧边栏',

  'navigation.collapseSidebar': '收起侧边栏',

  'navigation.openSidebar': '打开当前模块导航',

  'navigation.closeSidebar': '关闭当前模块导航',

  'sidebar.exampleCategories': '示例分类',

  'sidebar.category.basics': '入门',

  'sidebar.category.business': '业务场景',

  'sidebar.category.advanced': '进阶',

  'sidebar.tutorials': '教程',

  // 应用菜单与设置

  'appMenu.label': '应用菜单',

  'appMenu.settings': '设置',

  'appMenu.documentation': '文档',

  'appMenu.language': '语言',

  'settings.title': '设置',

  'settings.subtitle': '设置工作台偏好并查看构建信息',

  'settings.eyebrow': '偏好设置',

  'settings.preferences': '偏好设置',

  'settings.language': '语言',

  'settings.theme': '主题',

  'settings.theme.light': '浅色',

  'settings.theme.dark': '深色',

  'settings.build': '构建信息',

  'settings.operateMode': '运维模式',

  'settings.buildMode': '构建模式',

  'settings.appVersion': '应用版本',

  'settings.resources': '资源',

  // 首页

  'home.eyebrow': '面向 Java 应用的可视化流程编排',

  'home.title': '让业务流程更清晰、更可靠',

  'home.subtitle':
    '将业务逻辑可视化，支持高性能进程内执行、持久化流程和 Agent 工作流编排，并提供版本化发布与运行监控。',

  'home.start': '创建第一个流程',

  'home.docs': '浏览示例',

  'home.status': '状态',

  'home.modules': '模块',

  'home.version': 'v{{version}} · {{status}}',

  'home.features': '学习、构建与运维',

  'home.featuresDesc': '通过示例学习常用模式，构建自己的流程，并管理流程版本和运行状态。',

  // 功能模块

  'feature.learn.title': '学习',

  'feature.learn.desc': '通过示例学习条件分支、并行执行、流程等待和恢复等常用模式。',

  'feature.learn.action': '浏览示例',

  'feature.workspace.title': '构建',

  'feature.workspace.desc': '可视化设计流程或直接编辑源码，并完成流程定义校验和编译。',

  'feature.workspace.action': '打开工作区',

  'feature.ops.title': '运维',

  'feature.ops.desc': '发布不可变版本、配置灰度流量，并准确识别每次执行所使用的流程版本。',

  'feature.ops.action': '进入运维',

  // 示例列表

  'theme.light': '切换到浅色模式',

  'theme.dark': '切换到深色模式',

  // 流程类型

  'error.loadFailed': '加载失败',

  'error.exampleNotFound': '示例不存在',

  'error.networkError': '网络错误，请重试',

  'error.deleteFailed': '删除失败',

  'error.duplicateFailed': '复制失败',

  'error.deployFailed': '部署失败',

  'error.rollbackFailed': '回滚失败',

  'error.abortFailed': '中止失败',

  'error.requestFailed': '请求失败',

  'error.requestTimeout': '请求超时，请稍后重试',

  'error.networkConnection': '网络错误，请检查连接后重试',

  'error.unknown': '未知错误',

  'error.operationFailed': '操作失败',

  'error.pageRenderTitle': '无法显示此页面',

  'error.pageRenderDescription': '请刷新页面后重试。',

  'error.application': '应用发生错误，请刷新页面后重试。',

  'errorBoundary.title': '页面出错了',

  'errorBoundary.description': '请重试，或返回首页。',

  'errorBoundary.retry': '重试',

  'errorBoundary.home': '返回首页',

  'errorBoundary.details': '错误详情（仅开发环境）',

  'errorBoundary.errorLabel': '错误：',

  'errorBoundary.componentStackLabel': '组件调用栈：',

  // 空状态

  'empty.search.title': '未找到相关内容',

  'empty.search.description': '请调整或清空筛选条件。',

  'empty.data.title': '暂无内容',

  'empty.data.description': '创建第一个流程后即可开始使用。',

  'empty.error.title': '加载失败',

  'empty.error.description': '请稍后重试。',

  // Mock 模式

  'mockBanner.prefix': '演示模式显示本地模拟数据，不代表引擎实际输出。请设置',

  'mockBanner.suffix': '，并通过同源网关连接后端服务。',

  'mockBanner.dismiss': '关闭提示',

  // 状态页

  'notFound.message': '找不到你访问的页面。',

  'notFound.home': '返回首页',

  'serverError.message': '服务器发生错误。',

  'serverError.reload': '刷新页面',

  'serverError.home': '返回首页',

  // Operate模块 - 流程管理

  'common.actions': '操作',

  'common.detail': '查看详情',

  'common.totalItems': '共 {{total}} 条',

  'common.yes': '是',

  'common.no': '否',

  'common.previous': '上一步',

  'common.duplicate': '复制',

  'common.more': '更多',

  // 筛选器

  'error.exportFailed': '导出失败',

  // 流程扩展

  'feedback.helpfulQuestion': '这个示例有帮助吗？',

  'feedback.quickActions': '快捷操作',

  'feedback.helpful': '有帮助',

  'feedback.notHelpful': '没有帮助',

  'feedback.bookmark': '收藏',

  'feedback.share': '分享',

  'feedback.likeSuccess': '已标记为有帮助',

  'feedback.likeCancelled': '已取消“有帮助”反馈',

  'feedback.dislikeSuccess': '已标记为没有帮助',

  'feedback.dislikeCancelled': '已取消“没有帮助”反馈',

  'feedback.bookmarkAdded': '已收藏',

  'feedback.bookmarkRemoved': '已取消收藏',

  'feedback.linkCopied': '链接已复制',

  'feedback.copyFailed': '链接复制失败',

  'feedback.shareSuccess': '已分享',

  'common.download': '下载',

  // 页脚

  'footer.copyright': 'Copyright 2026 Alibaba CompileFlow contributors.',

  'footer.subtitle': '使用 CompileFlow 工作台学习、设计、发布和运维流程。',

  // 工作台

  'search.trigger': '搜索…',

  'search.placeholder': '搜索示例和本地流程…',

  'search.inputLabel': '搜索示例和本地流程',

  'search.dialogTitle': '全局搜索',

  'search.enterHint': '按 Enter 搜索',
  'search.loading': '正在打开全局搜索…',
  'search.indexing': '正在建立搜索索引…',
  'search.sourcesUnavailable': '部分搜索来源暂时不可用，请稍后重试',

  'search.matchedExamples': '匹配的示例',

  'search.matchedProcesses': '匹配的本地流程',

  'search.noMatch': '没有匹配结果，可尝试使用快速导航',

  'search.quickNav': '快速导航',

  'search.submit': '搜索',

  'search.close': '关闭',

  'search.openShortcut': '打开全局搜索（⌘K）',

  'search.quickLink.examplesDesc': '浏览流程示例',

  'search.quickLink.processesDesc': '管理流程定义',

  'search.quickLink.monitoringDesc': '查看实时运行指标',

  'search.quickLink.logsDesc': '搜索执行日志',

  // 流程管理提示
} as const

export default zhCommon
