import { describe, expect, test } from 'vitest'

import type { BpmnNode, UnifiedProcessDefinition } from '../../types/flowDefinition'
import type { TbbpmConnection, TbbpmNode } from '../../types/tbbpm'
import { generateBpmnXml, parseBpmnXml } from '../bpmnXmlCodec'
import { generateTbbpmXml, parseTbbpmXml } from '../tbbpmXmlCodec'

function requireParsedProcess<Definition extends UnifiedProcessDefinition>(result: {
  success: boolean
  data?: Definition
  error?: { message: string }
}): Definition {
  expect(result.success, result.error?.message).toBe(true)
  if (!result.data) throw new Error(result.error?.message || 'Expected parsed flow data')
  return result.data
}

// ==================== BPMN测试用例 ====================

describe('BPMN XML双向同步测试', () => {
  test('空流程往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  targetNamespace="http://compileflow.alibaba.com/schema/bpmn"
                  id="Definitions_1">
  <bpmn:process id="Process_1" name="Test Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

    // 解析
    const parseResult = parseBpmnXml(originalXml)
    expect(parseResult.success).toBe(true)
    expect(parseResult.data).toBeDefined()
    expect(parseResult.data?.type).toBe('BPMN')
    expect(parseResult.data?.nodes.length).toBe(1)

    // 生成
    const generatedXml = generateBpmnXml(parseResult.data!)
    expect(generatedXml).toContain('<bpmn:startEvent')
    expect(generatedXml).toContain('id="StartEvent_1"')
  })

  test('ServiceTask节点往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:cf="http://www.compileflow.org"
                  targetNamespace="http://compileflow.alibaba.com/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:serviceTask id="ServiceTask_1" name="Order Service">
      <bpmn:extensionElements>
        <cf:action type="java" class="com.example.OrderService" method="execute"/>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
  </bpmn:process>
</bpmn:definitions>`

    const parseResult = parseBpmnXml(originalXml)
    expect(parseResult.success).toBe(true)

    const serviceTask = parseResult.data?.nodes[0] as BpmnNode
    expect(serviceTask?.type).toBe('bpmn:ServiceTask')
    expect(serviceTask?.properties.action).toMatchObject({
      actionType: 'java',
      className: 'com.example.OrderService',
      method: 'execute',
    })

    const generatedXml = generateBpmnXml(parseResult.data!)
    expect(generatedXml).toContain(
      '<cf:action type="java" class="com.example.OrderService" method="execute"/>'
    )
  })

  test('BPMN action Durable 执行语义保持往返', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:cf="http://www.compileflow.org"
                  targetNamespace="urn:compileflow:test">
  <bpmn:process id="effect_action" isExecutable="true">
    <bpmn:serviceTask id="task">
      <bpmn:extensionElements>
        <cf:action type="java" execution="effect"
                   class="com.example.EffectAction" method="execute"/>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
  </bpmn:process>
</bpmn:definitions>`

    const parsed = parseBpmnXml(originalXml)

    expect(parsed.success).toBe(true)
    expect((parsed.data?.nodes[0] as BpmnNode)?.properties.action).toMatchObject({
      execution: 'effect',
    })
    expect(generateBpmnXml(parsed.data!)).toContain('type="java" execution="effect"')
  })

  test('BPMN 动态 Effect 恢复策略保持往返', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:cf="http://www.compileflow.org"
                  targetNamespace="urn:compileflow:test">
  <bpmn:process id="dynamic_effect" isExecutable="true">
    <bpmn:extensionElements>
      <cf:var name="recoveryPlan" dataType="java.lang.Object" inOutType="inner"/>
    </bpmn:extensionElements>
    <bpmn:serviceTask id="task">
      <bpmn:extensionElements>
        <cf:action type="java" execution="effect" class="com.example.Task">
          <cf:effectPolicy recoveryPlanVariable="recoveryPlan"/>
        </cf:action>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
  </bpmn:process>
</bpmn:definitions>`

    const parsed = parseBpmnXml(originalXml)

    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.nodes[0]?.properties.action?.effectPolicy).toEqual({
      recovery: undefined,
      recoveryPlanVariable: 'recoveryPlan',
      maxAttempts: undefined,
      maxReconcileAttempts: undefined,
      recoveryDelay: undefined,
      maxRecoveryDuration: undefined,
      reconcileAction: undefined,
    })
    const generated = generateBpmnXml(parsed.data!)
    expect(generated).toContain('<cf:effectPolicy recoveryPlanVariable="recoveryPlan"/>')
    expect(parseBpmnXml(generated).success).toBe(true)
  })

  test('SequenceFlow条件表达式往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:cf="http://www.compileflow.org"
                  targetNamespace="http://compileflow.alibaba.com/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1"/>
    <bpmn:exclusiveGateway id="Split_1" default="Flow_default"/>
    <bpmn:serviceTask id="High_1">
      <bpmn:extensionElements>
        <cf:action type="java" class="com.example.HighAction" method="run"/>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="Default_1">
      <bpmn:extensionElements>
        <cf:action type="java" class="com.example.DefaultAction" method="run"/>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:exclusiveGateway id="Join_1"/>
    <bpmn:endEvent id="End_1"/>
    <bpmn:sequenceFlow id="Flow_start" sourceRef="Start_1" targetRef="Split_1"/>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="Split_1" targetRef="High_1">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression" language="java">amount &gt; 1000</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_default" sourceRef="Split_1" targetRef="Default_1"/>
    <bpmn:sequenceFlow id="Flow_high_join" sourceRef="High_1" targetRef="Join_1"/>
    <bpmn:sequenceFlow id="Flow_default_join" sourceRef="Default_1" targetRef="Join_1"/>
    <bpmn:sequenceFlow id="Flow_end" sourceRef="Join_1" targetRef="End_1"/>
  </bpmn:process>
</bpmn:definitions>`

    const parseResult = parseBpmnXml(originalXml)
    expect(parseResult.success).toBe(true)
    expect(parseResult.data?.connections.length).toBe(6)

    const flow = parseResult.data?.connections.find((connection) => connection.id === 'Flow_1')
    expect(flow?.condition).toBe('amount > 1000')

    const generatedXml = generateBpmnXml(parseResult.data!)
    expect(generatedXml).toContain('conditionExpression')
    expect(generatedXml).toContain('amount &gt; 1000')
    expect(generatedXml).not.toContain('${amount')
  })

  test('解析错误处理', () => {
    const invalidXml = 'not a valid xml'

    const parseResult = parseBpmnXml(invalidXml)
    expect(parseResult.success).toBe(false)
    expect(parseResult.error).toBeDefined()
    expect(parseResult.error?.code).toBe('PARSE_ERROR')
  })
})

