import type { Example } from '@/shared/contracts'

const mockExamples: readonly Example[] = [
  {
    id: 'learn.tbbpm.greeting',
    name: 'TBBPM Greeting',
    modelType: 'TBBPM',
    level: 0,
    duration: '5 minutes',
    difficulty: 1,
    description: 'Build a return value with a self-contained QL Script action.',
    category: 'basics',
    tags: ['TBBPM', 'QL Script', 'Variables'],
    whatYouWillLearn: [
      'Declare process input and return variables',
      'Connect start, task, and end nodes',
      'Map an action result into the process context',
    ],
    keyConcepts: ['TBBPM document', 'Script action', 'return variable'],
    code: `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="learn.tbbpm.greeting" name="TBBPM Greeting"
     description="Build a greeting with a QL Script action">
    <var name="name" dataType="java.lang.String"
         inOutType="param" defaultValue="World"/>
    <var name="message" dataType="java.lang.String" inOutType="return"/>

    <start id="start" name="Start" g="40,40,32,32">
        <transition to="buildGreeting"/>
    </start>
    <scriptTask id="buildGreeting" name="Build greeting" g="120,32,140,48">
        <action type="script" language="qlexpress">
            <input target="name" dataType="java.lang.String" source="name"/>
            <output dataType="java.lang.String" target="message"/>
            <code><![CDATA["Hello, " + name]]></code>
        </action>
        <transition to="end"/>
    </scriptTask>
    <end id="end" name="End" g="320,40,32,32"/>
</bpm>`,
    overview:
      'This is the smallest useful TBBPM definition: one start node, one executable task, and one end node. It has no application-specific class dependency, so the built-in QL Script can be preflighted and executed as-is.',
    explanation:
      'The missing `name` input uses the declared `World` default. The QL Script evaluates a string and the action output maps that value to the root `message` variable.',
    nextSteps:
      'Add a string input variable with a default value, map it into the action, and build a personalized greeting.',
    documentation:
      'See the [TBBPM specification](https://github.com/alibaba/compileflow/blob/master/docs/en/specifications/tbbpm.md) and the [node support matrix](https://github.com/alibaba/compileflow/blob/master/docs/en/node-support.md).',
  },
  {
    id: 'learn.bpmn.routing',
    name: 'BPMN Amount Routing',
    modelType: 'BPMN',
    level: 1,
    duration: '10 minutes',
    difficulty: 2,
    description: 'Route an amount through an exclusive gateway and return the selected path.',
    category: 'business',
    tags: ['BPMN', 'Exclusive Gateway', 'QL Script'],
    whatYouWillLearn: [
      'Declare CompileFlow variables in BPMN extension elements',
      'Use mutually exclusive sequence-flow conditions',
      'Use Script Tasks for definition-owned inline code',
    ],
    keyConcepts: ['BPMN subset', 'exclusiveGateway', 'scriptTask'],
    code: `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:cf="http://www.compileflow.org"
             targetNamespace="http://www.compileflow.org/examples">
  <process id="learn.bpmn.routing" name="BPMN Amount Routing" isExecutable="true">
    <extensionElements>
      <cf:var name="amount" dataType="java.lang.Integer"
              inOutType="param" defaultValue="150"/>
      <cf:var name="route" dataType="java.lang.String" inOutType="return"/>
    </extensionElements>

    <startEvent id="start" name="Start"/>
    <sequenceFlow id="toDecision" sourceRef="start" targetRef="decision"/>
    <exclusiveGateway id="decision" name="Amount decision"/>
    <sequenceFlow id="highValue" sourceRef="decision" targetRef="review">
      <conditionExpression xsi:type="tFormalExpression" language="java"><![CDATA[amount >= 100]]></conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="standardValue" sourceRef="decision" targetRef="automatic">
      <conditionExpression xsi:type="tFormalExpression" language="java"><![CDATA[amount < 100]]></conditionExpression>
    </sequenceFlow>
    <scriptTask id="review" name="Manual review route" scriptFormat="qlexpress">
      <extensionElements>
        <cf:output dataType="java.lang.String" target="route"/>
      </extensionElements>
      <script><![CDATA["manual-review"]]></script>
    </scriptTask>
    <scriptTask id="automatic" name="Automatic route" scriptFormat="qlexpress">
      <extensionElements>
        <cf:output dataType="java.lang.String" target="route"/>
      </extensionElements>
      <script><![CDATA["automatic"]]></script>
    </scriptTask>
    <sequenceFlow id="reviewToEnd" sourceRef="review" targetRef="end"/>
    <sequenceFlow id="automaticToEnd" sourceRef="automatic" targetRef="end"/>
    <endEvent id="end" name="End"/>
  </process>
</definitions>`,
    overview:
      'This example uses only the CompileFlow-supported BPMN subset. It models a synchronous decision, not a durable human task: an external task system must own human-task state and lifecycle.',
    explanation:
      'The default amount is 150, so an empty input selects `manual-review`. Supplying an amount below 100 selects `automatic`. Both branches converge on the same end event.',
    nextSteps:
      'Run the flow with `{"amount": 50}` and compare the returned `route`, then add a third policy through an explicit default sequence flow.',
    documentation:
      'See the [Process model](https://github.com/alibaba/compileflow/blob/master/docs/en/architecture/process-model.md) for BPMN extension placement and supported-node rules.',
  },
  {
    id: 'learn.tbbpm.parallel',
    name: 'TBBPM Parallel Calculation',
    modelType: 'TBBPM',
    level: 1,
    duration: '12 minutes',
    difficulty: 2,
    description:
      'Run two independent calculations on separate logical paths and join their outputs.',
    category: 'business',
    tags: ['TBBPM', 'Parallel Gateway', 'Concurrency'],
    whatYouWillLearn: [
      'Model a parallel split and structural join',
      'Keep branch outputs in distinct process variables',
      'Use self-contained actions without application classes',
    ],
    keyConcepts: ['parallel split', 'parallel join', 'branch output'],
    code: `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="learn.tbbpm.parallel" name="TBBPM Parallel Calculation"
     description="Calculate two independent values in parallel">
    <var name="leftResult" dataType="java.lang.Integer" inOutType="return"/>
    <var name="rightResult" dataType="java.lang.Integer" inOutType="return"/>

    <start id="start" name="Start" g="40,80,32,32">
        <transition to="fork"/>
    </start>
    <parallel id="fork" name="Fork" g="120,72,48,48">
        <transition to="left"/>
        <transition to="right"/>
    </parallel>
    <scriptTask id="left" name="Left calculation" g="220,32,140,48">
        <action type="script" language="qlexpress" execution="replayable">
            <output dataType="java.lang.Integer" target="leftResult"/>
            <code>20</code>
        </action>
        <transition to="join"/>
    </scriptTask>
    <scriptTask id="right" name="Right calculation" g="220,120,140,48">
        <action type="script" language="qlexpress" execution="replayable">
            <output dataType="java.lang.Integer" target="rightResult"/>
            <code>22</code>
        </action>
        <transition to="join"/>
    </scriptTask>
    <parallel id="join" name="Join" g="420,72,48,48">
        <transition to="end"/>
    </parallel>
    <end id="end" name="End" g="520,80,32,32"/>
</bpm>`,
    overview:
      'Parallel branches are appropriate only when their work and outputs are independent. This example writes to two separate return variables and joins before completion.',
    explanation:
      'The split starts both calculations through the engine execution executor. The join waits for both paths, then the result map exposes `leftResult=20` and `rightResult=22`.',
    nextSteps:
      'Replace one constant with a mapped input and compare the behavior with a sequential pair of tasks.',
    documentation:
      'See [Advanced Features](https://github.com/alibaba/compileflow/blob/master/docs/en/advanced-features.md) for concurrency behavior.',
  },
  {
    id: 'learn.bpmn.script-task',
    name: 'BPMN Script Task',
    modelType: 'BPMN',
    level: 0,
    duration: '8 minutes',
    difficulty: 1,
    description: 'Author definition-owned inline code in a standard BPMN Script Task.',
    category: 'basics',
    tags: ['BPMN', 'Script Task', 'Variables'],
    whatYouWillLearn: [
      'Keep BPMN control flow in standard elements',
      'Author QLExpress directly in a Script Task',
      'Map script output to a process variable',
    ],
    keyConcepts: ['scriptTask', 'scriptFormat', 'output mapping'],
    code: `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:cf="http://www.compileflow.org"
             targetNamespace="http://www.compileflow.org/examples">
  <process id="learn.bpmn.script-task" name="BPMN Script Task" isExecutable="true">
    <extensionElements>
      <cf:var name="status" dataType="java.lang.String" inOutType="return"/>
    </extensionElements>
    <startEvent id="start" name="Start"/>
    <sequenceFlow id="toAction" sourceRef="start" targetRef="action"/>
    <scriptTask id="action" name="Build status" scriptFormat="qlexpress">
      <extensionElements>
        <cf:output dataType="java.lang.String" target="status"/>
      </extensionElements>
      <script><![CDATA["completed"]]></script>
    </scriptTask>
    <sequenceFlow id="toEnd" sourceRef="action" targetRef="end"/>
    <endEvent id="end" name="End"/>
  </process>
</definitions>`,
    overview:
      'The BPMN Script Task owns its inline source while CompileFlow extensions describe variable mappings and optional execution policies. The example has no external bean or class dependency.',
    explanation:
      'The Script Task evaluates QLExpress and writes `completed` into the process-level return variable `status`.',
    nextSteps:
      'Add an input variable and map it into the script, then switch the language to Java 17 and rewrite the source.',
    documentation:
      'See the [extension guide](https://github.com/alibaba/compileflow/blob/master/docs/en/extension-guide.md) for supported action types and custom providers.',
  },
]

export function findMockExample(id: string): Example | undefined {
  return mockExamples.find((example) => example.id === id)
}

export function listMockExamples(): Example[] {
  return [...mockExamples]
}
