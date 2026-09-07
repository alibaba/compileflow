import { describe, expect, test } from 'vitest'

import type { MultiInstanceLoopCharacteristics } from '../../types/bpmnNodeTypes'
import type { BpmnNode, BpmnProcessDefinition } from '../../types/flowDefinition'
import { generateBpmnXml, parseBpmnXml } from '../bpmnXmlCodec'

const HEADER = `<bpmn:definitions
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:cf="http://www.compileflow.org"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  id="Definitions_test"
  targetNamespace="urn:compileflow:test">`

function xml(processContent: string, definitionsContent = ''): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
${HEADER}
  ${definitionsContent}
  <bpmn:process id="process" name="Process" isExecutable="true">
    ${processContent}
  </bpmn:process>
</bpmn:definitions>`
}

function draft(overrides: Partial<BpmnProcessDefinition> = {}): BpmnProcessDefinition {
  return {
    id: 'process',
    code: 'process',
    name: 'Process',
    type: 'BPMN',
    nodes: [],
    connections: [],
    variables: [],
    ...overrides,
  }
}

describe('bpmnXmlCodec', () => {
  test('rejects a renamed process code colliding with preserved definitions identity', () => {
    const parsed = parseBpmnXml(xml(''))
    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(() => generateBpmnXml(parsed.data!)).not.toThrow()
    const renamed = { ...parsed.data!, code: 'Definitions_test' }
    expect(() => generateBpmnXml(renamed)).toThrow('Duplicate BPMN id: Definitions_test')
  })

  test('rejects collaboration participants outside the editable profile', () => {
    const parsed = parseBpmnXml(
      xml(
        '',
        '<bpmn:collaboration id="collab"><bpmn:participant id="pool" processRef="process"/></bpmn:collaboration>'
      )
    )
    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('collaboration')
  })

  test('preserves accepted definitions metadata through visual editing', () => {
    const source = xml('').replace(
      'id="Definitions_test"',
      'id="Definitions_test" exporter="Example" exporterVersion="2" typeLanguage="urn:types" expressionLanguage="urn:expressions" xsi:schemaLocation="urn:example example.xsd"'
    )
    const parsed = parseBpmnXml(source)
    expect(parsed.success, parsed.error?.message).toBe(true)
    const output = new DOMParser().parseFromString(
      generateBpmnXml(parsed.data!),
      'application/xml'
    ).documentElement
    const input = new DOMParser().parseFromString(source, 'application/xml').documentElement
    for (const attribute of [
      'id',
      'exporter',
      'exporterVersion',
      'typeLanguage',
      'expressionLanguage',
      'xsi:schemaLocation',
    ]) {
      expect(output.getAttribute(attribute), attribute).toBe(input.getAttribute(attribute))
    }
  })
  test('preserves significant script and documentation boundary whitespace', () => {
    const text = '\n  value + 1;\n  '
    const parsed = parseBpmnXml(
      xml(`<bpmn:documentation>${text}</bpmn:documentation>
      <bpmn:scriptTask id="script" scriptFormat="qlexpress">
        <bpmn:documentation>${text}</bpmn:documentation>
        <bpmn:script><![CDATA[${text}]]></bpmn:script>
      </bpmn:scriptTask>`)
    )
    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.description).toBe(text)
    expect(parsed.data?.nodes[0].properties.script).toBe(text)
    const reparsed = parseBpmnXml(generateBpmnXml(parsed.data!))
    expect(reparsed.data?.nodes[0].documentation).toBe(text)
    expect(reparsed.data?.nodes[0].properties.script).toBe(text)
  })
  test('assigns non-overlapping fallback geometry when BPMN DI is absent', () => {
    const parsed = parseBpmnXml(
      xml(`<bpmn:startEvent id="start"/>
      <bpmn:scriptTask id="script" scriptFormat="qlexpress">
        <bpmn:script>1</bpmn:script>
      </bpmn:scriptTask>
      <bpmn:endEvent id="end"/>
      <bpmn:sequenceFlow id="to_script" sourceRef="start" targetRef="script"/>
      <bpmn:sequenceFlow id="to_end" sourceRef="script" targetRef="end"/>`)
    )

    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.nodes.map((node) => node.position)).toEqual([
      { x: 80, y: 80 },
      { x: 196, y: 80 },
      { x: 376, y: 80 },
    ])
  })

  test('preserves empty String defaults at process and action boundaries', () => {
    const source = xml(`
      <bpmn:extensionElements>
        <cf:var name="rootValue" dataType="java.lang.String"
                inOutType="inner" defaultValue=""/>
      </bpmn:extensionElements>
      <bpmn:serviceTask id="service">
        <bpmn:extensionElements>
          <cf:action type="java" class="com.example.Service" method="execute">
            <cf:input target="value" dataType="java.lang.String" defaultValue=""/>
          </cf:action>
        </bpmn:extensionElements>
      </bpmn:serviceTask>
    `)

    const parsed = parseBpmnXml(source)

    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.variables?.[0]?.defaultValue).toBe('')
    const service = parsed.data?.nodes.find(
      (candidate) => candidate.type === 'bpmn:ServiceTask'
    ) as BpmnNode | undefined
    expect(service?.properties.action).toMatchObject({
      mappings: [{ defaultValue: '' }],
    })

    const generated = generateBpmnXml(parsed.data!)
    expect(generated.match(/defaultValue=""/g)).toHaveLength(2)
    expect(parseBpmnXml(generated).data?.variables?.[0]?.defaultValue).toBe('')
  })

  test('rejects mapping defaults that generated action code would ignore', () => {
    const parsed = parseBpmnXml(
      xml(`
      <bpmn:serviceTask id="service">
        <bpmn:extensionElements>
          <cf:action type="java" class="com.example.Service" method="execute">
            <cf:input target="value" dataType="java.lang.String"
                      source="value" defaultValue="fallback"/>
          </cf:action>
        </bpmn:extensionElements>
      </bpmn:serviceTask>
    `)
    )

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('must not declare both source and defaultValue')
  })

  test('rejects dataType on called-process inputs', () => {
    const parsed = parseBpmnXml(
      xml(`
      <bpmn:callActivity id="call" calledElement="child.flow" cf:classpath="child.bpmn">
        <bpmn:extensionElements>
          <cf:input source="value" target="value" dataType="java.lang.String"/>
        </bpmn:extensionElements>
      </bpmn:callActivity>
    `)
    )

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('unsupported attribute "dataType"')
  })

  test('rejects invalid actions before emitting backend-invalid XML', () => {
    const flow = draft({
      nodes: [
        {
          ...node('service', 'bpmn:ServiceTask', 0, 0),
          properties: {
            action: {
              actionType: 'java',
              method: 'execute',
            },
          },
        },
      ],
    })

    expect(() => generateBpmnXml(flow)).toThrow('Invalid java action: action.missingClass')
  })

  test('rejects in-memory task properties on a pure gateway during export', () => {
    const flow = draft({
      nodes: [
        node('start', 'bpmn:StartEvent', 0, 0),
        {
          ...node('route', 'bpmn:ExclusiveGateway', 100, 0),
          properties: {
            invocationPolicy: { timeout: 'PT1S' },
          } as unknown as BpmnNode['properties'],
        },
        node('end', 'bpmn:EndEvent', 200, 0),
      ],
      connections: [
        { id: 'to_route', sourceId: 'start', targetId: 'route' },
        { id: 'to_end', sourceId: 'route', targetId: 'end' },
      ],
    })

    expect(() => generateBpmnXml(flow)).toThrow(
      'contains inapplicable properties: invocationPolicy'
    )
  })

  test('rejects unsupported executable BPMN attributes at the model boundary', () => {
    const nonInterrupting = draft({
      nodes: [
        node('start', 'bpmn:StartEvent', 0, 0, {
          isInterrupting: false,
        } as unknown as BpmnNode['properties']),
      ],
    })
    expect(() => generateBpmnXml(nonInterrupting)).toThrow(
      'contains inapplicable properties: isInterrupting'
    )

    const nonImmediate = parseBpmnXml(
      xml(`<bpmn:startEvent id="start"/>
      <bpmn:endEvent id="end"/>
      <bpmn:sequenceFlow id="flow" sourceRef="start" targetRef="end" isImmediate="false"/>`)
    )
    expect(nonImmediate.success).toBe(false)
    expect(nonImmediate.error?.message).toContain('cannot declare isImmediate="false"')
  })

  test('round-trips the CompileFlow action, variable, message, and DI contract', () => {
    const flow = draft({
      description: 'Executable process',
      messages: [{ id: 'continue-message', name: 'continue-message' }],
      variables: [
        { name: 'input', type: 'java.lang.String', inOutType: 'param' },
        { name: 'result', type: 'java.lang.String', inOutType: 'return' },
      ],
      nodes: [
        node('start', 'bpmn:StartEvent', 100, 100),
        {
          ...node('service', 'bpmn:ServiceTask', 220, 80),
          documentation: 'Invoke service',
          properties: {
            action: {
              actionType: 'java',
              className: 'com.example.Service',
              method: 'execute',
              mappings: [
                {
                  target: 'value',
                  dataType: 'java.lang.String',
                  direction: 'input',
                  source: 'input',
                },
                {
                  dataType: 'java.lang.String',
                  direction: 'output',
                  target: 'result',
                },
              ],
              invocationPolicy: { timeout: 'PT2M', attemptTimeout: 'PT30S', maxAttempts: 2 },
            },
          },
        },
        {
          ...node('receive', 'bpmn:ReceiveTask', 380, 80),
          properties: { messageRef: 'continue-message' },
        },
        node('end', 'bpmn:EndEvent', 540, 100),
      ],
      connections: [
        { id: 'to_service', sourceId: 'start', targetId: 'service' },
        {
          id: 'to_receive',
          sourceId: 'service',
          targetId: 'receive',
          waypoints: [
            { x: 320, y: 120 },
            { x: 350, y: 140 },
            { x: 380, y: 120 },
          ],
        },
        { id: 'to_end', sourceId: 'receive', targetId: 'end' },
      ],
    })

    const generated = generateBpmnXml(flow)
    const parsed = parseBpmnXml(generated)

    expect(generated).toContain(
      '<cf:action type="java" class="com.example.Service" method="execute">'
    )
    expect(generated).toContain('<cf:var name="input" dataType="java.lang.String"')
    expect(generated).toContain('<bpmn:message id="continue-message" name="continue-message"/>')
    expect(generated).toContain('<bpmndi:BPMNEdge')
    expect(generated.match(/<di:waypoint/g)).toHaveLength(7)
    expect(generated).not.toContain('camunda:')
    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.description).toBe('Executable process')
    expect(parsed.data?.variables).toHaveLength(2)
    expect(parsed.data?.messages).toEqual([{ id: 'continue-message', name: 'continue-message' }])
    const service = parsed.data?.nodes.find((candidate) => candidate.id === 'service') as
      | BpmnNode
      | undefined
    expect(service?.documentation).toBe('Invoke service')
    expect(service?.properties.action).toMatchObject({
      actionType: 'java',
      className: 'com.example.Service',
      method: 'execute',
    })
    expect(
      parsed.data?.connections.find((connection) => connection.id === 'to_receive')?.waypoints
    ).toEqual([
      { x: 320, y: 120 },
      { x: 350, y: 140 },
      { x: 380, y: 120 },
    ])
    expect(parsed.data?.nodes[0]?.size).toEqual({ width: 36, height: 36 })
  })

  test('rejects an unresolved receive-task message instead of inventing an event selector', () => {
    const flow = draft({
      nodes: [
        {
          ...node('receive', 'bpmn:ReceiveTask', 0, 0),
          properties: { messageRef: 'missing-message' },
        },
      ],
    })

    expect(() => generateBpmnXml(flow)).toThrow(
      'messageRef "missing-message" does not resolve to a message'
    )
  })

  test('parses script and call-activity mapped variables', () => {
    const source = xml(`
      <bpmn:extensionElements>
        <cf:var name="result" dataType="java.lang.String" inOutType="return"/>
      </bpmn:extensionElements>
      <bpmn:startEvent id="start"/>
      <bpmn:scriptTask id="script" scriptFormat="qlexpress" cf:execution="effect">
        <bpmn:extensionElements>
          <cf:output dataType="java.lang.String" target="result"/>
          <cf:effectPolicy recovery="manual" maxAttempts="1"/>
        </bpmn:extensionElements>
        <bpmn:script>value + "-done"</bpmn:script>
      </bpmn:scriptTask>
      <bpmn:callActivity id="call" calledElement="child" cf:classpath="flows/child.bpmn">
        <bpmn:extensionElements>
          <cf:output source="childResult" target="result"/>
        </bpmn:extensionElements>
      </bpmn:callActivity>
      <bpmn:endEvent id="end"/>
      <bpmn:sequenceFlow id="one" sourceRef="start" targetRef="script"/>
      <bpmn:sequenceFlow id="two" sourceRef="script" targetRef="call"/>
      <bpmn:sequenceFlow id="three" sourceRef="call" targetRef="end"/>`)

    const parsed = parseBpmnXml(source)

    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.nodes.find((node) => node.id === 'script')?.properties).toMatchObject({
      scriptFormat: 'qlexpress',
      script: 'value + "-done"',
      execution: 'effect',
      mappings: [{ direction: 'output', target: 'result' }],
      effectPolicy: { recovery: 'manual', maxAttempts: 1 },
    })
    expect(parsed.data?.nodes.find((node) => node.id === 'call')?.properties).toMatchObject({
      calledElement: 'child',
      classpath: 'flows/child.bpmn',
      mappings: [{ source: 'childResult', direction: 'output', target: 'result' }],
    })
    const generated = generateBpmnXml(parsed.data!)
    expect(generated).toContain('cf:execution="effect"')
    expect(generated).toContain('<cf:effectPolicy recovery="manual" maxAttempts="1"/>')
    expect(generated).not.toContain('cf:processRef')
  })

  test('rejects reconcile sources outside a native script Effect request', () => {
    const parsed = parseBpmnXml(
      xml(`
        <bpmn:extensionElements>
          <cf:var name="orderId" dataType="java.lang.String" inOutType="param"/>
        </bpmn:extensionElements>
        <bpmn:scriptTask id="script" scriptFormat="qlexpress" cf:execution="effect">
          <bpmn:extensionElements>
            <cf:input source="orderId" target="requestId" dataType="java.lang.String"/>
            <cf:effectPolicy recovery="reconcile" maxAttempts="1"
                             maxReconcileAttempts="1" recoveryDelay="PT1S">
              <cf:reconcileAction type="java" class="com.example.Query" method="execute">
                <cf:input source="requestId" target="requestId" dataType="java.lang.String"/>
              </cf:reconcileAction>
            </cf:effectPolicy>
          </bpmn:extensionElements>
          <bpmn:script>requestId</bpmn:script>
        </bpmn:scriptTask>`)
    )
    expect(parsed.success, parsed.error?.message).toBe(true)
    const script = parsed.data!.nodes[0]
    script.properties.effectPolicy!.reconcileAction!.inputs![0].source = 'missing'

    expect(() => generateBpmnXml(parsed.data!)).toThrow('effectPolicy.invalid')

    script.properties.mappings![0].source = '__cf_effect_id'
    script.properties.effectPolicy!.reconcileAction!.inputs![0].source = 'requestId'

    expect(() => generateBpmnXml(parsed.data!)).toThrow('effectPolicy.invalid')
  })

  test.each([
    ['version', 'version="child-v3"'],
    ['alias', 'alias="production"'],
    ['namespace', 'namespace="shared"'],
  ])('rejects call-activity %s qualifiers', (_name, qualifier) => {
    const parsed = parseBpmnXml(
      xml(`
        <bpmn:startEvent id="start"/>
        <bpmn:callActivity id="call" calledElement="child" cf:classpath="flows/child.bpmn">
          <bpmn:extensionElements>
            <cf:processRef ${qualifier}/>
          </bpmn:extensionElements>
        </bpmn:callActivity>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="one" sourceRef="start" targetRef="call"/>
        <bpmn:sequenceFlow id="two" sourceRef="call" targetRef="end"/>
      `)
    )

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('cf:processRef is not supported')
  })

  test.each([
    ['user task', '<bpmn:userTask id="user"/>', 'cannot safely edit BPMN element'],
    ['generic task', '<bpmn:task id="task"/>', 'cannot safely edit BPMN element'],
    [
      'Camunda execution attribute',
      '<bpmn:serviceTask xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="task" camunda:class="com.example.Service"/>',
      'Unsupported attribute',
    ],
  ])('rejects unsupported %s instead of dropping semantics', (_, element, message) => {
    const parsed = parseBpmnXml(xml(element))

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain(message)
  })

  test('round-trips nested embedded subprocess containers without flattening their graphs', () => {
    const parsed = parseBpmnXml(
      xml(`<bpmn:startEvent id="start"/>
      <bpmn:subProcess id="outer" triggeredByEvent="false">
        <bpmn:standardLoopCharacteristics testBefore="true" loopMaximum="2"/>
        <bpmn:startEvent id="outerStart"/>
        <bpmn:subProcess id="inner">
          <bpmn:startEvent id="innerStart"/>
          <bpmn:endEvent id="innerEnd"/>
          <bpmn:sequenceFlow id="innerProcess" sourceRef="innerStart" targetRef="innerEnd"/>
        </bpmn:subProcess>
        <bpmn:endEvent id="outerEnd"/>
        <bpmn:sequenceFlow id="outerIn" sourceRef="outerStart" targetRef="inner"/>
        <bpmn:sequenceFlow id="outerOut" sourceRef="inner" targetRef="outerEnd"/>
      </bpmn:subProcess>
      <bpmn:endEvent id="end"/>
      <bpmn:sequenceFlow id="enter" sourceRef="start" targetRef="outer"/>
      <bpmn:sequenceFlow id="leave" sourceRef="outer" targetRef="end"/>`)
    )

    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.nodes.find((node) => node.id === 'outerStart')?.parentId).toBe('outer')
    expect(parsed.data?.nodes.find((node) => node.id === 'innerStart')?.parentId).toBe('inner')
    expect(parsed.data?.nodes.find((node) => node.id === 'outer')?.properties).toMatchObject({
      loopCharacteristics: {
        type: 'standard',
        testBefore: true,
        loopMaximum: 2,
      },
    })

    const generated = generateBpmnXml(parsed.data!)
    const reparsed = parseBpmnXml(generated)

    expect(reparsed.success, reparsed.error?.message).toBe(true)
    expect(generated).not.toContain('isInterrupting=')
    expect(generated).not.toContain('triggeredByEvent=')
    expect(reparsed.data?.nodes.find((node) => node.id === 'outerStart')?.parentId).toBe('outer')
    expect(reparsed.data?.nodes.find((node) => node.id === 'innerStart')?.parentId).toBe('inner')
    expect(generated.indexOf('id="innerStart"')).toBeGreaterThan(
      generated.indexOf('<bpmn:subProcess id="inner"')
    )
    expect(generated.match(/id="innerStart"/g)).toHaveLength(1)
  })

  test('rejects cross-container sequence flows and incomplete subprocess boundaries', () => {
    const crossBoundary = parseBpmnXml(
      xml(`<bpmn:startEvent id="start"/>
      <bpmn:subProcess id="sub">
        <bpmn:startEvent id="nestedStart"/>
        <bpmn:endEvent id="nestedEnd"/>
        <bpmn:sequenceFlow id="nested" sourceRef="nestedStart" targetRef="nestedEnd"/>
      </bpmn:subProcess>
      <bpmn:endEvent id="end"/>
      <bpmn:sequenceFlow id="invalid" sourceRef="nestedStart" targetRef="end"/>`)
    )
    const incomplete = parseBpmnXml(
      xml(`<bpmn:startEvent id="start"/>
      <bpmn:subProcess id="sub"><bpmn:startEvent id="nestedStart"/></bpmn:subProcess>
      <bpmn:endEvent id="end"/>
      <bpmn:sequenceFlow id="enter" sourceRef="start" targetRef="sub"/>
      <bpmn:sequenceFlow id="leave" sourceRef="sub" targetRef="end"/>`)
    )

    expect(crossBoundary.success).toBe(false)
    expect(crossBoundary.error?.message).toContain('crosses process container boundaries')
    expect(incomplete.success).toBe(false)
    expect(incomplete.error?.message).toContain(
      'must contain exactly one direct startEvent and one direct endEvent'
    )
  })

  test('rejects foreign extension namespaces', () => {
    const parsed = parseBpmnXml(
      xml(`<bpmn:serviceTask id="task">
        <bpmn:extensionElements>
          <vendor:task xmlns:vendor="urn:vendor"/>
        </bpmn:extensionElements>
      </bpmn:serviceTask>`)
    )

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('Unsupported BPMN extension namespace')
  })

  test('enforces one global ID domain for definitions, process, messages, nodes, and flows', () => {
    const definitionsCollision = parseBpmnXml(
      xml('<bpmn:startEvent id="start"/>').replace('id="Definitions_test"', 'id="process"')
    )
    const messageCollision = parseBpmnXml(
      xml(
        '<bpmn:receiveTask id="message_order" messageRef="message_order"/>',
        '<bpmn:message id="message_order" name="Order"/>'
      )
    )

    expect(definitionsCollision.success).toBe(false)
    expect(definitionsCollision.error?.message).toContain('Duplicate BPMN id: process')
    expect(messageCollision.success).toBe(false)
    expect(messageCollision.error?.message).toContain('Duplicate BPMN id: message_order')

    expect(() =>
      generateBpmnXml(
        draft({
          messages: [{ id: 'task', name: 'Task message' }],
          nodes: [
            {
              ...node('task', 'bpmn:ReceiveTask', 100, 100),
              properties: { messageRef: 'task' },
            },
          ],
        })
      )
    ).toThrow('Duplicate BPMN id: task')
  })

  test.each([
    [
      'unsupported condition language',
      '<bpmn:conditionExpression language="javascript">${flag}</bpmn:conditionExpression>',
      "language must be 'java'",
    ],
    [
      'unsupported condition type',
      '<bpmn:conditionExpression xsi:type="bpmn:tExpression" language="java">${flag}</bpmn:conditionExpression>',
      "xsi:type must be 'tFormalExpression'",
    ],
    [
      'condition child element',
      '<bpmn:conditionExpression><bpmn:documentation>flag</bpmn:documentation></bpmn:conditionExpression>',
      'must not contain child elements',
    ],
  ])('rejects %s', (_, condition, message) => {
    const parsed = parseBpmnXml(
      xml(`<bpmn:startEvent id="start"/>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="flow" sourceRef="start" targetRef="end">
          ${condition}
        </bpmn:sequenceFlow>`)
    )

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain(message)
  })

  test('accepts sequential and parallel multi-instance loops with optional ordered output', () => {
    const loop = (sequential: boolean, output = '') =>
      xml(`<bpmn:serviceTask id="task">
        <bpmn:multiInstanceLoopCharacteristics isSequential="${sequential}"
          cf:collection="items" cf:item="item" ${output}/>
      </bpmn:serviceTask>`)

    expect(parseBpmnXml(loop(true)).success).toBe(true)
    expect(
      parseBpmnXml(loop(false, 'cf:target="results" cf:source="iterationResult"')).success
    ).toBe(true)
    expect(parseBpmnXml(loop(false)).success).toBe(true)
    expect(
      parseBpmnXml(
        xml(`<bpmn:serviceTask id="task">
          <bpmn:multiInstanceLoopCharacteristics isSequential="true"
            cf:collection="items" cf:item="item" vendor="ignored"/>
        </bpmn:serviceTask>`)
      ).error?.message
    ).toContain('Unsupported attribute')
    expect(
      parseBpmnXml(
        xml(`<bpmn:serviceTask id="task">
          <bpmn:standardLoopCharacteristics/>
        </bpmn:serviceTask>`)
      ).error?.message
    ).toContain('requires loopCondition or loopMaximum')
  })

  test.each(['1e2', '1.0', '2147483648'])(
    'rejects unsupported standard loopMaximum %s',
    (loopMaximum) => {
      const parsed = parseBpmnXml(
        xml(`<bpmn:serviceTask id="task">
          <bpmn:standardLoopCharacteristics loopMaximum="${loopMaximum}"/>
        </bpmn:serviceTask>`)
      )

      expect(parsed.success).toBe(false)
      expect(parsed.error?.message).toContain('loopMaximum')
    }
  )

  test.each(['index', 'itemType', 'target', 'source'])(
    'rejects blank cf:%s on multi-instance loops',
    (attribute) => {
      const parsed = parseBpmnXml(
        xml(`<bpmn:serviceTask id="task">
          <bpmn:multiInstanceLoopCharacteristics isSequential="true"
            cf:collection="items" cf:item="item" cf:${attribute}=" "/>
        </bpmn:serviceTask>`)
      )

      expect(parsed.success).toBe(false)
      expect(parsed.error?.message).toContain(`cf:${attribute} must not be blank when declared`)
    }
  )

  test('rejects receive-task loops because durable multi-instance waits are unsupported', () => {
    const parsed = parseBpmnXml(
      xml(`<bpmn:receiveTask id="wait" messageRef="message">
        <bpmn:standardLoopCharacteristics loopMaximum="2"/>
      </bpmn:receiveTask>`)
    )

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('Unsupported child')
  })

  test('enforces executable process and root variable boundaries', () => {
    const nonExecutable = parseBpmnXml(
      xml('<bpmn:startEvent id="start"/>').replace('isExecutable="true"', 'isExecutable="false"')
    )
    const mappedRoot = parseBpmnXml(
      xml(`<bpmn:extensionElements>
        <cf:var name="input" dataType="java.lang.String"
                contextVarName="request.value" inOutType="param"/>
      </bpmn:extensionElements>`)
    )

    expect(nonExecutable.success).toBe(false)
    expect(nonExecutable.error?.message).toContain('isExecutable="true"')
    expect(mappedRoot.success).toBe(false)
    expect(mappedRoot.error?.message).toContain('Unsupported attribute "contextVarName"')
  })

  test('generates Java formal expressions accepted by the engine contract', () => {
    const flow = draft({
      nodes: [
        node('start', 'bpmn:StartEvent', 100, 100),
        {
          ...node('split', 'bpmn:ExclusiveGateway', 180, 100),
          properties: { default: 'fallback' },
        },
        {
          ...node('task', 'bpmn:ServiceTask', 220, 80),
          properties: {
            action: {
              actionType: 'java',
              className: 'com.example.Service',
              method: 'execute',
            },
            loopCharacteristics: {
              type: 'standard',
              loopCondition: 'remaining > 0',
              testBefore: true,
            },
          },
        },
        {
          ...node('fallbackTask', 'bpmn:ServiceTask', 220, 180),
          properties: {
            action: {
              actionType: 'java',
              className: 'com.example.FallbackService',
              method: 'execute',
            },
          },
        },
        node('join', 'bpmn:ExclusiveGateway', 360, 100),
        node('end', 'bpmn:EndEvent', 440, 100),
      ],
      connections: [
        { id: 'to_split', sourceId: 'start', targetId: 'split' },
        {
          id: 'conditional',
          sourceId: 'split',
          targetId: 'task',
          condition: 'enabled',
        },
        { id: 'fallback', sourceId: 'split', targetId: 'fallbackTask' },
        { id: 'task_done', sourceId: 'task', targetId: 'join' },
        { id: 'fallback_done', sourceId: 'fallbackTask', targetId: 'join' },
        { id: 'done', sourceId: 'join', targetId: 'end' },
      ],
    })

    const generated = generateBpmnXml(flow)

    expect(generated).toContain(
      '<bpmn:conditionExpression xsi:type="bpmn:tFormalExpression" language="java">'
    )
    expect(generated).toContain(
      '<bpmn:loopCondition xsi:type="bpmn:tFormalExpression" language="java">'
    )
    expect(parseBpmnXml(generated).success).toBe(true)
  })

  test('rejects malformed, unsafe, and oversized XML', () => {
    expect(parseBpmnXml('<broken>').success).toBe(false)
    expect(
      parseBpmnXml(`<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
        <bpmn:definitions xmlns:bpmn="${BPMN_NAMESPACE}">&xxe;</bpmn:definitions>`).success
    ).toBe(false)
    const large = `<bpmn:task id="x"/>`.repeat(600_000)
    expect(
      parseBpmnXml(`${HEADER}<bpmn:process id="p">${large}</bpmn:process></bpmn:definitions>`)
        .success
    ).toBe(false)
  })

  test('requires the canonical BPMN namespace and exactly one process', () => {
    expect(parseBpmnXml('<definitions><process id="p"/></definitions>').error?.code).toBe(
      'INVALID_ROOT'
    )
    expect(parseBpmnXml(`${HEADER}</bpmn:definitions>`).error?.code).toBe('INVALID_PROCESS_COUNT')
  })

  test('writes ordered-output parallel multi-instance execution', () => {
    const parallelLoop: MultiInstanceLoopCharacteristics = {
      type: 'multiInstance',
      isSequential: false,
      collection: 'items',
      item: 'item',
      target: 'results',
      source: 'iterationResult',
    }
    const flow = draft({
      nodes: [
        {
          ...node('task', 'bpmn:ServiceTask', 100, 100),
          properties: {
            loopCharacteristics: parallelLoop,
          },
        },
      ],
    })

    const generated = generateBpmnXml(flow)

    expect(generated).toContain('isSequential="false"')
    expect(generated).toContain('cf:target="results"')
    expect(generated).toContain('cf:source="iterationResult"')
  })
})

const BPMN_NAMESPACE = 'http://www.omg.org/spec/BPMN/20100524/MODEL'

function node(
  id: string,
  type: BpmnNode['type'],
  x: number,
  y: number,
  properties: BpmnNode['properties'] = {}
): BpmnNode {
  return {
    id,
    type,
    name: id,
    position: { x, y },
    properties,
  }
}