// ==================== TBBPM测试用例 ====================

describe('TBBPM XML双向同步测试', () => {
  test('缺少 g 时按节点真实默认尺寸布局且互不重叠', () => {
    const definition = requireParsedProcess(
      parseTbbpmXml(`<bpm code="missing_geometry">
        <start id="start"><transition to="task"/></start>
        <autoTask id="task">
          <action type="java" class="com.example.Task" method="execute"/>
          <transition to="end"/>
        </autoTask>
        <end id="end"/>
      </bpm>`)
    )

    expect(definition.nodes.map(({ position, size, type }) => ({ position, size, type }))).toEqual([
      { type: 'start', position: { x: 80, y: 80 }, size: { width: 80, height: 80 } },
      { type: 'autoTask', position: { x: 360, y: 80 }, size: { width: 200, height: 100 } },
      { type: 'end', position: { x: 640, y: 80 }, size: { width: 80, height: 80 } },
    ])
  })

  test('Wait timeout在两种Wait节点上往返一致', () => {
    const xml = `<bpm code="waits">
      <waitTask id="checkpoint" timeout="PT24H"/>
      <waitEventTask id="approval" event="approved" timeout="PT30M"/>
    </bpm>`

    const definition = requireParsedProcess(parseTbbpmXml(xml))
    const nodes = definition.nodes as TbbpmNode[]
    expect(nodes.map((node) => node.properties.timeout)).toEqual(['PT24H', 'PT30M'])

    const generated = generateTbbpmXml(definition)
    expect(generated).toContain('<waitTask id="checkpoint"')
    expect(generated).toContain('timeout="PT24H"')
    expect(generated).toContain('<waitEventTask id="approval"')
    expect(generated).toContain('timeout="PT30M"')
  })

  test('空流程往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="test_flow" name="测试流程">
  <start id="start1" name="开始" g="100,100,80,50"/>
</bpm>`

    const parseResult = parseTbbpmXml(originalXml)
    expect(parseResult.success).toBe(true)
    expect(parseResult.data?.type).toBe('TBBPM')
    expect(parseResult.data?.code).toBe('test_flow')
    expect(parseResult.data?.nodes.length).toBe(1)

    const startNode = parseResult.data?.nodes[0] as TbbpmNode
    expect(startNode?.type).toBe('start')
    expect(startNode?.position.x).toBe(100)
    expect(startNode?.position.y).toBe(100)

    const generatedXml = generateTbbpmXml(parseResult.data!)
    expect(generatedXml).toContain('<start')
    expect(generatedXml).toContain('id="start1"')
    expect(generatedXml).not.toContain(' type="process"')
  })

  test('根流程变量使用统一模型并保持往返', () => {
    const originalXml = `<bpm code="variable_roundtrip">
      <var name="input" dataType="java.lang.String" inOutType="param"
           defaultValue="sample" description="Input value"/>
      <var name="result" dataType="java.lang.Integer" inOutType="return"/>
      <start id="start" g="0,0,32,32"/>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)

    expect(parsed.success).toBe(true)
    expect(parsed.data?.variables).toEqual([
      {
        name: 'input',
        type: 'java.lang.String',
        inOutType: 'param',
        defaultValue: 'sample',
        description: 'Input value',
      },
      {
        name: 'result',
        type: 'java.lang.Integer',
        inOutType: 'return',
        defaultValue: undefined,
        description: undefined,
      },
    ])
    expect(parsed.data?.variables?.[0]).not.toHaveProperty('dataType')

    const generatedXml = generateTbbpmXml(parsed.data!)
    expect(generatedXml).toContain('name="input" dataType="java.lang.String" inOutType="param"')
    expect(parseTbbpmXml(generatedXml).data?.variables).toEqual(parsed.data?.variables)
  })

  test('节点说明统一映射到通用 documentation 并保持往返', () => {
    const source = `<bpm code="node_documentation">
      <autoTask id="task" name="Task" description="Invokes &amp; audits" g="0,0,100,80"/>
    </bpm>`

    const parsed = parseTbbpmXml(source)

    expect(parsed.success, parsed.error?.message).toBe(true)
    const task = parsed.data?.nodes[0] as TbbpmNode
    expect(task.documentation).toBe('Invokes & audits')
    expect(task.properties).not.toHaveProperty('description')

    const generated = generateTbbpmXml(parsed.data!)
    expect(generated).toContain('description="Invokes &amp; audits"')
    expect(parseTbbpmXml(generated).data?.nodes[0]?.documentation).toBe('Invokes & audits')
  })

  test('空字符串默认值在流程、Action 与子流程边界保持往返', () => {
    const originalXml = `<bpm code="empty_string_defaults">
      <var name="rootValue" dataType="java.lang.String"
           inOutType="inner" defaultValue=""/>
      <scriptTask id="task" g="0,0,100,80">
        <action type="script" language="qlexpress">
          <input target="value" dataType="java.lang.String" defaultValue=""/>
          <code>value</code>
        </action>
      </scriptTask>
      <bpmCall id="sub" g="120,0,100,80" code="child" classpath="flows/child.bpm">
        <input target="childInput" defaultValue=""/>
      </bpmCall>
    </bpm>`

    const parsed = requireParsedProcess(parseTbbpmXml(originalXml))

    expect(parsed.variables?.[0]?.defaultValue).toBe('')
    expect((parsed.nodes[0] as TbbpmNode).properties.action?.mappings?.[0]).toMatchObject({
      direction: 'input',
      defaultValue: '',
    })
    expect((parsed.nodes[1] as TbbpmNode).properties.callMappings?.[0]).toMatchObject({
      direction: 'input',
      defaultValue: '',
    })

    const generated = generateTbbpmXml(parsed)
    expect(generated.match(/defaultValue=""/g)).toHaveLength(3)
    expect(parseTbbpmXml(generated).data?.variables?.[0]?.defaultValue).toBe('')
  })

  test('子流程输入拒绝重复声明 dataType', () => {
    const parsed = parseTbbpmXml(`<bpm code="invalid_call_input_type">
      <bpmCall id="sub" code="child" classpath="flows/child.bpm">
        <input source="value" target="value" dataType="java.lang.String"/>
      </bpmCall>
    </bpm>`)

    expect(parsed.success).toBe(false)
  })

  test('Action mapping dataType拒绝首尾空白', () => {
    const parsed = parseTbbpmXml(`<bpm code="mapping_identity">
      <scriptTask id="task" g="0,0,100,80">
        <action type="script" language="qlexpress">
          <input target="value" dataType=" java.lang.String " defaultValue="sample"/>
          <code>value</code>
        </action>
      </scriptTask>
    </bpm>`)

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('dataType must not contain surrounding whitespace')
  })

  test.each(['bpmCallVersion', 'bpmCallAlias', 'bpmCallNamespace'])(
    '子流程拒绝部署限定属性 %s',
    (attribute) => {
      const parsed = parseTbbpmXml(`<bpm code="subprocess_binding">
        <bpmCall id="sub" g="0,0,100,80"
                code="child" classpath="flows/child.bpm" ${attribute}="value"/>
      </bpm>`)

      expect(parsed.success).toBe(false)
      expect(parsed.error?.message).toContain(
        `attribute "${attribute}" is not part of the TBBPM contract`
      )
    }
  )

  test('拒绝生成调用时会被忽略的映射默认值', () => {
    const parsed = parseTbbpmXml(`<bpm code="invalid_mapping_default">
      <autoTask id="task" g="0,0,100,80">
        <action type="java" class="com.example.Service" method="execute">
          <output dataType="java.lang.String" defaultValue="ignored"/>
        </action>
      </autoTask>
    </bpm>`)

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('unsupported attribute "defaultValue"')
  })

  test('根流程变量拒绝映射属性和旧 result 方向', () => {
    const mappedRoot = parseTbbpmXml(
      '<bpm code="invalid"><var name="input" dataType="java.lang.String" ' +
        'inOutType="param" contextVarName="input"/></bpm>'
    )
    const resultDirection = parseTbbpmXml(
      '<bpm code="invalid"><var name="input" dataType="java.lang.String" ' +
        'inOutType="result"/></bpm>'
    )

    expect(mappedRoot.success).toBe(false)
    expect(mappedRoot.error?.message).toContain('contextVarName')
    expect(resultDirection.success).toBe(false)
    expect(resultDirection.error?.message).toContain('param, return, or inner')
  })

  test('Action 与子流程映射目标保持往返且必须引用根变量', () => {
    const originalXml = `<bpm code="mapping_roundtrip">
      <var name="input" dataType="java.lang.Integer" inOutType="param"/>
      <var name="result" dataType="java.lang.Integer" inOutType="return"/>
      <var name="auditResult" dataType="java.lang.String" inOutType="return"/>
      <scriptTask id="task" g="0,0,100,80">
        <action type="script" language="qlexpress">
          <input target="value" dataType="java.lang.Integer" source="input"/>
          <output dataType="java.lang.Integer" target="result"/>
          <code>value + 1</code>
        </action>
      </scriptTask>
      <bpmCall id="sub" g="120,0,100,80" code="child" classpath="flows/child.bpm">
        <output source="childResult" target="result"/>
        <output source="childAudit" target="auditResult"/>
      </bpmCall>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)

    expect(parsed.success).toBe(true)
    const actionNode = parsed.data?.nodes[0] as TbbpmNode | undefined
    const bpmCallNode = parsed.data?.nodes[1] as TbbpmNode | undefined
    expect(actionNode?.properties.action?.mappings).toMatchObject([
      { direction: 'input', source: 'input' },
      { direction: 'output', target: 'result' },
    ])
    expect(bpmCallNode?.properties.callMappings).toMatchObject([
      { direction: 'output', target: 'result' },
      { direction: 'output', target: 'auditResult' },
    ])

    const generatedXml = generateTbbpmXml(parsed.data!)
    expect(parseTbbpmXml(generatedXml).success).toBe(true)

    const unknownTarget = parseTbbpmXml(
      originalXml.replace('target="result"/>', 'target="missing"/>')
    )
    expect(unknownTarget.success).toBe(false)
    expect(unknownTarget.error?.message).toContain('declared process variable')

    const duplicateTarget = parseTbbpmXml(
      originalXml.replace('target="auditResult"/>', 'target="result"/>')
    )
    expect(duplicateTarget.success).toBe(false)
    expect(duplicateTarget.error?.message).toContain('duplicate output target')
  })

  test('内嵌 subBpm 保持容器归属和边界节点往返', () => {
    const source = `<bpm code="embedded_scope">
      <subBpm id="validation" name="Validation" g="0,0,220,120">
        <start id="scopeStart" g="20,20,80,80">
          <transition to="validate"/>
        </start>
        <autoTask id="validate" g="120,20,100,80">
          <transition to="scopeEnd"/>
        </autoTask>
        <end id="scopeEnd" g="240,20,80,80"/>
      </subBpm>
    </bpm>`

    const parsed = requireParsedProcess(parseTbbpmXml(source))
    const scope = parsed.nodes.find((node) => node.id === 'validation') as TbbpmNode
    const children = parsed.nodes.filter((node) => node.parentId === scope.id)

    expect(scope.type).toBe('subBpm')
    expect(children.map((node) => node.type)).toEqual(['start', 'autoTask', 'end'])

    const generated = generateTbbpmXml(parsed)
    const reparsed = requireParsedProcess(parseTbbpmXml(generated))
    expect(reparsed.nodes.filter((node) => node.parentId === scope.id)).toHaveLength(3)
    expect(generated).not.toContain('code="validation"')
  })

  test('TBBPM action Durable 执行语义保持往返', () => {
    const originalXml = `<bpm code="effect_action">
      <autoTask id="task" g="0,0,100,80">
        <action type="java" execution="effect"
                class="com.example.EffectAction" method="execute"/>
      </autoTask>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)

    expect(parsed.success).toBe(true)
    expect((parsed.data?.nodes[0] as TbbpmNode)?.properties.action?.execution).toBe('effect')
    const generated = generateTbbpmXml(parsed.data!)
    expect(generated).toContain('type="java" execution="effect"')
    expect(
      (parseTbbpmXml(generated).data?.nodes[0] as TbbpmNode | undefined)?.properties.action
        ?.execution
    ).toBe('effect')
  })

  test.each(['execution=" EFFECT "', 'execution="transactional"'])(
    'Action 拒绝非规范 Durable 执行语义：%s',
    (attribute) => {
      const result = parseTbbpmXml(`<bpm code="invalid_execution">
        <autoTask id="task" g="0,0,100,80">
          <action type="java" class="com.example.Task" ${attribute}/>
        </autoTask>
      </bpm>`)
      expect(result.success).toBe(false)
    }
  )

  test('note 节点 name 属性往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="test" name="Test">
  <start id="start1" name="开始" g="0,0,80,50"/>
  <note id="note1" name="备注标题" comment="备注内容" g="100,0,120,40"/>
  <end id="end1" name="结束" g="200,0,80,50"/>
</bpm>`

    const parseResult = parseTbbpmXml(originalXml)
    expect(parseResult.success).toBe(true)

    const noteNode = parseResult.data?.nodes.find((n) => n.id === 'note1')
    expect(noteNode?.name).toBe('备注标题')
    expect((noteNode?.properties as Record<string, unknown>).comment).toBe('备注内容')

    const generatedXml = generateTbbpmXml(parseResult.data!)
    expect(generatedXml).toContain('name="备注标题"')
    expect(generatedXml).toContain('comment="备注内容"')
  })

  test('AutoTask Java动作往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="test" name="Test">
  <autoTask id="task1" name="订单验证" g="200,100,100,80">
    <action type="java" class="com.example.OrderValidator" method="validate"/>
    <transition to="end1"/>
  </autoTask>
  <end id="end1" name="结束" g="400,100,80,50"/>
</bpm>`

    const parseResult = parseTbbpmXml(originalXml)
    expect(parseResult.success).toBe(true)

    const autoTask = parseResult.data?.nodes[0] as TbbpmNode
    expect(autoTask?.type).toBe('autoTask')
    expect(autoTask?.properties.action?.actionType).toBe('java')
    expect(autoTask?.properties.action?.className).toBe('com.example.OrderValidator')
    expect(autoTask?.properties.action?.method).toBe('validate')

    expect(parseResult.data?.connections.length).toBe(1)
    expect(parseResult.data?.connections[0].targetId).toBe('end1')

    const generatedXml = generateTbbpmXml(parseResult.data!)
    expect(generatedXml).toContain(
      '<action type="java" class="com.example.OrderValidator" method="validate"/>'
    )
    expect(generatedXml).toContain('<transition to="end1"/>')
  })

  test('Spring Bean 动作保留 bean、className 与 method', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="spring_action" name="Spring Action">
  <autoTask id="task1" name="调用服务" g="100,100,100,80">
    <action type="spring-bean" bean="orderService"
            class="com.example.OrderService" method="submit"/>
  </autoTask>
</bpm>`

    const parsed = parseTbbpmXml(originalXml)
    expect(parsed.success).toBe(true)
    expect(parsed.data?.nodes[0]?.properties).toMatchObject({
      action: {
        actionType: 'spring-bean',
        bean: 'orderService',
        className: 'com.example.OrderService',
        method: 'submit',
      },
    })

    const generatedXml = generateTbbpmXml(parsed.data!)
    expect(generatedXml).toContain('bean="orderService"')
    expect(generatedXml).toContain('class="com.example.OrderService"')
    expect(generatedXml).toContain('method="submit"')
  })

  test('explicit script actions preserve language and source', () => {
    const originalXml = `<bpm code="script_action">
      <scriptTask id="task1" g="0,0,100,80">
        <action type="script" language="java">
          <code><![CDATA[return Instant.now().toString();]]></code>
        </action>
      </scriptTask>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)
    expect(parsed.success).toBe(true)
    expect(parsed.data?.nodes[0]?.properties).toMatchObject({
      action: {
        actionType: 'script',
        language: 'java',
        source: 'return Instant.now().toString();',
      },
    })

    const generatedXml = generateTbbpmXml(parsed.data!)
    expect(generatedXml).toContain('<code><![CDATA[return Instant.now().toString();]]></code>')
    expect(parseTbbpmXml(generatedXml).data?.nodes[0]?.properties.action).toMatchObject({
      language: 'java',
      source: 'return Instant.now().toString();',
    })
  })

  test('custom script languages use the stable script action type', () => {
    const originalXml = `<bpm code="custom_script">
      <scriptTask id="task1" g="0,0,100,80">
        <action type="script" language="kotlin-script"><code>price * 2</code></action>
      </scriptTask>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)
    expect(parsed.success).toBe(true)
    expect(parsed.data?.nodes[0]?.properties).toMatchObject({
      action: {
        actionType: 'script',
        language: 'kotlin-script',
        source: 'price * 2',
      },
    })

    const generatedXml = generateTbbpmXml(parsed.data!)
    expect(generatedXml).toContain('type="script"')
    expect(generatedXml).toContain('language="kotlin-script"')
  })

  test('拒绝在纯路由 Exclusive 上配置动作', () => {
    const originalXml = `<bpm code="gateway_action">
      <exclusive id="exclusive1" g="0,0,100,80">
        <action type="script" language="java">
          <code><![CDATA[routeKey = "premium";]]></code>
        </action>
        <transition to="end1" condition="routeKey == &quot;premium&quot;"/>
        <transition to="end2"/>
      </exclusive>
      <end id="end1" g="200,0,32,32"/>
      <end id="end2" g="200,80,32,32"/>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)
    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('unsupported child "action"')
  })

  test('导出时拒绝内存模型把任务属性挂到纯路由网关', () => {
    const parsed = parseTbbpmXml(`<bpm code="gateway_property">
      <exclusive id="exclusive1" g="0,0,100,80">
        <transition to="end1" condition="premium"/>
        <transition to="end2"/>
      </exclusive>
      <end id="end1" g="200,0,32,32"/>
      <end id="end2" g="200,80,32,32"/>
    </bpm>`)
    expect(parsed.success).toBe(true)
    const exclusive = parsed.data!.nodes[0] as TbbpmNode
    exclusive.properties.action = { actionType: 'java' }

    expect(() => generateTbbpmXml(parsed.data!)).toThrow('contains inapplicable properties: action')
  })

  test('拒绝 Java 引擎不支持的 exclusive 节点级 condition', () => {
    const result = parseTbbpmXml(
      '<bpm code="invalid"><exclusive id="d" g="0,0,100,80" condition="x"/></bpm>'
    )

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('attribute "condition"')
  })

  test('拒绝同一节点的重复 action', () => {
    const result = parseTbbpmXml(`<bpm code="duplicate_action">
      <autoTask id="task1" g="0,0,100,80">
        <action type="java" class="com.example.First"/>
        <action type="java" class="com.example.Second"/>
      </autoTask>
    </bpm>`)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('at most one action')
  })

  test('拒绝已移除的嵌套 action 实现结构', () => {
    const legacyXml = `<bpm code="legacy_action">
      <autoTask id="task1" g="0,0,100,80">
        <action><javaActionDefinition className="com.example.Task" method="run"/></action>
      </autoTask>
    </bpm>`

    const result = parseTbbpmXml(legacyXml)
    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('non-blank type')
  })

  test('拒绝已移除的根 type 属性', () => {
    const result = parseTbbpmXml(
      '<bpm code="legacy_root" type="process"><start id="start" g="0,0,32,32"/></bpm>'
    )

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('attribute "type"')
  })

  test.each([
    {
      name: 'missing root code',
      xml: '<bpm><start id="start" g="0,0,32,32"/></bpm>',
      expected: 'code',
    },
    {
      name: 'missing node id',
      xml: '<bpm code="invalid"><start g="0,0,32,32"/></bpm>',
      expected: 'id',
    },
    {
      name: 'missing transition target',
      xml: '<bpm code="invalid"><start id="start" g="0,0,32,32"><transition/></start></bpm>',
      expected: 'to',
    },
    {
      name: 'removed transition priority',
      xml: '<bpm code="invalid"><start id="start" g="0,0,32,32"><transition to="end" priority="1.5"/></start><end id="end" g="80,0,32,32"/></bpm>',
      expected: 'not part of the TBBPM contract',
    },
    {
      name: 'unsupported node child',
      xml: '<bpm code="invalid"><start id="start" g="0,0,32,32"><unknown/></start></bpm>',
      expected: 'child "unknown"',
    },
  ])('外部 XML 边界拒绝 $name', ({ xml, expected }) => {
    const result = parseTbbpmXml(xml)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain(expected)
  })

  test('AutoTask InvocationPolicy完整往返一致性', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="invocation_policy" name="Invocation Policy">
  <autoTask id="task1" name="Task" g="100,100,100,80">
    <action type="java" class="com.example.Task" method="run">
      <invocationPolicy timeout="PT20S" attemptTimeout="PT5S" maxAttempts="3" initialBackoff="PT0.2S"
                 backoffMultiplier="2" maxBackoff="PT2S"
                 jitter="none" retryOn="custom-retry" onFailure="custom-failure"/>
    </action>
  </autoTask>
</bpm>`

    const parsed = parseTbbpmXml(originalXml)
    expect(parsed.success).toBe(true)
    expect(parsed.data?.nodes[0]?.properties.action?.invocationPolicy).toEqual({
      timeout: 'PT20S',
      attemptTimeout: 'PT5S',
      maxAttempts: 3,
      initialBackoff: 'PT0.2S',
      backoffMultiplier: 2,
      maxBackoff: 'PT2S',
      jitter: 'none',
      retryOn: 'custom-retry',
      onFailure: 'custom-failure',
    })

    const generatedXml = generateTbbpmXml(parsed.data!)
    expect(generatedXml).toContain('maxAttempts="3"')
    expect(generatedXml).toContain('backoffMultiplier="2"')
    expect(generatedXml).toContain('jitter="none"')
    expect(generatedXml).not.toContain(' retry="')
    expect(parseTbbpmXml(generatedXml).success).toBe(true)
  })

  test('AutoTask InvocationPolicy拒绝重复和越界配置', () => {
    const duplicateXml = `<bpm code="invalid"><autoTask id="task">
      <action type="java" class="com.example.Task">
        <invocationPolicy maxAttempts="1"/><invocationPolicy maxAttempts="2"/>
      </action>
    </autoTask></bpm>`
    const excessiveRetryXml = `<bpm code="invalid"><autoTask id="task">
      <action type="java" class="com.example.Task">
        <invocationPolicy maxAttempts="101"/>
      </action>
    </autoTask></bpm>`

    expect(parseTbbpmXml(duplicateXml).success).toBe(false)
    expect(parseTbbpmXml(excessiveRetryXml).success).toBe(false)
  })

  test('Effect 恢复策略与 reconcileAction 完整往返', () => {
    const originalXml = `<bpm code="effect_recovery">
      <var name="orderId" dataType="java.lang.String" inOutType="param"/>
      <var name="status" dataType="java.lang.String" inOutType="return"/>
      <autoTask id="reserve" g="0,0,100,80">
        <action type="spring-bean" execution="effect" bean="inventory"
                class="com.example.Inventory" method="reserve">
          <input source="orderId" target="orderId" dataType="java.lang.String"/>
          <output target="status" dataType="java.lang.String"/>
          <invocationPolicy timeout="PT30S" attemptTimeout="PT10S"/>
          <effectPolicy recovery="reconcile" maxAttempts="3"
                        maxReconcileAttempts="5" recoveryDelay="PT1S"
                        maxRecoveryDuration="PT1H">
            <reconcileAction type="spring-bean" bean="inventory"
                             class="com.example.Inventory" method="query">
              <input source="orderId" target="requestId" dataType="java.lang.String"/>
            </reconcileAction>
          </effectPolicy>
        </action>
      </autoTask>
    </bpm>`

    const parsed = parseTbbpmXml(originalXml)

    expect(parsed.success, parsed.error?.message).toBe(true)
    const policy = parsed.data?.nodes[0]?.properties.action?.effectPolicy
    expect(policy).toMatchObject({
      recovery: 'reconcile',
      maxAttempts: 3,
      maxReconcileAttempts: 5,
      recoveryDelay: 'PT1S',
      maxRecoveryDuration: 'PT1H',
      reconcileAction: {
        actionType: 'spring-bean',
        bean: 'inventory',
        className: 'com.example.Inventory',
        method: 'query',
        inputs: [{ source: 'orderId', target: 'requestId', dataType: 'java.lang.String' }],
      },
    })
    const generated = generateTbbpmXml(parsed.data!)
    expect(generated.indexOf('<invocationPolicy')).toBeLessThan(generated.indexOf('<effectPolicy'))
    expect(generated).toContain('<reconcileAction type="spring-bean"')
    expect(parseTbbpmXml(generated).data?.nodes[0]?.properties.action?.effectPolicy).toEqual(policy)
  })

  test.each([
    ['effectPolicy requires effect execution', '<effectPolicy recovery="manual"/>'],
    ['retry requires recoveryDelay', '<effectPolicy recovery="retry" maxAttempts="2"/>'],
    [
      'dynamic policy rejects static settings',
      '<effectPolicy recoveryPlanVariable="plan" recovery="manual"/>',
    ],
  ])('Effect 恢复策略拒绝无效配置：%s', (_name, policyXml) => {
    const parsed = parseTbbpmXml(`<bpm code="invalid_effect">
      <autoTask id="task">
        <action type="java" class="com.example.Task">${policyXml}</action>
      </autoTask>
    </bpm>`)

    expect(parsed.success).toBe(false)
  })

  test('ScriptTask round-trips without normalizing source', () => {
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="test" name="Test">
  <scriptTask id="task1" name="脚本任务" g="100,100,100,80">
    <action type="script" language="groovy"><code>println &quot;Hello World&quot;</code></action>
  </scriptTask>
</bpm>`

    const parseResult = parseTbbpmXml(originalXml)
    expect(parseResult.success).toBe(true)

    const scriptTask = parseResult.data?.nodes[0] as TbbpmNode
    expect(scriptTask?.properties.action?.actionType).toBe('script')
    expect(scriptTask?.properties.action?.language).toBe('groovy')
    expect(scriptTask?.properties.action?.source).toBe('println "Hello World"')

    const generatedXml = generateTbbpmXml(parseResult.data!)
    expect(generatedXml).toContain('language="groovy"')
  })

  test('rejects legacy direct language action types', () => {
    const result = parseTbbpmXml(`<bpm code="invalid_script">
      <autoTask id="task1" g="0,0,100,80">
        <action type="groovy">
          <actionDefinition><code>println "ignored"</code></actionDefinition>
        </action>
      </autoTask>
    </bpm>`)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('Unsupported action type')
  })

  test.each([
    {
      owner: 'action',
      xml: `<bpm code="invalid_action_mapping">
        <scriptTask id="task" g="0,0,100,80">
          <action type="script" language="java">
            <var name="value" dataType="java.lang.Integer" inOutType="inner"/>
            <code>value = 1;</code>
          </action>
        </scriptTask>
      </bpm>`,
    },
    {
      owner: 'bpmCall',
      xml: `<bpm code="invalid_sub_mapping">
        <bpmCall id="sub" g="0,0,100,80" code="child" classpath="flows/child.bpm">
          <var name="value" dataType="java.lang.Integer" inOutType="inner"/>
        </bpmCall>
      </bpm>`,
    },
  ])('$owner 拒绝已移除的 var 映射元素', ({ xml }) => {
    const result = parseTbbpmXml(xml)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('child "var"')
  })

  test('timerTask 在根流程和 subBpm 中保持往返', () => {
    const originalXml = `<bpm code="timer_roundtrip">
      <start id="start"><transition to="scope"/></start>
      <subBpm id="scope">
        <transition to="end"/>
        <start id="scopeStart"><transition to="timer"/></start>
        <timerTask id="timer" durationExpression="delay">
          <transition to="scopeEnd"/>
        </timerTask>
        <end id="scopeEnd"/>
      </subBpm>
      <timerTask id="rootTimer" wakeAtExpression="deadline">
        <transition to="end"/>
      </timerTask>
      <end id="end"/>
    </bpm>`

    const parsed = requireParsedProcess(parseTbbpmXml(originalXml))
    expect((parsed.nodes.find((node) => node.id === 'timer') as TbbpmNode).properties).toEqual({
      durationExpression: 'delay',
    })
    expect((parsed.nodes.find((node) => node.id === 'rootTimer') as TbbpmNode).properties).toEqual({
      wakeAtExpression: 'deadline',
    })

    const generated = generateTbbpmXml(parsed)
    expect(generated).toContain('<timerTask id="timer"')
    expect(generated).toContain('durationExpression="delay"')
    expect(requireParsedProcess(parseTbbpmXml(generated)).nodes).toHaveLength(7)
  })

  test('subBpm 中的设计期 note 不参与可达性', () => {
    const parsed = requireParsedProcess(
      parseTbbpmXml(`<bpm code="sub_bpm_note">
        <start id="start"><transition to="scope"/></start>
        <subBpm id="scope">
          <transition to="end"/>
          <start id="scopeStart"><transition to="scopeEnd"/></start>
          <end id="scopeEnd"/>
          <note id="explanation" comment="design only"/>
        </subBpm>
        <end id="end"/>
      </bpm>`)
    )

    expect(generateTbbpmXml(parsed)).toContain('<note id="explanation"')
  })

  test('while 和 foreach 使用显式局部边界并可往返', () => {
    const originalXml = `<bpm code="loops" name="Loops">
  <var name="items" dataType="java.util.List" inOutType="param"/>
  <var name="slot" dataType="java.lang.String" inOutType="inner"/>
  <var name="results" dataType="java.util.List" inOutType="return"/>
  <start id="rootStart"><transition to="while"/></start>
  <while id="while" condition="active" index="iteration" maxIterations="100">
    <transition to="forEach"/>
    <start id="whileStart"><transition to="whileEnd"/></start>
    <end id="whileEnd"/>
  </while>
  <foreach id="forEach" collection="items" item="item"
               itemType="java.lang.String" index="index" execution="parallel">
    <output target="results" source="slot"/>
    <transition to="rootEnd"/>
    <start id="eachStart"><transition to="eachEnd"/></start>
    <end id="eachEnd"/>
  </foreach>
  <end id="rootEnd"/>
</bpm>`
    const parsed = requireParsedProcess(parseTbbpmXml(originalXml))
    const whileNode = parsed.nodes.find((node) => node.id === 'while') as TbbpmNode
    const foreach = parsed.nodes.find((node) => node.id === 'forEach') as TbbpmNode
    expect(whileNode.type).toBe('while')
    expect(whileNode.properties.condition).toBe('active')
    expect(foreach.type).toBe('foreach')
    expect(foreach.properties.collection).toBe('items')
    expect(foreach.properties.output).toEqual({ target: 'results', source: 'slot' })

    const generated = generateTbbpmXml(parsed)
    expect(generated).toContain('<while id="while"')
    expect(generated).toContain('<foreach id="forEach"')
    expect(generated).toContain('<output target="results" source="slot"/>')
    expect(requireParsedProcess(parseTbbpmXml(generated)).nodes).toHaveLength(8)

    foreach.properties.execution = 'sequential'
    expect(generateTbbpmXml(parsed)).not.toContain('execution="sequential"')

    whileNode.properties.index = ''
    foreach.properties.index = ''
    const withoutOptionalVariables = generateTbbpmXml(parsed)
    expect(withoutOptionalVariables).not.toContain('index="iteration"')
    expect(withoutOptionalVariables).not.toContain('index=')

    parsed.variables!.find((variable) => variable.name === 'slot')!.inOutType = 'return'
    expect(() => generateTbbpmXml(parsed)).toThrow('output source must be inner')
  })

  test('循环体必须有且只有一个 start 和 end', () => {
    const parsed = requireParsedProcess(
      parseTbbpmXml(`<bpm code="invalid_loop">
        <while id="loop" condition="active" maxIterations="10">
          <start id="one"/><start id="two"/><end id="end"/>
        </while>
      </bpm>`)
    )
    expect(() => generateTbbpmXml(parsed)).toThrow('exactly one start and one end')
  })

  test.each(['2147483648', '1e2', '1.5'])('while 拒绝非法 maxIterations %s', (limit) => {
    const result = parseTbbpmXml(`<bpm code="invalid_loop_limit">
      <while id="loop" condition="active" maxIterations="${limit}">
        <start id="start"><transition to="end"/></start><end id="end"/>
      </while>
    </bpm>`)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('between 1 and 2147483647')
  })

  test.each([
    ['condition', 'maxIterations="10"'],
    ['maxIterations', 'condition="active"'],
  ])('while 拒绝缺失必填属性 %s', (attribute, attributes) => {
    const result = parseTbbpmXml(`<bpm code="invalid_loop_attribute">
      <while id="loop" ${attributes}>
        <start id="start"><transition to="end"/></start><end id="end"/>
      </while>
    </bpm>`)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain(`non-blank ${attribute} attribute`)
  })

  test.each([
    ['index', 'while', 'condition="active" maxIterations="10" index=""'],
    ['index', 'foreach', 'collection="items" item="item" itemType="java.lang.String" index=""'],
  ])('循环节点拒绝空白可选属性 %s', (attribute, nodeType, attributes) => {
    const result = parseTbbpmXml(`<bpm code="invalid_optional_loop_attribute">
      <${nodeType} id="loop" ${attributes}>
        <start id="start"><transition to="end"/></start><end id="end"/>
      </${nodeType}>
    </bpm>`)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain(`non-blank ${attribute} attribute`)
  })

  test('foreach output 必须位于转移和循环体之前', () => {
    const result = parseTbbpmXml(`<bpm code="invalid_output_order">
      <foreach id="loop" collection="items" item="item"
                   itemType="java.lang.String">
        <transition to="end"/>
        <output target="results" source="slot"/>
        <start id="start"><transition to="bodyEnd"/></start><end id="bodyEnd"/>
      </foreach>
      <end id="end"/>
    </bpm>`)

    expect(result.success).toBe(false)
    expect(result.error?.message).toContain('output must precede transitions and body nodes')
  })

  test('多节点多连接往返一致性', () => {
    // Note: XML attribute values with '>' are technically valid per XML spec but some
    // parsers reject them. Use '&gt;' and '&lt;' to ensure portable parse results.
    const originalXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="complex_flow" name="复杂流程">
  <start id="start1" name="开始" g="100,100,80,50">
    <transition to="exclusive1"/>
  </start>
  <exclusive id="exclusive1" name="决策" g="250,100,60,60">
    <transition to="task1" name="大额订单" condition="amount &gt; 1000"/>
    <transition to="task2" name="小额订单" condition="amount &lt;= 1000"/>
  </exclusive>
  <autoTask id="task1" name="大额处理" g="400,50,100,80">
    <action type="java" class="com.example.LargeOrderHandler" method="process"/>
    <transition to="end1"/>
  </autoTask>
  <autoTask id="task2" name="小额处理" g="400,150,100,80">
    <action type="java" class="com.example.SmallOrderHandler" method="process"/>
    <transition to="end1"/>
  </autoTask>
  <end id="end1" name="结束" g="600,100,80,50"/>
</bpm>`

    const parseResult = parseTbbpmXml(originalXml)
    expect(parseResult.success).toBe(true)
    expect(parseResult.data?.nodes.length).toBe(5)
    // 5 transitions: start→exclusive, exclusive→task1, exclusive→task2, task1→end, task2→end
    expect(parseResult.data?.connections.length).toBe(5)

    const exclusive = parseResult.data?.nodes.find((n) => n.id === 'exclusive1') as TbbpmNode
    expect(exclusive?.type).toBe('exclusive')

    const exclusiveConnections = parseResult.data?.connections.filter(
      (c) => c.sourceId === 'exclusive1'
    ) as TbbpmConnection[] | undefined
    expect(exclusiveConnections?.length).toBe(2)
    expect(exclusiveConnections?.[0].name).toBe('大额订单')
    expect(exclusiveConnections?.[0].condition).toBe('amount > 1000')

    const generatedXml = generateTbbpmXml(parseResult.data!)
    expect(generatedXml).toContain('exclusive')
    expect(generatedXml).toContain('大额订单')
    // Generator escapes '>' to '&gt;' in attribute values.
    expect(generatedXml).toContain('condition="amount &gt; 1000"')
    expect(generatedXml).not.toContain('${amount')
  })

  test('解析错误处理', () => {
    const invalidXml = 'not a valid xml'

    const parseResult = parseTbbpmXml(invalidXml)
    expect(parseResult.success).toBe(false)
    expect(parseResult.error).toBeDefined()
    expect(parseResult.error?.code).toBe('PARSE_ERROR')
  })

  test('缺少bpm根元素错误', () => {
    const invalidXml = `<?xml version="1.0" encoding="UTF-8"?>
<process>
  <start name="start1"/>
</process>`

    const parseResult = parseTbbpmXml(invalidXml)
    expect(parseResult.success).toBe(false)
    expect(parseResult.error?.code).toBe('INVALID_ROOT')
  })

  test('节点引用验证（可选）', () => {
    const invalidXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="test" name="Test">
  <start id="start1" name="开始" g="100,100,80,50">
    <transition to="nonexistent_node"/>
  </start>
</bpm>`

    const parseResult = parseTbbpmXml(invalidXml, { validate: true })
    expect(parseResult.success).toBe(true)
    expect(parseResult.warnings).toBeDefined()
    expect(parseResult.warnings!.length).toBeGreaterThan(0)
    expect(parseResult.warnings![0].code).toBe('INVALID_TARGET_REF')
  })

  test('节点引用验证发现跨容器转换', () => {
    const xml = `<bpm code="test">
  <start id="rootStart">
    <transition to="nestedEnd"/>
  </start>
  <subBpm id="scope">
    <start id="nestedStart">
      <transition to="nestedEnd"/>
    </start>
    <end id="nestedEnd"/>
  </subBpm>
  <end id="rootEnd"/>
</bpm>`

    const result = parseTbbpmXml(xml, { validate: true })

    expect(result.success).toBe(true)
    expect(result.warnings).toContainEqual(expect.objectContaining({ code: 'CROSS_CONTAINER_REF' }))
  })
})

