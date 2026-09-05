import { describe, expect, test } from 'vitest'

import type { VariableMapping } from '../../types/action'
import type { TbbpmConnection, TbbpmNode } from '../../types/tbbpm'
import { TbbpmValidator } from '../TbbpmValidator'

describe('TbbpmValidator', () => {
  test('timerTask requires exactly one schedule', () => {
    const timer: TbbpmNode = {
      id: 'timer',
      type: 'timerTask',
      position: { x: 0, y: 0 },
      properties: {},
    }

    expect(
      new TbbpmValidator([timer], []).validateNodeRules().map((error) => error.code)
    ).toContain('timerTask.schedule')

    timer.properties.duration = 'PT30S'
    expect(
      new TbbpmValidator([timer], []).validateNodeRules().map((error) => error.code)
    ).not.toContain('timerTask.schedule')

    timer.properties.wakeAtExpression = 'deadline'
    expect(
      new TbbpmValidator([timer], []).validateNodeRules().map((error) => error.code)
    ).toContain('timerTask.schedule')
  })

  test('rejects properties that do not belong to the node type', () => {
    const gateway: TbbpmNode = {
      id: 'route',
      type: 'exclusive',
      position: { x: 0, y: 0 },
      properties: { action: { actionType: 'java' } },
    }

    expect(
      new TbbpmValidator([gateway], []).validateNodeRules().map((error) => error.code)
    ).toContain('node.inapplicableProperty')
  })

  describe('连接验证', () => {
    test('应拒绝会被运行时忽略或产生歧义的网关路由', () => {
      const targets: TbbpmNode[] = [
        { id: 'left', type: 'end', position: { x: 100, y: 0 }, properties: {} },
        { id: 'right', type: 'end', position: { x: 100, y: 100 }, properties: {} },
      ]

      const exclusiveErrors = new TbbpmValidator(
        [
          { id: 'exclusive', type: 'exclusive', position: { x: 0, y: 0 }, properties: {} },
          ...targets,
        ],
        [
          { id: 'leftProcess', sourceId: 'exclusive', targetId: 'left' },
          { id: 'rightProcess', sourceId: 'exclusive', targetId: 'right' },
        ]
      ).validateNodeRules()
      const parallelErrors = new TbbpmValidator(
        [
          { id: 'parallel', type: 'parallel', position: { x: 0, y: 0 }, properties: {} },
          ...targets,
        ],
        [
          { id: 'leftProcess', sourceId: 'parallel', targetId: 'left', condition: 'enabled' },
          { id: 'rightProcess', sourceId: 'parallel', targetId: 'right' },
        ]
      ).validateNodeRules()
      const inclusiveErrors = new TbbpmValidator(
        [
          { id: 'inclusive', type: 'inclusive', position: { x: 0, y: 0 }, properties: {} },
          ...targets,
        ],
        [
          { id: 'leftProcess', sourceId: 'inclusive', targetId: 'left' },
          { id: 'rightProcess', sourceId: 'inclusive', targetId: 'right' },
        ]
      ).validateNodeRules()

      expect(
        exclusiveErrors.some((error) => error.code === 'exclusive.multipleDefaultBranches')
      ).toBe(true)
      expect(
        parallelErrors.some((error) => error.code === 'gateway.parallelConditionUnsupported')
      ).toBe(true)
      expect(
        inclusiveErrors.some((error) => error.code === 'gateway.multipleDefaultBranches')
      ).toBe(true)
    })

    test('应拒绝网关混合形状、join 条件和 exclusive 重复条件', () => {
      const nodes: TbbpmNode[] = [
        { id: 'left', type: 'autoTask', position: { x: 0, y: 0 }, properties: {} },
        { id: 'right', type: 'autoTask', position: { x: 0, y: 100 }, properties: {} },
        { id: 'exclusive', type: 'exclusive', position: { x: 100, y: 0 }, properties: {} },
        { id: 'first', type: 'end', position: { x: 200, y: 0 }, properties: {} },
        { id: 'second', type: 'end', position: { x: 200, y: 100 }, properties: {} },
      ]
      const connections: TbbpmConnection[] = [
        { id: 'leftIn', sourceId: 'left', targetId: 'exclusive' },
        { id: 'rightIn', sourceId: 'right', targetId: 'exclusive' },
        {
          id: 'firstOut',
          sourceId: 'exclusive',
          targetId: 'first',
          condition: 'enabled',
        },
        {
          id: 'secondOut',
          sourceId: 'exclusive',
          targetId: 'second',
          condition: 'enabled',
        },
      ]

      const codes = new TbbpmValidator(nodes, connections)
        .validateNodeRules()
        .map((error) => error.code)

      expect(codes).toContain('gateway.invalidShape')
      expect(codes).not.toContain('exclusive.duplicateCondition')

      const splitCodes = new TbbpmValidator(
        [
          { id: 'source', type: 'start', position: { x: 0, y: 0 }, properties: {} },
          ...nodes.slice(2),
        ],
        [{ id: 'in', sourceId: 'source', targetId: 'exclusive' }, ...connections.slice(2)]
      )
        .validateNodeRules()
        .map((error) => error.code)
      expect(splitCodes).toContain('exclusive.duplicateCondition')

      const joinCodes = new TbbpmValidator(
        [
          ...nodes.slice(0, 2),
          { id: 'join', type: 'exclusive', position: { x: 100, y: 0 }, properties: {} },
          { id: 'end', type: 'end', position: { x: 200, y: 0 }, properties: {} },
        ],
        [
          { id: 'leftIn', sourceId: 'left', targetId: 'join' },
          { id: 'rightIn', sourceId: 'right', targetId: 'join' },
          { id: 'out', sourceId: 'join', targetId: 'end', condition: 'enabled' },
        ]
      )
        .validateNodeRules()
        .map((error) => error.code)
      expect(joinCodes).toContain('gateway.joinConditionUnsupported')
    })

    test('应拒绝非网关隐式分支和条件出边', () => {
      const nodes: TbbpmNode[] = [
        {
          id: 'task',
          type: 'autoTask',
          position: { x: 0, y: 0 },
          properties: {
            action: { actionType: 'java', className: 'Handler', method: 'run' },
          },
        },
        { id: 'left', type: 'end', position: { x: 100, y: 0 }, properties: {} },
        { id: 'right', type: 'end', position: { x: 100, y: 100 }, properties: {} },
      ]
      const codes = new TbbpmValidator(nodes, [
        { id: 'leftProcess', sourceId: 'task', targetId: 'left', condition: 'enabled' },
        { id: 'rightProcess', sourceId: 'task', targetId: 'right' },
      ])
        .validateNodeRules()
        .map((error) => error.code)

      expect(codes).toEqual(
        expect.arrayContaining(['node.requiresExplicitGateway', 'node.conditionRequiresGateway'])
      )
    })

    test('应检测无效连接（目标节点不存在）', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
      ]
      const connections: TbbpmConnection[] = [
        { id: 'c1', sourceId: 'start1', targetId: 'nonexistent' }, // 目标不存在
      ]

      const validator = new TbbpmValidator(nodes, connections)
      const errors = validator.validateNodeRules()

      expect(errors.some((e) => e.code === 'conn.missingTarget')).toBe(true)
    })

    test('应拒绝跨越循环容器边界的连接', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 0 }, properties: {} },
        {
          id: 'loop1',
          type: 'while',
          name: '循环',
          position: { x: 100, y: 0 },
          properties: {
            condition: 'false',
            maxIterations: 10,
          },
        },
        {
          id: 'body',
          parentId: 'loop1',
          type: 'autoTask',
          name: '循环体',
          position: { x: 120, y: 20 },
          properties: {
            action: { actionType: 'java', className: 'Handler', method: 'run' },
          },
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 240, y: 0 }, properties: {} },
      ]
      const connections: TbbpmConnection[] = [
        { id: 'c1', sourceId: 'start1', targetId: 'loop1' },
        { id: 'invalid', sourceId: 'body', targetId: 'end1' },
      ]

      const errors = new TbbpmValidator(nodes, connections).validateNodeRules()

      expect(errors.some((e) => e.code === 'container.crossBoundaryTransition')).toBe(true)
    })
  })

  describe('属性验证', () => {
    test('应验证AutoTask必填属性', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
        {
          id: 'task1',
          type: 'autoTask',
          name: '自动任务',
          position: { x: 100, y: 100 },
          properties: {
            // Missing actionType and invocation details.
          },
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 200, y: 100 }, properties: {} },
      ]

      const validator = new TbbpmValidator(nodes, [])
      const errors = validator.validateNodeRules()

      expect(errors.some((e) => e.elementId === 'task1' && e.code === 'action.missingType')).toBe(
        true
      )
    })

    test('AutoTask rejects script actions', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
        {
          id: 'task1',
          type: 'autoTask',
          name: 'Script task',
          position: { x: 100, y: 100 },
          properties: {
            action: { actionType: 'script', language: 'groovy', source: 'return true' },
          },
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 200, y: 100 }, properties: {} },
      ]
      const connections: TbbpmConnection[] = [
        { id: 'c1', sourceId: 'start1', targetId: 'task1' },
        { id: 'c2', sourceId: 'task1', targetId: 'end1' },
      ]

      const validator = new TbbpmValidator(nodes, connections)
      const taskErrors = validator.validateNodeRules().filter((e) => e.elementId === 'task1')

      expect(taskErrors.map((error) => error.code)).toContain('autoTask.unsupportedActionType')
    })

    test('ScriptTask reports a missing source', () => {
      const nodes: TbbpmNode[] = [
        {
          id: 'task1',
          type: 'scriptTask',
          name: 'Script task',
          position: { x: 0, y: 0 },
          properties: { action: { actionType: 'script', language: 'groovy' } },
        },
      ]

      const validator = new TbbpmValidator(nodes, [])
      const errors = validator.validateNodeRules()

      expect(
        errors.some((e) => e.elementId === 'task1' && e.code === 'action.missingScriptSource')
      ).toBe(true)
    })

    test('ScriptTask 应显式声明动作类型', () => {
      const nodes: TbbpmNode[] = [
        {
          id: 'script1',
          type: 'scriptTask',
          name: '脚本',
          position: { x: 0, y: 0 },
          properties: {},
        },
      ]

      const validator = new TbbpmValidator(nodes, [])
      const errors = validator.validateNodeRules()

      expect(errors.some((e) => e.elementId === 'script1' && e.code === 'action.missingType')).toBe(
        true
      )
    })

    test('应验证Exclusive节点必填属性', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
        {
          id: 'exclusive1',
          type: 'exclusive',
          name: '决策',
          position: { x: 100, y: 100 },
          properties: {}, // Missing condition.
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 200, y: 100 }, properties: {} },
      ]

      const validator = new TbbpmValidator(nodes, [])
      const errors = validator.validateNodeRules()

      expect(errors.some((e) => e.elementId === 'exclusive1')).toBe(true)
    })

    test('应分别验证两种循环的必填属性', () => {
      const forEach: TbbpmNode = {
        id: 'forEach',
        type: 'foreach',
        name: 'For Each',
        position: { x: 0, y: 0 },
        properties: {},
      }
      const whileNode: TbbpmNode = {
        id: 'while',
        type: 'while',
        name: 'While',
        position: { x: 0, y: 0 },
        properties: {},
      }

      const errors = new TbbpmValidator([forEach, whileNode], []).validateNodeRules()

      expect(
        errors.some((e) => e.elementId === 'forEach' && e.code === 'loop.missingCollection')
      ).toBe(true)
      expect(errors.some((e) => e.elementId === 'forEach' && e.code === 'loop.missingItem')).toBe(
        true
      )
      expect(
        errors.some((e) => e.elementId === 'while' && e.code === 'loop.missingCondition')
      ).toBe(true)
      expect(errors.some((e) => e.elementId === 'while' && e.code === 'loop.missingBody')).toBe(
        true
      )
    })

    test('应拒绝循环体 end 节点有出边', () => {
      const nodes: TbbpmNode[] = [
        {
          id: 'loop',
          type: 'while',
          position: { x: 0, y: 0 },
          properties: {
            condition: 'active',
            maxIterations: 10,
          },
        },
        {
          id: 'start',
          type: 'start',
          parentId: 'loop',
          position: { x: 0, y: 0 },
          properties: {},
        },
        {
          id: 'end',
          type: 'end',
          parentId: 'loop',
          position: { x: 100, y: 0 },
          properties: {},
        },
      ]

      const codes = new TbbpmValidator(nodes, [
        { id: 'startToEnd', sourceId: 'start', targetId: 'end' },
        { id: 'endToStart', sourceId: 'end', targetId: 'start' },
      ])
        .validateNodeRules()
        .map((error) => error.code)

      expect(codes).toContain('loop.endHasOutgoing')
    })

    test('应在生成 XML 前拒绝循环变量遮蔽和保留标识符', () => {
      const loop: TbbpmNode = {
        id: 'loop',
        type: 'foreach',
        name: 'Loop',
        position: { x: 0, y: 0 },
        properties: {
          collection: 'items',
          item: 'items',
          index: '_cf$index',
          itemType: 'java.lang.String',
        },
      }
      const body: TbbpmNode = {
        id: 'body',
        parentId: 'loop',
        type: 'autoTask',
        name: 'Body',
        position: { x: 0, y: 0 },
        properties: {
          action: {
            actionType: 'script',
            language: 'java',
            source: 'int value = 1;',
          },
        },
      }

      const errors = new TbbpmValidator([loop, body], [], [{ name: 'items' }]).validateNodeRules()

      expect(errors.map((error) => error.code)).toEqual(
        expect.arrayContaining(['loop.variableShadowing', 'loop.invalidLocalVariable'])
      )
    })

    test('集合遍历输出元素必须是 inner 流程变量', () => {
      const loop: TbbpmNode = {
        id: 'loop',
        type: 'foreach',
        position: { x: 0, y: 0 },
        properties: {
          collection: 'items',
          item: 'item',
          itemType: 'java.lang.String',
          output: { target: 'results', source: 'slot' },
        },
      }

      const errors = new TbbpmValidator(
        [loop],
        [],
        [
          { name: 'items', type: 'java.util.List<java.lang.String>', inOutType: 'param' },
          { name: 'results', type: 'java.util.List<java.lang.String>', inOutType: 'return' },
          { name: 'slot', type: 'java.lang.String', inOutType: 'return' },
        ]
      ).validateNodeRules()

      expect(errors.map((error) => error.code)).toContain('loop.outputSourceNotInner')
    })

    test('循环体支持等待节点，并允许设计期 note', () => {
      const loop: TbbpmNode = {
        id: 'loop',
        type: 'while',
        position: { x: 0, y: 0 },
        properties: {
          condition: 'active',
          maxIterations: 2,
        },
      }
      const start: TbbpmNode = {
        id: 'start',
        type: 'start',
        parentId: 'loop',
        position: { x: 0, y: 0 },
        properties: {},
      }
      const end: TbbpmNode = {
        id: 'end',
        type: 'end',
        parentId: 'loop',
        position: { x: 0, y: 0 },
        properties: {},
      }
      const note: TbbpmNode = {
        id: 'note',
        type: 'note',
        parentId: 'loop',
        position: { x: 0, y: 0 },
        properties: { comment: 'design only' },
      }

      expect(
        new TbbpmValidator(
          [loop, start, end, note],
          [{ id: 'body', sourceId: 'start', targetId: 'end' }]
        ).validateNodeRules()
      ).toEqual([])
    })

    test('并行 forEach 允许 continue，但拒绝 break', () => {
      const loop: TbbpmNode = {
        id: 'loop',
        type: 'foreach',
        position: { x: 0, y: 0 },
        properties: {
          execution: 'parallel',
          collection: 'items',
          item: 'item',
          itemType: 'java.lang.String',
        },
      }
      const control: TbbpmNode = {
        id: 'control',
        type: 'continue',
        parentId: 'loop',
        position: { x: 0, y: 0 },
        properties: {},
      }
      const validator = () => new TbbpmValidator([loop, control], [], [{ name: 'items' }])

      expect(
        validator()
          .validateNodeRules()
          .map((issue) => issue.code)
      ).not.toContain('loop.parallelBreakUnsupported')
      control.type = 'break'
      expect(
        validator()
          .validateNodeRules()
          .map((issue) => issue.code)
      ).toContain('loop.parallelBreakUnsupported')
    })

    test('subBpm 中的循环控制绑定最近的外层循环', () => {
      const loop: TbbpmNode = {
        id: 'loop',
        type: 'while',
        position: { x: 0, y: 0 },
        properties: { condition: 'active', maxIterations: 2 },
      }
      const scope: TbbpmNode = {
        id: 'scope',
        type: 'subBpm',
        parentId: 'loop',
        position: { x: 0, y: 0 },
        properties: {},
      }
      const control: TbbpmNode = {
        id: 'control',
        type: 'break',
        parentId: 'scope',
        position: { x: 0, y: 0 },
        properties: {},
      }

      const codes = new TbbpmValidator([loop, scope, control], [])
        .validateNodeRules()
        .map((issue) => issue.code)
      expect(codes).not.toContain('container.invalidChildType')
      expect(codes).not.toContain('loop.rootOnlyChildType')

      loop.type = 'foreach'
      loop.properties = {
        execution: 'parallel',
        collection: 'items',
        item: 'item',
        itemType: 'java.lang.String',
      }
      expect(
        new TbbpmValidator([loop, scope, control], [], [{ name: 'items' }])
          .validateNodeRules()
          .map((issue) => issue.code)
      ).toContain('loop.parallelBreakUnsupported')

      loop.type = 'subBpm'
      loop.properties = {}
      expect(
        new TbbpmValidator([loop, scope, control], [])
          .validateNodeRules()
          .map((issue) => issue.code)
      ).toContain('loop.rootOnlyChildType')
    })

    test('Spring Bean 动作必须配置 bean 与 className，并默认调用 execute', () => {
      const node: TbbpmNode = {
        id: 'springTask',
        type: 'autoTask',
        name: 'Spring Task',
        position: { x: 0, y: 0 },
        properties: {
          action: { actionType: 'spring-bean', bean: 'service' },
        },
      }

      const errors = new TbbpmValidator([node], []).validateNodeRules()

      expect(errors.some((e) => e.code === 'action.missingClass')).toBe(true)
      expect(errors.some((e) => e.code === 'action.missingMethod')).toBe(false)
    })

    test('ScriptTask requires the script action type and validates InvocationPolicy', () => {
      const script: TbbpmNode = {
        id: 'script',
        type: 'scriptTask',
        name: 'Script',
        position: { x: 0, y: 0 },
        properties: {
          action: {
            actionType: 'script',
            language: 'java',
            source: 'counter++;',
            invocationPolicy: { maxAttempts: 101 },
          },
        },
      }

      const errors = new TbbpmValidator([script], []).validateNodeRules()

      expect(errors.map((error) => error.code)).toEqual(
        expect.arrayContaining(['invocationPolicy.invalid'])
      )
    })

    test('应拒绝全模型范围内重复的节点 ID', () => {
      const nodes: TbbpmNode[] = [
        {
          id: 'duplicate',
          type: 'while',
          name: 'Loop',
          position: { x: 0, y: 0 },
          properties: {
            condition: 'false',
            maxIterations: 10,
          },
        },
        {
          id: 'duplicate',
          parentId: 'duplicate',
          type: 'autoTask',
          name: 'Body',
          position: { x: 10, y: 10 },
          properties: {
            action: { actionType: 'java', className: 'Handler', method: 'run' },
          },
        },
      ]

      const errors = new TbbpmValidator(nodes, []).validateNodeRules()

      expect(errors.some((e) => e.code === 'process.duplicateNodeId')).toBe(true)
    })
  })

  describe('表达式语义验证', () => {
    test('不使用 JavaScript 关键字黑名单误判 Java 条件', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
        {
          id: 'exclusive1',
          type: 'exclusive',
          name: '决策',
          position: { x: 100, y: 100 },
          properties: {},
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 200, y: 100 }, properties: {} },
        { id: 'end2', type: 'end', name: '结束2', position: { x: 300, y: 100 }, properties: {} },
      ]
      const connections: TbbpmConnection[] = [
        { id: 'c1', sourceId: 'exclusive1', targetId: 'end1', condition: 'eval("malicious code")' },
        { id: 'c2', sourceId: 'exclusive1', targetId: 'end2', condition: 'true' },
      ]

      const validator = new TbbpmValidator(nodes, connections)
      const errors = validator.validateNodeRules()

      expect(errors.some((e) => e.code === 'exclusive.unsafeExpression')).toBe(false)
    })

    test('script source is validated by the server-side language provider', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
        {
          id: 'script1',
          type: 'scriptTask',
          name: '脚本',
          position: { x: 100, y: 100 },
          properties: {
            action: {
              actionType: 'script',
              language: 'qlexpress',
              source: 'new Function("return 1")()',
            },
          },
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 200, y: 100 }, properties: {} },
      ]

      const validator = new TbbpmValidator(nodes, [])
      const errors = validator.validateNodeRules()

      expect(errors.some((e) => e.code === 'scriptTask.unsafeExpression')).toBe(false)
    })

    test('应允许安全表达式', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 100 }, properties: {} },
        {
          id: 'exclusive1',
          type: 'exclusive',
          name: '决策',
          position: { x: 100, y: 100 },
          properties: { condition: 'amount > 1000 && status == "approved"' },
        },
        { id: 'end1', type: 'end', name: '结束', position: { x: 200, y: 100 }, properties: {} },
      ]

      const validator = new TbbpmValidator(nodes, [])
      const errors = validator.validateNodeRules()

      const securityErrors = errors.filter(
        (e) => e.elementId === 'exclusive1' && e.category === 'compileflow'
      )
      expect(securityErrors.length).toBe(0)
    })

    test('应拒绝网关、循环和跳转条件直接修改流程状态', () => {
      const nodes: TbbpmNode[] = [
        {
          id: 'exclusive',
          type: 'exclusive',
          name: '决策',
          position: { x: 0, y: 0 },
          properties: {},
        },
        {
          id: 'loop',
          type: 'while',
          name: '循环',
          position: { x: 100, y: 0 },
          properties: {
            condition: 'remaining-- > 0',
            maxIterations: 10,
          },
        },
        {
          id: 'break',
          type: 'break',
          name: '跳出',
          parentId: 'loop',
          position: { x: 100, y: 100 },
          properties: { condition: 'cancelled = true' },
        },
        {
          id: 'end',
          type: 'end',
          name: '结束',
          position: { x: 200, y: 0 },
          properties: {},
        },
      ]
      const connections: TbbpmConnection[] = [
        {
          id: 'mutating',
          sourceId: 'exclusive',
          targetId: 'end',
          condition: 'approved = true',
        },
      ]

      const errors = new TbbpmValidator(nodes, connections).validateNodeRules()
      const mutationErrors = errors.filter((error) => error.code === 'condition.directMutation')

      expect(mutationErrors).toHaveLength(3)
      expect(mutationErrors.map((error) => error.params?.operator)).toEqual(
        expect.arrayContaining(['=', '--'])
      )
    })

    test('应拒绝生成调用时会被忽略的映射默认值', () => {
      const task: TbbpmNode = {
        id: 'task',
        type: 'autoTask',
        position: { x: 0, y: 0 },
        properties: {
          action: {
            actionType: 'java',
            className: 'example.Action',
            method: 'run',
            mappings: [
              {
                target: 'input',
                dataType: 'java.lang.String',
                direction: 'input',
                source: 'input',
                defaultValue: '',
              },
              {
                dataType: 'java.lang.String',
                direction: 'output',
                target: 'output',
                defaultValue: 'ignored',
              } as unknown as VariableMapping,
            ],
          },
        },
      }

      const errors = new TbbpmValidator([task], [], [{ name: 'input' }, { name: 'output' }])
        .validateNodeRules()
        .filter((error) => error.code === 'mapping.inapplicableDefault')

      expect(errors.map((error) => error.params?.reason)).toEqual(['source', 'output'])
    })
  })

  describe('边界条件', () => {
    test('BPM 调用允许多个独立输出但拒绝重复父变量目标', () => {
      const validCall: TbbpmNode = {
        id: 'sub',
        type: 'bpmCall',
        name: 'BPM 调用',
        position: { x: 0, y: 0 },
        properties: {
          code: 'child',
          classpath: 'flows/child.bpm',
          callMappings: [
            {
              source: 'first',
              direction: 'output',
              target: 'firstResult',
            },
            {
              source: 'second',
              direction: 'output',
              target: 'secondResult',
            },
          ],
        },
      }
      const variables = [{ name: 'firstResult' }, { name: 'secondResult' }]

      expect(
        new TbbpmValidator([validCall], [], variables)
          .validateNodeRules()
          .map((error) => error.code)
      ).not.toEqual(
        expect.arrayContaining([
          'tbbpm.mapping.multipleOutputs',
          'tbbpm.mapping.duplicateOutputTarget',
        ])
      )

      const conflictingCall: TbbpmNode = {
        ...validCall,
        properties: {
          ...validCall.properties,
          callMappings: validCall.properties.callMappings?.map((variable) => ({
            ...variable,
            target: 'firstResult',
          })),
        },
      }
      expect(
        new TbbpmValidator([conflictingCall], [], variables)
          .validateNodeRules()
          .map((error) => error.code)
      ).toContain('tbbpm.mapping.duplicateOutputTarget')
    })

    test('应处理极大流程（性能测试）', () => {
      const nodes: TbbpmNode[] = [
        { id: 'start1', type: 'start', name: '开始', position: { x: 0, y: 0 }, properties: {} },
      ]

      // 生成1000个节点
      for (let i = 1; i <= 1000; i++) {
        nodes.push({
          id: `task${i}`,
          type: 'autoTask',
          name: `任务${i}`,
          position: { x: i * 10, y: 100 },
          properties: {
            action: { actionType: 'java', className: 'Handler', method: 'run' },
          },
        })
      }

      nodes.push({
        id: 'end1',
        type: 'end',
        name: '结束',
        position: { x: 10000, y: 0 },
        properties: {},
      })

      const connections: TbbpmConnection[] = []
      for (let i = 1; i <= 1000; i++) {
        connections.push({
          id: `c${i}`,
          sourceId: i === 1 ? 'start1' : `task${i - 1}`,
          targetId: `task${i}`,
        })
      }
      connections.push({
        id: 'c_end',
        sourceId: 'task1000',
        targetId: 'end1',
      })

      const startTime = performance.now()
      const validator = new TbbpmValidator(nodes, connections)
      const errors = validator.validateNodeRules()
      const duration = performance.now() - startTime

      // 验证应在1秒内完成
      expect(duration).toBeLessThan(1000)
      expect(errors.length).toBeGreaterThanOrEqual(0)
    })
  })
})
