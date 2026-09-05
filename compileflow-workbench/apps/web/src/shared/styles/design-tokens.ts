const gray = {
  50: '#fafafa', // 最浅背景
  100: '#f5f5f5', // 浅背景
  200: '#f0f0f0', // 工具栏背景
  300: '#d9d9d9', // 边框
  400: '#bfbfbf', // 禁用文本
  500: '#8c8c8c', // 次要文本
  600: '#595959', // 正文
  700: '#434343', // 标题
  800: '#262626', // 深标题
  900: '#1f1f1f', // 最深
} as const

const nodeColors = {
  // 流程控制 - 绿色系（开始）/ 珊瑚红（结束）— secondary triad
  flow: {
    start: '#0f9f6e',
    end: '#fe2857',
  },

  // 任务节点 - Electric blue
  task: {
    main: '#087cfa',
    light: '#3d9bff',
    dark: '#0666cc',
  },

  // 网关节点 - warning orange
  gateway: {
    main: '#fc801d',
    light: '#ff9a4d',
    dark: '#d96610',
  },

  // 子流程 - Iris primary family
  subprocess: {
    main: '#6b57ff',
    light: '#8874ff',
    dark: '#5644e0',
  },

  // 控制节点
  control: {
    break: '#fe2857',
    continue: '#0f9f6e',
  },
} as const

export function getNodeColor(nodeType: string): string {
  const colorMap: Record<string, string> = {
    // 流程控制
    start: nodeColors.flow.start,
    end: nodeColors.flow.end,

    // 任务节点
    autoTask: nodeColors.task.main,
    waitTask: nodeColors.task.main,
    waitEventTask: nodeColors.task.main,
    timerTask: nodeColors.task.main,
    scriptTask: nodeColors.task.main,

    // 网关节点
    exclusive: nodeColors.gateway.main,
    parallel: nodeColors.gateway.main,
    inclusive: nodeColors.gateway.main,

    // 子流程
    subBpm: nodeColors.subprocess.main,
    bpmCall: nodeColors.subprocess.main,
    while: nodeColors.subprocess.main,
    foreach: nodeColors.subprocess.main,

    // 控制节点
    break: nodeColors.control.break,
    continue: nodeColors.control.continue,

    // 其他
    note: gray[500],
  }

  return colorMap[nodeType] || gray[600]
}