// ==================== 边界情况测试 ====================

describe('边界情况测试', () => {
  test('BPMN: 特殊字符转义', () => {
    const definition: UnifiedProcessDefinition = {
      id: 'test',
      code: 'test',
      name: 'Test Process',
      type: 'BPMN',
      nodes: [
        {
          id: 'Task_1',
          type: 'bpmn:ServiceTask',
          name: 'Task with <special> & "chars"',
          position: { x: 100, y: 100 },
          size: { width: 100, height: 80 },
          properties: {},
        },
      ],
      connections: [],
    }

    const xml = generateBpmnXml(definition)
    expect(xml).toContain('&lt;special&gt;')
    expect(xml).toContain('&amp;')
    expect(xml).toContain('&quot;')
  })

  test('TBBPM: 特殊字符转义', () => {
    const definition: UnifiedProcessDefinition = {
      id: 'test',
      code: 'test',
      name: 'Test Process',
      type: 'TBBPM',
      nodes: [
        {
          id: 'task1',
          type: 'autoTask',
          name: 'Task with <special> & "chars"',
          position: { x: 100, y: 100 },
          size: { width: 100, height: 80 },
          properties: {
            action: {
              actionType: 'java',
              className: 'com.example.Test',
              method: 'execute',
            },
          },
        },
      ],
      connections: [],
    }

    const xml = generateTbbpmXml(definition)
    expect(xml).toContain('&lt;special&gt;')
    expect(xml).toContain('&amp;')
    expect(xml).toContain('&quot;')
  })

  test('BPMN: 空连接线处理', () => {
    const definition: UnifiedProcessDefinition = {
      id: 'test',
      code: 'test',
      name: 'Test',
      type: 'BPMN',
      nodes: [
        {
          id: 'Start_1',
          type: 'bpmn:StartEvent',
          name: 'Start',
          position: { x: 100, y: 100 },
          properties: {},
        },
      ],
      connections: [],
    }

    const xml = generateBpmnXml(definition)
    expect(xml).toContain('<bpmn:startEvent')
    expect(xml).not.toContain('<bpmn:sequenceFlow')
  })

  test('TBBPM: 空连接线处理', () => {
    const definition: UnifiedProcessDefinition = {
      id: 'test',
      code: 'test',
      name: 'Test',
      type: 'TBBPM',
      nodes: [
        {
          id: 'start1',
          type: 'start',
          name: 'Start',
          position: { x: 100, y: 100 },
          properties: {},
        },
      ],
      connections: [],
    }

    const xml = generateTbbpmXml(definition)
    expect(xml).toContain('<start')
    expect(xml).not.toContain('<transition')
  })
})

