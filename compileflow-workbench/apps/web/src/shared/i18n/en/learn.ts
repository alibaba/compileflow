const enLearn = {
  'examples.title': 'Examples',

  'examples.subtitle':
    'Hands-on flows that teach CompileFlow—from first steps to advanced patterns.',

  'examples.difficulty': 'Difficulty',

  'examples.category': 'Category',

  'examples.search': 'Search examples…',

  'examples.total': '{{count}} examples',

  'examples.notFound': 'No matching examples',

  'examples.startLearning': 'Start →',

  'examples.viewDetail': 'View details',

  'examples.learnWhat': 'You will learn',

  // Difficulty Levels

  'level.beginner': 'Beginner',

  'level.basic': 'Basic',

  'level.intermediate': 'Intermediate',

  'level.advanced': 'Advanced',

  // Categories

  'category.basics': 'Basics',

  'category.business': 'Business',

  'category.approval': 'Approval',

  // Example Detail

  'detail.back': 'Back to examples',

  'detail.duration': '{{time}}',

  'detail.durationMinutes': '{{count}} minutes',

  'detail.difficulty': 'Difficulty: {{stars}}',

  'detail.difficultyValue': '{{value}} out of 5 stars',

  'detail.format': 'Format: {{desc}}',

  'detail.tab.overview': 'Overview',

  'detail.tab.code': 'Code',

  'detail.tab.execute': 'Run',

  'detail.tab.docs': 'Docs',

  'detail.whatYouWillLearn': 'What you will learn',

  'detail.keyConcepts': 'Key concepts',
  'detail.explanation': 'How it works',
  'detail.nextSteps': 'Next steps',

  'detail.noCode': 'No code available',

  // Execution

  'detail.openInDesigner': 'Open in designer',

  'detail.openInDesignerSuccess': 'Opened in {{type}} designer',

  'detail.quickActions': 'Quick actions',

  // Operate Module - Monitoring

  'category.beginner': 'Getting Started',

  'category.advanced': 'Advanced Features',

  // Sort

  'examples.modelType': 'Process type',

  'examples.sortBy': 'Sort by',

  // Detail - noDocs

  'detail.noDocs': 'No documentation yet',

  // Logs extras

  'detail.downloadSuccess': 'Downloaded "{{name}}"',

  'learning.progress': 'Progress',

  'learning.completed': 'Done',

  'learning.totalProgress': 'Overall',

  'learning.markComplete': 'Mark complete',

  'learning.congrats': 'Nice work—this example is done. Keep going.',

  // Table of contents

  'toc.title': 'On this page',

  // Related examples

  'examples.highlyRecommended': 'Recommended',

  'examples.viewAll': 'View all examples →',

  // Related examples

  'examples.related': 'Related examples',

  'examples.noRelated': 'No related examples',

  'examples.totalCount': 'Examples',

  // Example Navigation

  'exampleNav.prev': 'Previous',

  'exampleNav.next': 'Next',

  'exampleNav.progress': 'Example {{current}} / {{total}}',

  'exampleNav.navigationLabel': 'Example navigation',

  'exampleContent.learn.tbbpm.greeting.name': 'TBBPM Greeting',

  'exampleContent.learn.tbbpm.greeting.description':
    'Build a return value with a self-contained QL Script action.',
  'exampleContent.learn.tbbpm.greeting.overview':
    'This is the smallest useful TBBPM definition: one start node, one executable task, and one end node. It has no application-specific class dependency, so the built-in QL Script can be preflighted and executed as-is.',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.0':
    'Declare process input and return variables',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.1': 'Connect start, task, and end nodes',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.2':
    'Map an action result into the process context',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.0': 'TBBPM document',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.1': 'Script action',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.2': 'return variable',
  'exampleContent.learn.tbbpm.greeting.explanation':
    'The missing `name` input uses the declared `World` default. The QL Script evaluates a string and the nested action variable maps that value to the root `message` output.',
  'exampleContent.learn.tbbpm.greeting.nextSteps':
    'Add a string input variable with a default value, map it into the action, and build a personalized greeting.',
  'exampleContent.learn.tbbpm.greeting.documentation':
    'See the [TBBPM specification](https://github.com/alibaba/compileflow/blob/master/docs/specs/tbbpm-specification.en.md) and the [node support matrix](https://github.com/alibaba/compileflow/blob/master/docs/en/node-support.md).',

  'exampleContent.learn.bpmn.routing.name': 'BPMN Amount Routing',

  'exampleContent.learn.bpmn.routing.description':
    'Route an amount through an exclusive gateway and return the selected path.',
  'exampleContent.learn.bpmn.routing.overview':
    'This example uses only the CompileFlow-supported BPMN subset. It models a synchronous decision, not a durable human task: an external task system must own human-task state and lifecycle.',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.0':
    'Declare CompileFlow variables in BPMN extension elements',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.1':
    'Use mutually exclusive sequence-flow conditions',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.2':
    'Attach executable actions to BPMN service tasks',
  'exampleContent.learn.bpmn.routing.keyConcepts.0': 'BPMN subset',
  'exampleContent.learn.bpmn.routing.keyConcepts.1': 'exclusiveGateway',
  'exampleContent.learn.bpmn.routing.keyConcepts.2': 'cf:action',
  'exampleContent.learn.bpmn.routing.explanation':
    'The default amount is 150, so an empty input selects `manual-review`. Supplying an amount below 100 selects `automatic`. Both branches converge on the same end event.',
  'exampleContent.learn.bpmn.routing.nextSteps':
    'Run the flow with `{"amount": 50}` and compare the returned `route`, then add a third policy through an explicit default sequence flow.',
  'exampleContent.learn.bpmn.routing.documentation':
    'See the [Process model](https://github.com/alibaba/compileflow/blob/master/docs/architecture/07-PROCESS_MODEL.en.md) for BPMN extension placement and supported-node rules.',

  'exampleContent.learn.tbbpm.parallel.name': 'TBBPM Parallel Calculation',

  'exampleContent.learn.tbbpm.parallel.description':
    'Run two independent calculations with explicit concurrency capabilities and join their outputs.',
  'exampleContent.learn.tbbpm.parallel.overview':
    'Parallel branches are appropriate only when their work and outputs are independent. This example writes to two separate return variables and joins before completion.',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.0':
    'Model a parallel split and structural join',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.1':
    'Keep branch outputs in distinct process variables',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.2':
    'Use self-contained actions without application classes',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.0': 'parallel split',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.1': 'parallel join',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.2': 'branch output',
  'exampleContent.learn.tbbpm.parallel.explanation':
    'The split starts both calculations through the engine execution executor. The join waits for both paths, then the result map exposes `leftResult=20` and `rightResult=22`.',
  'exampleContent.learn.tbbpm.parallel.nextSteps':
    'Replace one constant with a mapped input and compare the behavior with a sequential pair of tasks.',
  'exampleContent.learn.tbbpm.parallel.documentation':
    'See [Advanced Features](https://github.com/alibaba/compileflow/blob/master/docs/en/advanced-features.md) for concurrency behavior.',

  'exampleContent.learn.bpmn.script-task.name': 'BPMN Script Task',

  'exampleContent.learn.bpmn.script-task.description':
    'Author definition-owned inline code in a standard BPMN Script Task.',
  'exampleContent.learn.bpmn.script-task.overview':
    'The BPMN Script Task owns its inline source while CompileFlow extensions describe variable mappings and optional execution policies. The example has no external bean or class dependency.',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.0':
    'Keep BPMN control flow in standard elements',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.1':
    'Author QLExpress directly in a Script Task',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.2':
    'Map script output to a process variable',
  'exampleContent.learn.bpmn.script-task.keyConcepts.0': 'scriptTask',
  'exampleContent.learn.bpmn.script-task.keyConcepts.1': 'scriptFormat',
  'exampleContent.learn.bpmn.script-task.keyConcepts.2': 'output mapping',
  'exampleContent.learn.bpmn.script-task.explanation':
    'The Script Task evaluates QLExpress and writes `completed` into the process-level return variable `status`.',
  'exampleContent.learn.bpmn.script-task.nextSteps':
    'Add an input variable and map it into the script, then switch the language to Java 17 and rewrite the source.',
  'exampleContent.learn.bpmn.script-task.documentation':
    'See the [extension guide](https://github.com/alibaba/compileflow/blob/master/docs/en/extension-guide.md) for supported action types and custom providers.',

  // Global search
} as const

export default enLearn
