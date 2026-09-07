const zhLearn = {
  'examples.title': '示例库',

  'examples.subtitle': '通过可运行的示例学习 CompileFlow，从基础用法逐步进阶。',

  'examples.difficulty': '难度',

  'examples.category': '分类',

  'examples.search': '搜索示例…',

  'examples.total': '共 {{count}} 个示例',

  'examples.notFound': '没有匹配的示例',

  'examples.startLearning': '打开示例 →',

  'examples.viewDetail': '查看详情',

  'examples.learnWhat': '你将学到',

  // 难度等级

  'level.beginner': '入门',

  'level.basic': '初级',

  'level.intermediate': '中级',

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

  'detail.difficultyValue': '难度：{{value}}/5 星',

  'detail.format': '格式：{{desc}}',

  'detail.tab.overview': '概览',

  'detail.tab.code': '代码',

  'detail.tab.execute': '执行',

  'detail.tab.docs': '说明',

  'detail.whatYouWillLearn': '你将学到',

  'detail.keyConcepts': '核心概念',
  'detail.explanation': '原理说明',
  'detail.nextSteps': '下一步',

  'detail.noCode': '该示例暂无代码',

  // 执行

  'category.beginner': '入门示例',

  'category.advanced': '进阶示例',

  // 排序

  'examples.modelType': '流程类型',

  'examples.sortBy': '排序',

  // 详情

  'detail.noDocs': '该示例暂无说明文档',

  // 日志扩展

  'detail.openInDesigner': '在设计器中打开',

  'detail.openInDesignerSuccess': '已在 {{type}} 设计器中打开',

  'detail.quickActions': '快捷操作',

  'detail.downloadSuccess': '已下载「{{name}}」',

  'learning.progress': '学习进度',

  'learning.totalProgress': '总进度',

  'learning.completed': '已完成',

  'learning.markComplete': '标记为已完成',

  'learning.congrats': '已完成该示例，可以继续学习下一个。',

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

  'exampleNav.progress': '第 {{current}} 个，共 {{total}} 个示例',

  'exampleNav.navigationLabel': '示例导航',

  'exampleContent.learn.tbbpm.greeting.name': 'TBBPM 问候流程',

  'exampleContent.learn.tbbpm.greeting.description': '使用独立的 QLExpress 脚本动作生成问候语。',

  'exampleContent.learn.tbbpm.greeting.overview':
    '这个最小 TBBPM 流程由开始节点、脚本任务和结束节点组成。脚本不依赖应用中的类，可以直接校验并运行。',

  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.0': '声明流程输入变量和返回变量',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.1': '连接开始、任务与结束节点',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.2': '将脚本结果映射到流程返回变量',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.0': 'TBBPM 流程定义',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.1': '脚本动作',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.2': '返回变量',
  'exampleContent.learn.tbbpm.greeting.explanation':
    '未传入 `name` 时，流程使用默认值 `World`。QLExpress 脚本生成问候语，并通过输出映射将结果写入流程返回变量 `message`。',
  'exampleContent.learn.tbbpm.greeting.nextSteps':
    '传入自定义 `name` 运行流程，再修改默认值，对比两次返回的问候语。',
  'exampleContent.learn.tbbpm.greeting.documentation':
    '参阅 [TBBPM 规范](https://github.com/alibaba/compileflow/blob/master/docs/zh/specifications/tbbpm.md) 和 [节点支持矩阵](https://github.com/alibaba/compileflow/blob/master/docs/zh/node-support.md)。',

  'exampleContent.learn.bpmn.routing.name': 'BPMN 金额路由',

  'exampleContent.learn.bpmn.routing.description': '通过排他网关，根据金额选择流程路径。',

  'exampleContent.learn.bpmn.routing.overview':
    '本示例使用 CompileFlow 支持的 BPMN 元素完成一次同步路由决策。`manual-review` 分支只返回路由名称，不会创建或持久化人工任务。',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.0': '在 BPMN 扩展元素中声明 CompileFlow 变量',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.1': '为顺序流设置互斥条件',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.2': '使用脚本任务设置各路径的返回结果',
  'exampleContent.learn.bpmn.routing.keyConcepts.0': 'BPMN 支持子集',
  'exampleContent.learn.bpmn.routing.keyConcepts.1': '排他网关',
  'exampleContent.learn.bpmn.routing.keyConcepts.2': '脚本任务',
  'exampleContent.learn.bpmn.routing.explanation':
    '金额默认为 150，未传入参数时会选择 `manual-review`。金额低于 100 时选择 `automatic`，两条路径最终都到达同一个结束事件。',
  'exampleContent.learn.bpmn.routing.nextSteps':
    '使用 `{"amount": 50}` 运行流程，对比返回的 `route`。然后添加一条明确的默认顺序流，实现第三条路由。',
  'exampleContent.learn.bpmn.routing.documentation':
    '参阅 [流程模型](https://github.com/alibaba/compileflow/blob/master/docs/zh/architecture/process-model.md)，了解 BPMN 扩展元素的位置和节点支持规则。',

  'exampleContent.learn.tbbpm.parallel.name': 'TBBPM 并行计算',

  'exampleContent.learn.tbbpm.parallel.description': '并行执行两个独立计算，并返回两个结果。',

  'exampleContent.learn.tbbpm.parallel.overview':
    '两个分支独立运行，并将结果写入不同的返回变量。汇聚节点等待两个分支完成后再结束流程。',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.0': '创建并行分支并进行汇聚',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.1': '将各分支输出保存到不同的流程变量',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.2': '使用不依赖应用类的脚本动作',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.0': '并行分叉',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.1': '并行汇聚',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.2': '分支输出',
  'exampleContent.learn.tbbpm.parallel.explanation':
    '并行分叉启动两个计算，汇聚节点等待两个分支都执行完成。最终结果中包含 `leftResult=20` 和 `rightResult=22`。',
  'exampleContent.learn.tbbpm.parallel.nextSteps':
    '将其中一个常量替换为输入变量，再将两个任务改为顺序执行，对比运行结果。',
  'exampleContent.learn.tbbpm.parallel.documentation':
    '并发行为参阅[高级特性](https://github.com/alibaba/compileflow/blob/master/docs/zh/advanced-features.md)。',

  'exampleContent.learn.bpmn.script-task.name': 'BPMN 脚本任务',

  'exampleContent.learn.bpmn.script-task.description':
    '在标准 BPMN 脚本任务中直接编写 QLExpress 脚本。',

  'exampleContent.learn.bpmn.script-task.overview':
    '脚本源代码直接保存在 BPMN 脚本任务中，CompileFlow 扩展负责将输出映射到流程变量。本示例不依赖外部 Bean 或应用类。',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.0': '使用标准 BPMN 元素组织流程',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.1': '直接在脚本任务中编写 QLExpress',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.2': '把脚本输出映射到流程变量',
  'exampleContent.learn.bpmn.script-task.keyConcepts.0': '脚本任务',
  'exampleContent.learn.bpmn.script-task.keyConcepts.1': '`scriptFormat` 属性',
  'exampleContent.learn.bpmn.script-task.keyConcepts.2': '输出映射',
  'exampleContent.learn.bpmn.script-task.explanation':
    '脚本任务运行 QLExpress 脚本，并将结果 `completed` 映射到流程返回变量 `status`。',
  'exampleContent.learn.bpmn.script-task.nextSteps':
    '添加一个输入变量并在脚本中使用。然后将脚本语言切换为 Java 17，重写该表达式。',
  'exampleContent.learn.bpmn.script-task.documentation':
    '参阅 [扩展指南](https://github.com/alibaba/compileflow/blob/master/docs/zh/extension-guide.md)，了解支持的动作类型和自定义提供者。',

  // 全局搜索
} as const

export default zhLearn