// ==================== 性能测试 ====================

describe('性能测试', () => {
  test('BPMN: 大规模流程解析性能', () => {
    // 生成100个节点的流程
    let xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  targetNamespace="http://compileflow.alibaba.com/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">`

    for (let i = 0; i < 100; i++) {
      xml += `\n    <bpmn:receiveTask id="Task_${i}" name="Task ${i}"/>`
    }

    xml += `\n  </bpmn:process>\n</bpmn:definitions>`

    const startTime = Date.now()
    const parseResult = parseBpmnXml(xml)
    const parseTime = Date.now() - startTime

    expect(parseResult.success).toBe(true)
    expect(parseResult.data?.nodes.length).toBe(100)
    expect(parseTime).toBeLessThan(1000) // 应该在1秒内完成
  })

  test('TBBPM: 大规模流程解析性能', () => {
    // 生成100个节点的流程
    let xml = `<?xml version="1.0" encoding="UTF-8"?>\n<bpm code="test" name="Test">`

    for (let i = 0; i < 100; i++) {
      xml += `\n  <autoTask id="task${i}" name="Task ${i}" g="${i * 10},100,100,80">
    <action type="java" class="com.example.Task${i}" method="execute"/>
  </autoTask>`
    }

    xml += `\n</bpm>`

    const startTime = Date.now()
    const parseResult = parseTbbpmXml(xml)
    const parseTime = Date.now() - startTime

    expect(parseResult.success).toBe(true)
    expect(parseResult.data?.nodes.length).toBe(100)
    expect(parseTime).toBeLessThan(1000) // 应该在1秒内完成
  })
})
