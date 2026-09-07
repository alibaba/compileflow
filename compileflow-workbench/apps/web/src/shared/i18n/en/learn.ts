const enLearn = {
  'examples.title': 'Examples',

  'examples.subtitle':
    'Learn CompileFlow with runnable examples, from basic workflows to advanced patterns.',

  'examples.difficulty': 'Difficulty',

  'examples.category': 'Category',

  'examples.search': 'Search examples…',

  'examples.total': '{{count}} examples',

  'examples.notFound': 'No matching examples',

  'examples.startLearning': 'Open example →',

  'examples.viewDetail': 'View details',

  'examples.learnWhat': 'You will learn',

  // Difficulty Levels

  'level.beginner': 'Introductory',

  'level.basic': 'Beginner',

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

  'detail.difficultyValue': '{{value}} of 5 stars',

  'detail.format': 'Format: {{desc}}',

  'detail.tab.overview': 'Overview',

  'detail.tab.code': 'Code',

  'detail.tab.execute': 'Run',

  'detail.tab.docs': 'Docs',

  'detail.whatYouWillLearn': 'What you will learn',

  'detail.keyConcepts': 'Key concepts',
  'detail.explanation': 'How it works',
  'detail.nextSteps': 'Next steps',

  'detail.noCode': 'No code is available for this example',

  // Execution

  'detail.openInDesigner': 'Open in designer',

  'detail.openInDesignerSuccess': 'Opened in {{type}} designer',

  'detail.quickActions': 'Quick actions',

  // Operate Module - Monitoring

  'category.beginner': 'Getting started',

  'category.advanced': 'Advanced',

  // Sort

  'examples.modelType': 'Process type',

  'examples.sortBy': 'Sort by',

  // Detail - noDocs

  'detail.noDocs': 'No documentation is available for this example',

  // Logs extras

  'detail.downloadSuccess': 'Downloaded "{{name}}"',

  'learning.progress': 'Progress',

  'learning.completed': 'Done',

  'learning.totalProgress': 'Overall progress',

  'learning.markComplete': 'Mark as complete',

  'learning.congrats': 'Example complete. Continue when you are ready.',

  // Table of contents

  'toc.title': 'On this page',

  // Related examples

  'examples.highlyRecommended': 'Recommended',

  'examples.viewAll': 'View all examples →',

  // Related examples

  'examples.related': 'Related examples',

  'examples.noRelated': 'No related examples',

  'examples.totalCount': 'Total examples',

  // Example Navigation

  'exampleNav.prev': 'Previous',

  'exampleNav.next': 'Next',

  'exampleNav.progress': 'Example {{current}} of {{total}}',

  'exampleNav.navigationLabel': 'Example navigation',

  'exampleContent.learn.tbbpm.greeting.name': 'TBBPM Greeting',

  'exampleContent.learn.tbbpm.greeting.description':
    'Create a greeting with a self-contained QLExpress script action.',
  'exampleContent.learn.tbbpm.greeting.overview':
    'This minimal TBBPM process contains a start node, a script task, and an end node. The script has no application class dependencies, so you can validate and run the example immediately.',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.0':
    'Declare process input and return variables',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.1': 'Connect start, task, and end nodes',
  'exampleContent.learn.tbbpm.greeting.whatYouWillLearn.2':
    'Map a script result to a process return variable',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.0': 'TBBPM definition',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.1': 'Script action',
  'exampleContent.learn.tbbpm.greeting.keyConcepts.2': 'Return variable',
  'exampleContent.learn.tbbpm.greeting.explanation':
    'If `name` is omitted, the process uses the default value `World`. The QLExpress script creates the greeting, and its output mapping writes the result to the process return variable `message`.',
  'exampleContent.learn.tbbpm.greeting.nextSteps':
    'Run the process with a custom `name`, then change the default value and compare the returned messages.',
  'exampleContent.learn.tbbpm.greeting.documentation':
    'See the [TBBPM specification](https://github.com/alibaba/compileflow/blob/master/docs/en/specifications/tbbpm.md) and the [node support matrix](https://github.com/alibaba/compileflow/blob/master/docs/en/node-support.md).',

  'exampleContent.learn.bpmn.routing.name': 'BPMN Amount Routing',

  'exampleContent.learn.bpmn.routing.description':
    'Use an exclusive gateway to select a route based on an amount.',
  'exampleContent.learn.bpmn.routing.overview':
    'This example uses CompileFlow-supported BPMN elements to make a synchronous routing decision. The `manual-review` branch returns a route name; it does not create or persist a human task.',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.0':
    'Declare CompileFlow variables in BPMN extension elements',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.1':
    'Define mutually exclusive conditions on sequence flows',
  'exampleContent.learn.bpmn.routing.whatYouWillLearn.2':
    'Use Script Tasks to set the result for each route',
  'exampleContent.learn.bpmn.routing.keyConcepts.0': 'BPMN subset',
  'exampleContent.learn.bpmn.routing.keyConcepts.1': 'Exclusive gateway',
  'exampleContent.learn.bpmn.routing.keyConcepts.2': 'Script task',
  'exampleContent.learn.bpmn.routing.explanation':
    'The default amount is 150, which selects `manual-review` when no input is provided. An amount below 100 selects `automatic`. Both routes finish at the same end event.',
  'exampleContent.learn.bpmn.routing.nextSteps':
    'Run the process with `{"amount": 50}` and compare the returned `route`. Then add a third route with an explicit default sequence flow.',
  'exampleContent.learn.bpmn.routing.documentation':
    'See the [Process model](https://github.com/alibaba/compileflow/blob/master/docs/en/architecture/process-model.md) for BPMN extension placement and supported-node rules.',

  'exampleContent.learn.tbbpm.parallel.name': 'TBBPM Parallel Calculation',

  'exampleContent.learn.tbbpm.parallel.description':
    'Run two independent calculations in parallel and return both results.',
  'exampleContent.learn.tbbpm.parallel.overview':
    'The two branches run independently and write to separate return variables. A join waits for both branches before the process ends.',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.0':
    'Create parallel branches and join them',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.1':
    'Store each branch output in a separate process variable',
  'exampleContent.learn.tbbpm.parallel.whatYouWillLearn.2':
    'Use script actions without application class dependencies',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.0': 'Parallel split',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.1': 'Parallel join',
  'exampleContent.learn.tbbpm.parallel.keyConcepts.2': 'Branch output',
  'exampleContent.learn.tbbpm.parallel.explanation':
    'The parallel split starts both calculations. The join waits for both branches to finish, and the result contains `leftResult=20` and `rightResult=22`.',
  'exampleContent.learn.tbbpm.parallel.nextSteps':
    'Replace one constant with an input variable, then compare the result with the same tasks arranged in sequence.',
  'exampleContent.learn.tbbpm.parallel.documentation':
    'See [Advanced Features](https://github.com/alibaba/compileflow/blob/master/docs/en/advanced-features.md) for concurrency behavior.',

  'exampleContent.learn.bpmn.script-task.name': 'BPMN Script Task',

  'exampleContent.learn.bpmn.script-task.description':
    'Write inline QLExpress code in a standard BPMN Script Task.',
  'exampleContent.learn.bpmn.script-task.overview':
    'The script source is stored directly in the BPMN Script Task. CompileFlow extensions map its output to a process variable, so the example runs without external beans or application classes.',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.0':
    'Build the control flow with standard BPMN elements',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.1':
    'Author QLExpress directly in a Script Task',
  'exampleContent.learn.bpmn.script-task.whatYouWillLearn.2':
    'Map script output to a process variable',
  'exampleContent.learn.bpmn.script-task.keyConcepts.0': 'Script task',
  'exampleContent.learn.bpmn.script-task.keyConcepts.1': '`scriptFormat` attribute',
  'exampleContent.learn.bpmn.script-task.keyConcepts.2': 'Output mapping',
  'exampleContent.learn.bpmn.script-task.explanation':
    'The Script Task runs the QLExpress script and maps its result, `completed`, to the process return variable `status`.',
  'exampleContent.learn.bpmn.script-task.nextSteps':
    'Add an input variable and use it in the script. Then switch the script language to Java 17 and rewrite the expression.',
  'exampleContent.learn.bpmn.script-task.documentation':
    'See the [extension guide](https://github.com/alibaba/compileflow/blob/master/docs/en/extension-guide.md) for supported action types and custom providers.',

  // Global search
} as const

export default enLearn
