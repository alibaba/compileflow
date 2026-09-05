const zhLearn = {
  'examples.title': '示例库',

  'examples.subtitle': '可运行示例带你掌握 CompileFlow——从入门到高阶模式。',

  'examples.difficulty': '难度',

  'examples.category': '分类',

  'examples.search': '搜索示例…',

  'examples.total': '共 {{count}} 个示例',

  'examples.notFound': '没有匹配的示例',

  'examples.startLearning': '开始学习 →',

  'examples.viewDetail': '查看详情',

  'examples.learnWhat': '你将学到',

  // 难度等级

  'level.beginner': '入门',

  'level.basic': '基础',

  'level.intermediate': '进阶',

  'level.advanced': '高级',

  // 分类

  'category.basics': '基础',

  'category.business': '业务场景',

  'category.approval': '审批流',

  // 示例详情

  'detail.back': '返回示例列表',

  'detail.duration': '{{time}}',

  'detail.durationMinutes': '{{count}} 分钟',

  'detail.difficulty': '难度：{{stars}}',

  'detail.difficultyValue': '难度 {{value}} 星（满分 5 星）',

  'detail.format': '格式：{{desc}}',

  'detail.tab.overview': '概览',

  'detail.tab.code': '代码',

  'detail.tab.execute': '执行',

  'detail.tab.docs': '说明',

  'detail.whatYouWillLearn': '你将学到',

  'detail.keyConcepts': '核心概念',
  'detail.explanation': '原理说明',
  'detail.nextSteps': '下一步',

  'detail.noCode': '暂无代码',

  // 执行

  'category.beginner': '基础入门',

  'category.advanced': '高级特性',

  // 排序

  'examples.modelType': '流程类型',

  'examples.sortBy': '排序',

  // 详情

  'detail.noDocs': '暂无说明文档',

  // 日志扩展

  'detail.openInDesigner': '在设计器中打开',

  'detail.openInDesignerSuccess': '已在 {{type}} 设计器中打开',

  'detail.quickActions': '快捷操作',

  'detail.downloadSuccess': '已下载「{{name}}」',

  'learning.progress': '学习进度',

  'learning.totalProgress': '总进度',

  'learning.completed': '已完成',

  'learning.markComplete': '标为已完成',

  'learning.congrats': '完成了！继续挑战下一个示例吧。',

  // 目录

  'toc.title': '本页目录',

  // 相关示例

  'examples.highlyRecommended': '推荐',

  'examples.viewAll': '查看全部示例 →',

  // 相关示例

  'examples.related': '相关示例',

  'examples.noRelated': '暂无相关示例',

  'examples.totalCount': '示例总数',

  // 示例导航

  'exampleNav.prev': '上一个',

  'exampleNav.next': '下一个',

  'exampleNav.progress': '示例 {{current}} / {{total}}',

  'exampleNav.navigationLabel': '示例导航',

  'exampleContent.learn.tbbpm.greeting.name': 'TBBPM 问候流程',

  'exampleContent.learn.tbbpm.greeting.description': '使用自包含 QL 脚本动作生成返回值。',

  'exampleContent.learn.tbbpm.greeting.overview':
    '这是最小可用的 TBBPM 定义：一个开始节点、一个可执行任务和一个结束节点。它不依赖应用侧类，因此可以直接对内置 QL 脚本执行预检和运行。',

  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.0': '声明流程输入变量和返回变量',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.1': '连接开始、任务与结束节点',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.2': '将动作结果映射到流程上下文',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.0': 'TBBPM 文档',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.1': '脚本动作',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.2': '返回变量',
  'exampleContent.learn.tbbpm.greeting.explanation':
    '未传入 `name` 时会使用声明的默认值 `World`。QL 脚本计算出字符串，再由动作内部的变量映射把结果写入根流程的 `message` 输出。',
  'exampleContent.learn.tbbpm.greeting.nextSteps':
    '添加一个带默认值的字符串输入变量，将它映射到动作中，并生成个性化问候语。',
  'exampleContent.learn.tbbpm.greeting.documentation':
    '参阅 [TBBPM 规范](https://github.com/alibaba/compileflow/blob/master/docs/specs/tbbpm-specification.en.md) 和 [节点支持矩阵](https://github.com/alibaba/compileflow/blob/master/docs/en/node-support.md)。',

  'exampleContent.learn.bpmn.routing.name': 'BPMN 金额路由',

  'exampleContent.learn.bpmn.routing.description': '通过排他网关按金额选择路径并返回路由结果。',

  'exampleContent.learn.bpmn.routing.overview':
    '本示例只使用 CompileFlow 支持的 BPMN 子集。它描述的是同步决策，而不是持久化人工任务；人工任务的状态与生命周期应由外部任务系统负责。',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.0': '在 BPMN 扩展元素中声明 CompileFlow 变量',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.1': '使用互斥的顺序流条件',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.2': '为 BPMN 服务任务附加可执行动作',
  'exampleContent.learn.bpmn.routing.keyConcepts.0': 'BPMN 支持子集',
  'exampleContent.learn.bpmn.routing.keyConcepts.1': '排他网关',
  'exampleContent.learn.bpmn.routing.keyConcepts.2': 'cf:action 动作',
  'exampleContent.learn.bpmn.routing.explanation':
    '默认金额为 150，因此空输入会选择 `manual-review`。金额低于 100 时选择 `automatic`，两个分支最终汇聚到同一个结束事件。',
  'exampleContent.learn.bpmn.routing.nextSteps':
    '使用 `{"amount": 50}` 运行流程并比较返回的 `route`，再通过显式默认顺序流添加第三条策略。',
  'exampleContent.learn.bpmn.routing.documentation':
    '参阅 [流程模型](https://github.com/alibaba/compileflow/blob/master/docs/architecture/07-PROCESS_MODEL.en.md)，了解 BPMN 扩展位置和节点支持规则。',

  'exampleContent.learn.tbbpm.parallel.name': 'TBBPM 并行计算',

  'exampleContent.learn.tbbpm.parallel.description':
    '以明确的并发能力运行两个独立计算，并在汇聚后返回结果。',

  'exampleContent.learn.tbbpm.parallel.overview':
    '只有当工作和输出彼此独立时才适合使用并行分支。本示例分别写入两个返回变量，并在完成前进行汇聚。',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.0': '建模并行分叉和结构化汇聚',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.1': '将分支输出保存在不同流程变量中',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.2': '使用不依赖应用类的自包含动作',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.0': '并行分叉',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.1': '并行汇聚',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.2': '分支输出',
  'exampleContent.learn.tbbpm.parallel.explanation':
    '分叉通过引擎执行器同时启动两个计算。汇聚节点等待两条路径完成，最终结果映射包含 `leftResult=20` 和 `rightResult=22`。',
  'exampleContent.learn.tbbpm.parallel.nextSteps':
    '将其中一个常量替换为映射输入，并与两个任务顺序执行时的行为进行比较。',
  'exampleContent.learn.tbbpm.parallel.documentation':
    '并发行为参阅[高级特性](https://github.com/alibaba/compileflow/blob/master/docs/zh/advanced-features.md)。',

  'exampleContent.learn.bpmn.script-task.name': 'BPMN 脚本任务',

  'exampleContent.learn.bpmn.script-task.description':
    '在标准 BPMN 脚本任务中编写流程定义自有的内联代码。',

  'exampleContent.learn.bpmn.script-task.overview':
    'BPMN 脚本任务直接拥有内联源码，CompileFlow 扩展描述变量映射与可选执行策略；该示例不依赖任何外部 Bean 或应用类。',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.0': '使用标准元素保持 BPMN 控制流',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.1': '直接在脚本任务中编写 QLExpress',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.2': '把脚本输出映射到流程变量',
  'exampleContent.learn.bpmn.script-task.keyConcepts.0': '脚本任务',
  'exampleContent.learn.bpmn.script-task.keyConcepts.1': '脚本格式',
  'exampleContent.learn.bpmn.script-task.keyConcepts.2': '输出映射',
  'exampleContent.learn.bpmn.script-task.explanation':
    '脚本任务执行 QLExpress，并把 `completed` 写入流程级返回变量 `status`。',
  'exampleContent.learn.bpmn.script-task.nextSteps':
    '添加输入变量并映射到脚本，然后把语言切换为 Java 17 并改写源码。',
  'exampleContent.learn.bpmn.script-task.documentation':
    '参阅 [扩展指南](https://github.com/alibaba/compileflow/blob/master/docs/en/extension-guide.md)，了解支持的动作类型和自定义 Provider。',

  // 全局搜索
} as const

export default zhLearn
