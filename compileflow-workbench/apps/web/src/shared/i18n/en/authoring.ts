const enAuthoring = {
  'code.copy': 'Copy',

  'code.download': 'Download',

  'code.copySuccess': 'Copied to clipboard',

  'code.copyFailed': 'Copy failed',

  'code.downloadSuccess': 'Download started',

  // Navigation

  'exec.title': 'Server execution',

  'exec.desc':
    'Runs the current draft on the server. Actions may call external systems and make irreversible changes. Use safe parameters in an isolated environment.',

  'exec.params': 'Parameters (JSON)',

  'exec.paramsPlaceholder': '{"param1": "value1", "param2": 123}',

  'exec.execute': 'Run flow',

  'exec.executing': 'Running…',

  'exec.success': 'Succeeded',

  'exec.failed': 'Failed',

  'exec.result': 'Result',

  'exec.error': 'Execution error: {{error}}',

  'exec.paramError': 'Enter valid JSON parameters.',

  'exec.noCode': 'No flow to run',

  // Theme

  'workspace.title': 'Build',

  'workspace.subtitle': 'Design flows from scratch or start with a template.',

  'workspace.newBpmn': 'New BPMN',

  'workspace.newTbbpm': 'New TBBPM',

  'workspace.browseExamples': 'Examples',

  'workspace.newBpmnAction': 'New BPMN flow',

  'workspace.newBpmnDesc': 'Create a BPMN flow on a blank canvas.',

  'workspace.newTbbpmAction': 'New TBBPM flow',

  'workspace.newTbbpmDesc': 'Create a TBBPM flow for compiled, in-process execution.',

  'workspace.browseExamplesAction': 'Browse examples',

  'workspace.browseExamplesDesc': 'Explore common flow patterns and configurations.',

  'workspace.apiIntegration': 'API',

  'workspace.apiIntegrationDesc': 'Integrate CompileFlow with your application.',

  'workspace.exportAll': 'Export all',

  'workspace.importData': 'Import',

  'workspace.myProcesses': 'My flows',

  'workspace.availableTemplates': 'Available templates',

  'workspace.thisMonth': 'This month',

  'workspace.recentProjects': 'Recent',

  'workspace.noRecentProjects': 'No recent projects',

  'workspace.open': 'Open',

  'workspace.quickStart': 'Quick start',

  'workspace.myWork': 'My work',

  'workspace.quickTemplates': 'Templates',

  'workspace.use': 'Use',

  'workspace.importFileLabel': 'Select a Workbench JSON file',

  'workspace.template.tpl-1.name': 'BPMN Starter',

  'workspace.template.tpl-1.description': 'A minimal executable flow from start to end',

  'workspace.template.tpl-2.name': 'TBBPM Greeting',

  'workspace.template.tpl-2.description': 'An inline Java action with input and output mappings',

  'workspace.template.tpl-3.name': 'Blank BPMN',

  'workspace.template.tpl-3.description': 'Empty BPMN process for visual modeling',

  'workspace.template.tpl-4.name': 'TBBPM Starter',

  'workspace.template.tpl-4.description': 'A minimal executable TBBPM flow',

  // Operate Home Page

  'workspace.exportSuccess': 'Exported',

  'workspace.exportFailed': 'Export failed',

  'workspace.importSummary':
    'Import finished: {{success}} imported, {{skipped}} skipped, {{failed}} failed.',

  'workspace.importFailed': 'Import failed. Check the file format.',

  // Process management tooltip

  'designer.view.visual': 'Canvas',

  'designer.view.tabsLabel': 'View mode',

  'designer.view.code': 'Code',

  'designer.view.preview': 'Split',

  'designer.view.previewEmpty': 'No content',

  'designer.header.backToWorkspace': 'Back to Build',

  'designer.header.workspace': 'Build',

  'designer.header.editName': 'Rename flow',

  'designer.header.unsavedChanges': 'Unsaved changes',

  'designer.header.saving': 'Saving…',

  'designer.header.saveDirty': 'Save *',

  'designer.header.saved': 'Saved',

  'designer.status.draft': 'Draft',

  'designer.status.local': 'Local',

  'designer.xmlEditor.dirtyHint': 'Click Apply to update the canvas with your XML changes.',

  'designer.xmlEditor.parseError': 'XML parse error',

  'designer.xmlEditor.applyFailed': 'XML could not be applied',

  'designer.xmlEditor.applySuccess': 'XML applied to canvas',

  'designer.xmlEditor.reset': 'Discard changes',

  'designer.xmlEditor.apply': 'Apply to canvas',

  'designer.xmlEditor.discardTitle': 'Discard XML edits?',

  'designer.xmlEditor.discardMessage':
    'Your changes in the XML editor have not been applied to the canvas.',

  'designer.xmlEditor.discardConfirm': 'Discard',

  'designer.xmlEditor.editorRegion': 'XML code editor',

  'designer.debug.simulationBanner':
    'Browser simulation supports a restricted set of Java conditions and uses simulated action results. Use server execution to verify retries, waits, subprocesses, and concurrent branches.',

  'designer.debug.modeSimulation': 'Simulation',

  'designer.debug.modeEngine': 'Server execution',

  'designer.debug.engineBanner': 'Runs the draft through the configured Workbench Server.',

  'designer.debug.engineUnavailable':
    'Server execution is unavailable. Check the Workbench Server connection and try again.',

  'designer.debug.engineTitle': 'Server execution',

  'designer.debug.engineOnline': 'Online',

  'designer.debug.engineOffline': 'Offline',

  'designer.debug.engineRefreshStatus': 'Refresh status',

  'designer.debug.engineRun': 'Run draft on server',

  'designer.debug.engineExecutionWarningTitle': 'Server execution',

  'designer.debug.engineExecutionWarning':
    'This runs the current draft on the Workbench Server. Scripts, Java actions, and Spring Beans can access server resources and external systems.',

  'designer.debug.engineReset': 'Clear log',

  'designer.debug.engineProcessCode': 'Process code',

  'designer.debug.engineParams': 'Input parameters (JSON)',

  'designer.debug.engineMissingCode': 'Enter a process code before running the flow',

  'designer.debug.engineInvalidParams': 'Enter valid JSON parameters',

  'designer.debug.engineStart': 'Running {{code}} on the server',

  'designer.debug.engineSuccess': 'Server execution completed',

  'designer.debug.engineFailed': 'Server execution failed',

  'designer.save.workspaceSuccess': 'Saved',

  'designer.save.operateSuccess': 'Saved to flow management',

  'designer.save.failed': 'Save failed',

  'designer.delete.operateSuccess': 'Managed flow deleted',

  'designer.autoSave.failed': 'Auto-save failed',

  'designer.autoSave.pending': 'Unsaved · auto-save scheduled',

  'designer.toolbar.label': 'Canvas toolbar',
  'designer.toolbar.moreActions': 'More canvas actions',

  'designer.toolbar.alignGroup': 'Align tools',

  'designer.toolbar.distributeGroup': 'Distribute tools',

  'designer.toolbar.batchGroup': 'Batch actions',

  'designer.toolbar.viewGroup': 'View controls',

  'designer.toolbar.alignLeft': 'Align left',

  'designer.toolbar.alignCenterV': 'Align center vertically',

  'designer.toolbar.alignRight': 'Align right',

  'designer.toolbar.alignTop': 'Align top',

  'designer.toolbar.alignCenterH': 'Align center horizontally',

  'designer.toolbar.alignBottom': 'Align bottom',

  'designer.toolbar.distributeH': 'Distribute horizontally',

  'designer.toolbar.distributeV': 'Distribute vertically',

  'designer.toolbar.copySelected': 'Duplicate selection (Ctrl+D)',

  'designer.toolbar.createConnection': 'Connect nodes (no drag required)',

  'designer.toolbar.deleteSelected': 'Delete selection (Delete)',

  'designer.toolbar.selectAll': 'Select all (Ctrl+A)',

  'designer.toolbar.zoomIn': 'Zoom in (Ctrl+wheel)',

  'designer.toolbar.zoomOut': 'Zoom out (Ctrl+wheel)',

  'designer.toolbar.zoomFit': 'Fit to canvas',

  'designer.toolbar.zoomReset': 'Reset view',

  'designer.layout.tabProperties': 'Properties',

  'designer.layout.rightPanelTabs': 'Right panel sections',

  'designer.layout.tabValidation': 'Validate',

  'designer.layout.tabDebug': 'Debug',

  'designer.layout.expandPalette': 'Expand node palette',

  'designer.layout.collapsePalette': 'Collapse node palette',

  'designer.layout.expandProperties': 'Expand properties panel',

  'designer.layout.collapseProperties': 'Collapse properties panel',

  'designer.layout.emptyTitle': 'Select a node or edge',

  'designer.layout.emptySub': 'Click an element on the canvas to view its properties',

  'designer.layout.emptyHint': 'Use Validate and Debug in the toolbar to check the flow',

  'designer.statusBar.zoomOut': 'Zoom out',

  'designer.statusBar.zoomReset': 'Reset zoom',

  'designer.statusBar.zoomIn': 'Zoom in',

  'designer.statusBar.nodeCount': 'Node count',

  'designer.statusBar.edgeCount': 'Edge count',

  'designer.statusBar.nodes': '{{count}} nodes',

  'designer.statusBar.edges': '{{count}} edges',

  'designer.statusBar.saving': 'Saving…',

  'designer.statusBar.unsaved': 'Unsaved',

  'designer.statusBar.saved': 'Saved',

  'designer.contextMenu.editProps': 'Edit properties',

  'designer.contextMenu.copy': 'Copy',

  'designer.contextMenu.delete': 'Delete',

  'designer.contextMenu.breakpoint': 'Set breakpoint',

  'designer.contextMenu.editCondition': 'Edit condition',

  'designer.contextMenu.deleteEdge': 'Delete connection',

  'designer.contextMenu.paste': 'Paste',

  'designer.contextMenu.selectAll': 'Select all',

  'designer.palette.searchPlaceholder': 'Search nodes…',

  'designer.palette.addNode': 'Click or drag to add node: {{label}}',

  'designer.palette.empty': 'No matching nodes',

  'designer.palette.emptyHint': 'Try another keyword, such as "start" or "gateway"',

  'designer.palette.tbbpmTitle': 'Node toolbox',

  'designer.palette.bpmnTitle': 'BPMN nodes',

  'designer.header.undo': 'Undo (Ctrl+Z)',

  'designer.header.redo': 'Redo (Ctrl+Shift+Z)',

  'designer.header.copy': 'Copy (Ctrl+C)',

  'designer.header.paste': 'Paste (Ctrl+V)',

  'designer.header.toggleGrid': 'Toggle grid (Ctrl+G)',

  'designer.header.searchNodes': 'Search nodes (Ctrl+F)',

  'designer.header.validate': 'Validate flow',

  'designer.header.debug': 'Debug flow',

  'designer.header.variables': 'Variable manager',

  'designer.header.shortcuts': 'Keyboard shortcuts (Ctrl+/)',

  'designer.header.help': 'Help documentation',

  'designer.header.menu.exportGroup': 'Import and export',

  'designer.header.menu.moreActions': 'More actions',

  'designer.header.menu.editingGroup': 'Edit',

  'designer.header.menu.toolsGroup': 'Tools',

  'designer.header.menu.exportXml': 'Export XML',

  'designer.header.menu.exportImage': 'Export image',

  'designer.header.menu.importXml': 'Import XML',

  'designer.header.menu.xmlEditor': 'XML source editor',

  'designer.header.menu.flowGroup': 'Process actions',

  'designer.header.menu.duplicate': 'Duplicate flow',

  'designer.header.menu.deleteProcess': 'Delete flow',

  'designer.header.deleteConfirmTitle': 'Delete flow?',

  'designer.header.deleteConfirmContent': 'Delete flow "{{name}}"? This cannot be undone.',

  'designer.layout.breakpointSet': 'Breakpoint set on node {{nodeId}}',

  'designer.debug.sim.runComplete': 'Execution completed',

  'designer.debug.sim.runFailed': 'Execution failed: {{message}}',

  'designer.debug.sim.resetDone': 'Reset',

  'designer.debug.sim.addBreakpointTitle': 'Add breakpoint',

  'designer.debug.sim.selectBreakpointNode': 'Select a node for the breakpoint',

  'designer.debug.sim.breakpointCondition': 'Breakpoint condition',

  'designer.debug.sim.breakpointConditionPh':
    'Optional Java condition, e.g. hit == true (empty = always)',

  'designer.debug.sim.breakpointUnconditional': 'Always',

  'designer.debug.sim.selectNodeWarning': 'Select a node first',

  'designer.debug.sim.breakpointAdded': 'Breakpoint added on node "{{name}}"',

  'designer.debug.sim.breakpointRemoved': 'Breakpoint removed',

  'designer.debug.sim.panelTitle': 'Process debugger',

  'designer.debug.sim.start': 'Start',

  'designer.debug.sim.step': 'Step',

  'designer.debug.sim.continue': 'Continue',

  'designer.debug.sim.reset': 'Reset',

  'designer.debug.sim.startTooltip': 'Start execution',

  'designer.debug.sim.stepTooltip': 'Run next step',

  'designer.debug.sim.continueTooltip': 'Continue execution',

  'designer.debug.sim.resetTooltip': 'Reset',

  'designer.debug.sim.initialVars': 'Initial variables (JSON)',

  'designer.debug.sim.currentNode': 'Current node',

  'designer.debug.sim.breakpoints': 'Breakpoints',

  'designer.debug.sim.variables': 'Variables',

  'designer.debug.sim.eventLog': 'Execution log',

  'designer.debug.sim.noVariables': 'No variables',

  'designer.debug.sim.add': 'Add',

  'designer.debug.state.ready': 'Ready',

  'designer.debug.state.running': 'Running',

  'designer.debug.state.paused': 'Paused',

  'designer.debug.state.completed': 'Completed',

  'designer.debug.state.error': 'Error',

  'designer.debug.event.nodeEnter': 'Entered node',

  'designer.debug.event.nodeExit': 'Exited node',

  'designer.debug.event.edgeTraverse': 'Traversed connection',

  'designer.debug.event.variableChange': 'Variable changed',

  'designer.debug.event.breakpointHit': 'Breakpoint hit',

  'designer.debug.event.error': 'Error',

  'designer.debug.col.nodeId': 'Node ID',

  'designer.debug.col.condition': 'Condition',

  'designer.debug.col.status': 'Status',

  'designer.debug.col.hitCount': 'Hit count',

  'designer.debug.col.actions': 'Actions',

  'designer.debug.col.time': 'Time',

  'designer.debug.col.type': 'Type',

  'designer.debug.col.details': 'Details',

  'designer.debug.enabled': 'Enabled',

  'designer.debug.disabled': 'Disabled',

  'designer.debug.detail.node': 'Node: {{id}}',

  'designer.debug.detail.edge': 'Edge: {{id}}',

  'designer.debug.detail.error': 'Error: {{message}}',

  'designer.props.exampleTitle': 'Configuration example',

  'designer.props.nodeId': 'Node ID',

  'designer.props.nodeName': 'Node name',

  'designer.props.nodeNameRequired': 'Enter a node name',

  'designer.props.nodeNamePlaceholder': 'Enter node name',

  'designer.props.nodeType': 'Node type',

  'designer.props.documentation': 'Description',

  'designer.props.documentationPlaceholder': 'Describe this node (optional)',

  'designer.palette.tbbpm.cat.flow': 'Process control',

  'designer.palette.tbbpm.cat.task': 'Tasks',

  'designer.palette.tbbpm.cat.gateway': 'Gateways',

  'designer.palette.tbbpm.cat.subprocess': 'Subprocess',

  'designer.palette.tbbpm.cat.loop': 'Loops',

  'designer.palette.tbbpm.cat.control': 'Loop control',

  'designer.palette.tbbpm.cat.annotation': 'Annotations',

  'designer.palette.tbbpm.node.start': 'Start',

  'designer.palette.tbbpm.node.startDesc': 'Process entry point',

  'designer.palette.tbbpm.node.end': 'End',

  'designer.palette.tbbpm.node.endDesc': 'Process exit point',

  'designer.palette.tbbpm.node.autoTask': 'Auto task',

  'designer.palette.tbbpm.node.autoTaskDesc': 'Java method or Spring Bean',

  'designer.palette.tbbpm.node.waitTask': 'Wait task',

  'designer.palette.tbbpm.node.waitTaskDesc': 'Wait for external signal',

  'designer.palette.tbbpm.node.waitEventTask': 'Wait event',

  'designer.palette.tbbpm.node.waitEventTaskDesc': 'Wait for specific event',

  'designer.palette.tbbpm.node.timerTask': 'Timer task',

  'designer.palette.tbbpm.node.timerTaskDesc': 'Durable wait for a duration or instant',

  'designer.palette.tbbpm.node.scriptTask': 'Script task',

  'designer.palette.tbbpm.node.scriptTaskDesc': 'Inline code · Java / QLExpress',

  'designer.palette.tbbpm.node.exclusive': 'Exclusive gateway',

  'designer.palette.tbbpm.node.exclusiveDesc': 'Conditional branch',

  'designer.palette.tbbpm.node.parallel': 'Parallel gateway',

  'designer.palette.tbbpm.node.parallelDesc': 'Parallel execution',

  'designer.palette.tbbpm.node.inclusive': 'Inclusive gateway',

  'designer.palette.tbbpm.node.inclusiveDesc': 'Multi-condition branch',

  'designer.palette.tbbpm.node.subBpm': 'Embedded BPM',

  'designer.palette.tbbpm.node.subBpmDesc': 'Group nodes in an embedded BPM',

  'designer.palette.tbbpm.node.bpmCall': 'BPM call',

  'designer.palette.tbbpm.node.bpmCallDesc': 'Call another flow',

  'designer.palette.tbbpm.node.while': 'While loop',

  'designer.palette.tbbpm.node.whileDesc': 'Repeat the body while a condition is true',

  'designer.palette.tbbpm.node.foreach': 'Foreach loop',

  'designer.palette.tbbpm.node.foreachDesc': 'Process every element sequentially or in parallel',

  'designer.palette.tbbpm.node.continue': 'Continue loop',

  'designer.palette.tbbpm.node.continueDesc': 'Skip current iteration',

  'designer.palette.tbbpm.node.break': 'Break loop',

  'designer.palette.tbbpm.node.breakDesc': 'Exit loop',

  'designer.palette.tbbpm.node.note': 'Note',

  'designer.palette.tbbpm.node.noteDesc': 'Design-time annotation',

  'designer.palette.bpmn.cat.events': 'Events',

  'designer.palette.bpmn.cat.tasks': 'Tasks',

  'designer.palette.bpmn.cat.gateways': 'Gateways',

  'designer.palette.bpmn.cat.composition': 'Composition',

  'designer.palette.bpmn.node.startEvent': 'Start event',

  'designer.palette.bpmn.node.startEventDesc': 'Process entry point',

  'designer.palette.bpmn.node.endEvent': 'End event',

  'designer.palette.bpmn.node.endEventDesc': 'Process exit point',

  'designer.palette.bpmn.node.serviceTask': 'Service task',

  'designer.palette.bpmn.node.serviceTaskDesc': 'Automated service',

  'designer.palette.bpmn.node.scriptTask': 'Script task',

  'designer.palette.bpmn.node.scriptTaskDesc': 'Script execution',

  'designer.palette.bpmn.node.receiveTask': 'Receive task',

  'designer.palette.bpmn.node.receiveTaskDesc': 'Receive message',

  'designer.palette.bpmn.node.exclusiveGateway': 'Exclusive gateway',

  'designer.palette.bpmn.node.exclusiveGatewayDesc': 'Single path selection',

  'designer.palette.bpmn.node.parallelGateway': 'Parallel gateway',

  'designer.palette.bpmn.node.parallelGatewayDesc': 'Parallel execution',

  'designer.palette.bpmn.node.inclusiveGateway': 'Inclusive gateway',

  'designer.palette.bpmn.node.inclusiveGatewayDesc': 'Multi-condition branch',

  'designer.palette.bpmn.node.callActivity': 'Call activity',

  'designer.palette.bpmn.node.callActivityDesc': 'Call another process',

  'designer.palette.bpmn.node.subProcess': 'Embedded subprocess',

  'designer.palette.bpmn.node.subProcessDesc': 'Group activities in an embedded subprocess',

  'designer.props.common.add': 'Add',

  'designer.props.common.close': 'Close',

  'designer.props.common.done': 'Done',

  'designer.props.common.optional': 'Optional',

  'designer.props.common.actionType': 'Action type',

  'designer.props.common.actionTypeJava': 'Java class method',

  'designer.props.common.actionTypeSpringBean': 'Spring Bean',

  'designer.props.common.actionTypeScript': 'Inline script',

  'designer.props.common.execution': 'Durable action type',

  'designer.props.common.executionHelp':
    'Replayable actions may run again during recovery. Effect actions are recorded before they are dispatched.',

  'designer.props.common.executionReplayable': 'Replayable (default)',

  'designer.props.common.executionEffect': 'Effect',

  'designer.props.common.effectPolicyEnabled': 'Enable effect recovery policy',

  'designer.props.common.effectPolicyMode': 'Recovery policy source',

  'designer.props.common.effectPolicyStatic': 'Static',

  'designer.props.common.effectPolicyDynamic': 'Process variable',

  'designer.props.common.effectRecovery': 'Recovery strategy',

  'designer.props.common.effectRecoveryManual': 'Manual',

  'designer.props.common.effectRecoveryRetry': 'Retry',

  'designer.props.common.effectRecoveryReconcile': 'Reconcile',

  'designer.props.common.recoveryPlanVariable': 'Recovery plan variable',

  'designer.props.common.recoveryDelay': 'Recovery delay',

  'designer.props.common.maxRecoveryDuration': 'Maximum recovery duration',

  'designer.props.common.maxReconcileAttempts': 'Maximum reconcile attempts',

  'designer.props.common.reconcileActionEnabled': 'Configure reconcile action',

  'designer.props.common.className': 'Class name',

  'designer.props.common.classNameHelp': 'Fully qualified Java class name, including its package',

  'designer.props.common.methodName': 'Method name',

  'designer.props.common.methodNameOptional': 'Method name (optional)',

  'designer.props.common.methodNameOptionalHelp':
    'Defaults to execute; set only when invoking a different method',

  'designer.props.common.beanName': 'Bean name',

  'designer.props.common.beanNameHelp': 'Bean name in the Spring container',

  'designer.props.common.scriptLanguage': 'Language',

  'designer.props.common.scriptSource': 'Source',

  'designer.props.common.timeout': 'Timeout',

  'designer.props.common.timeoutHelp': 'ISO 8601 duration, e.g. PT30S (30 seconds)',

  'designer.props.common.invocationTimeout': 'Invocation timeout',

  'designer.props.common.invocationTimeoutHelp':
    'Maximum time for the complete call, including retries and backoff delays',

  'designer.props.common.attemptTimeout': 'Attempt timeout',

  'designer.props.common.attemptTimeoutHelp': 'Maximum time for one attempt',

  'designer.props.common.invocationPolicyEnabled': 'Enable invocation policy',

  'designer.props.common.maxAttempts': 'Maximum attempts',

  'designer.props.common.maxAttemptsHelp': 'Total attempts including the first, from 1 to 100',

  'designer.props.common.effectMaxAttempts': 'Maximum recovery attempts',

  'designer.props.common.initialBackoff': 'Initial backoff',

  'designer.props.common.initialBackoffHelp': 'Non-negative ISO 8601 duration, e.g. PT5S',

  'designer.props.common.backoffMultiplier': 'Backoff multiplier',

  'designer.props.common.backoffMultiplierHelp': 'Finite number at least 1.0',

  'designer.props.common.maxBackoff': 'Maximum backoff',

  'designer.props.common.maxBackoffHelp': 'ISO 8601 duration not shorter than the initial backoff',

  'designer.props.common.jitter': 'Retry jitter',

  'designer.props.common.jitterHelp':
    'Full jitter spreads retries across the calculated backoff interval and is enabled by default',

  'designer.props.common.jitterFull': 'Full jitter (recommended)',

  'designer.props.common.jitterNone': 'No jitter',

  'designer.props.common.retryOn': 'Retry condition',

  'designer.props.common.onFailure': 'On failure',

  'designer.props.common.action': 'Action',

  'designer.props.common.defaultValueEnabled': 'Configure a default value',

  'designer.props.common.defaultEdgeId': 'Default outgoing flow',

  'designer.props.common.defaultEdgeIdHelp':
    'Existing outgoing flow used when no condition matches',

  'designer.props.common.gatewayJoinNoConfig':
    'Join gateways synchronize incoming branches and require no routing configuration.',

  'designer.props.common.exprBuilderTooltip': 'Build an expression visually',

  'designer.props.section.basic': 'Basic',

  'designer.props.section.actionType': 'Action type',

  'designer.props.section.execControl': 'Execution control',

  'designer.props.section.outgoingConditions': 'Outgoing conditions',

  'designer.props.section.invocationPolicy': 'Execution policy',

  'designer.props.section.actionConfig': 'Action configuration (optional)',

  'designer.props.section.invocationPolicyTitle': 'Invocation policy',

  'designer.props.section.effectPolicyTitle': 'Effect recovery',

  'designer.props.section.reconcileActionTitle': 'Reconcile action',

  'designer.props.section.varParams': 'Variable parameters',

  'designer.props.section.varTransfer': 'Variable mappings',

  'designer.props.col.seq': '#',

  'designer.props.col.targetNode': 'Target node',

  'designer.props.col.conditionExpr': 'Condition',

  'designer.props.col.varName': 'Variable name',

  'designer.props.col.localVarName': 'Local variable',

  'designer.props.col.javaType': 'Java type',

  'designer.props.col.direction': 'Direction',

  'designer.props.col.mappingReference': 'Source / target',

  'designer.props.col.defaultValue': 'Default value',

  'designer.props.col.description': 'Description',

  'designer.props.table.emptyParams': 'No parameters. Click Add to create one.',

  'designer.props.table.emptySubVars': 'No variable mapping configured',

  'designer.props.alert.noOutgoingEdges': 'No outgoing connections',

  'designer.props.alert.noOutgoingEdgesExclusiveDesc':
    'Connect this exclusive gateway to another node before adding conditions',

  'designer.props.alert.noOutgoingEdgesGatewayDesc':
    'Connect this gateway to another node before adding conditions',

  'designer.props.node.note.title': 'Note',

  'designer.props.node.note.desc': 'Adds context to the diagram and does not affect execution',

  'designer.props.node.note.content': 'Note content',

  'designer.props.node.note.contentPlaceholder': 'Enter note text…',

  'designer.props.node.parallel.title': 'Parallel gateway',

  'designer.props.node.parallel.desc':
    'Fork activates all outgoing edges; join waits for all incoming edges',

  'designer.props.node.parallel.noConfig': 'No additional configuration',

  'designer.props.node.parallel.noConfigDesc':
    'All outgoing edges are activated in parallel. Join waits for all branches.',

  'designer.props.node.break.title': 'Break (exit loop)',

  'designer.props.node.break.desc': 'Exit the current loop; optional execution condition',

  'designer.props.node.continue.title': 'Continue (next iteration)',

  'designer.props.node.continue.desc': 'Skip current iteration; optional execution condition',

  'designer.props.node.conditionOptional': 'Execution condition (optional)',

  'designer.props.node.conditionBreakHelp':
    'Break only when expression is true; empty means unconditional break',

  'designer.props.node.conditionContinueHelp':
    'Continue only when expression is true; empty means unconditional continue',

  'designer.props.node.exclusive.title': 'Exclusive gateway',

  'designer.props.node.exclusive.desc': 'Select a single outgoing path based on conditions',

  'designer.props.node.inclusive.title': 'Inclusive gateway',

  'designer.props.node.inclusive.desc': 'All matching outgoing edges are activated in parallel',

  'designer.props.node.autoTask.title': 'Auto task',

  'designer.props.node.autoTask.desc': 'Calls a Java method or Spring Bean from your application',

  'designer.props.node.subBpm.title': 'Embedded BPM',

  'designer.props.node.subBpm.desc': 'Groups part of this BPM into an embedded process',

  'designer.props.node.subBpm.boundaryHint':
    'An embedded BPM must contain exactly one start and one end node.',

  'designer.props.node.subBpm.children': 'Body nodes',

  'designer.props.node.subBpm.childrenHelp': 'Select the nodes inside this embedded BPM.',

  'designer.props.node.bpmCall.title': 'BPM call',

  'designer.props.node.bpmCall.desc': 'Invoke another BPM definition',

  'designer.props.node.bpmCall.code': 'BPM code',

  'designer.props.node.bpmCall.codeHelp': 'Unique identifier of the BPM to call',

  'designer.props.node.waitTask.title': 'Wait task',

  'designer.props.node.waitEventTask.title': 'Wait event task',

  'designer.props.node.waitTask.desc':
    'Wait for an external trigger, then resume from this entry point',

  'designer.props.node.waitTask.eventName': 'Event name',

  'designer.props.node.waitTask.eventHelp': 'Name of the event to wait for',

  'designer.props.node.timerTask.title': 'Timer task',

  'designer.props.node.timerTask.desc': 'Wait for a fixed duration, dynamic duration, or instant',

  'designer.props.node.timerTask.scheduleType': 'Schedule type',

  'designer.props.node.timerTask.duration': 'Fixed duration',

  'designer.props.node.timerTask.durationHelp': 'ISO-8601 Duration, for example PT30S',

  'designer.props.node.timerTask.durationExpression': 'Duration expression',

  'designer.props.node.timerTask.durationExpressionHelp': 'Java expression returning a Duration',

  'designer.props.node.timerTask.wakeAtExpression': 'Wake-at expression',

  'designer.props.node.timerTask.wakeAtExpressionHelp':
    'Java expression returning the wake-up instant',

  'designer.props.node.scriptTbbpm.title': 'Script task',

  'designer.props.node.scriptTbbpm.desc': 'Run inline QLExpress or trusted in-process Java 17 code',

  'designer.props.node.loop.whileTitle': 'While loop',

  'designer.props.node.loop.whileDesc': 'Repeat the loop body while a Java condition is true',

  'designer.props.node.loop.forEachTitle': 'Foreach loop',

  'designer.props.node.loop.forEachDesc':
    'Process a collection sequentially or with ordered Durable parallel execution',

  'designer.props.node.loop.execution': 'Execution',

  'designer.props.node.loop.executionHelp':
    'Sequential accepts Iterable or array input; parallel requires Durable execution and List input',

  'designer.props.node.loop.sequentialExecution': 'Sequential',

  'designer.props.node.loop.parallelExecution': 'Parallel',

  'designer.props.node.loop.whileExpr': 'Loop condition',

  'designer.props.node.loop.whileExprHelp': 'Continue while true; exit when false',

  'designer.props.node.loop.maxIterations': 'Maximum iterations',

  'designer.props.node.loop.collection': 'Collection variable',

  'designer.props.node.loop.collectionHelp':
    'A declared process variable or outer-loop variable that contains the collection',

  'designer.props.node.loop.item': 'Element variable',

  'designer.props.node.loop.itemHelp': 'Variable name for current element inside loop',

  'designer.props.node.loop.itemType': 'Element type',

  'designer.props.node.loop.itemTypeHelp': 'Java class name matching the collection element type',

  'designer.props.node.loop.index': 'Index variable',

  'designer.props.node.loop.indexHelp': 'Optional index variable (starts at 0)',

  'designer.props.node.loop.outputTarget': 'Output target',

  'designer.props.node.loop.outputSource': 'Output source',

  'designer.props.node.loop.outputTargetHelp':
    'Declared List process variable that receives results in input order; configure both output fields or neither',

  'designer.props.node.loop.outputSourceHelp':
    'An inner process variable that resets before each iteration and is appended to the output collection afterward',

  'designer.props.node.loop.bodyNodes': 'Body nodes',

  'designer.props.node.loop.bodyNodesHelp': 'Select the nodes directly inside this loop',

  'designer.props.node.loop.bodyNodesPlaceholder': 'Select loop body nodes',

  'designer.props.bpmnLoop.title': 'BPMN loop characteristics',

  'designer.props.bpmnLoop.mode': 'Loop mode',

  'designer.props.bpmnLoop.none': 'No loop',

  'designer.props.bpmnLoop.standard': 'Standard while / until loop',

  'designer.props.bpmnLoop.multiInstance': 'Multi-instance',

  'designer.props.bpmnLoop.testBefore': 'Test before first iteration',

  'designer.props.bpmnLoop.testBeforeHelp':
    'Enabled is while semantics; disabled executes once before testing (until semantics)',

  'designer.props.bpmnLoop.maximum': 'Maximum iterations',

  'designer.props.subProcess.body': 'Embedded process body',

  'designer.props.subProcess.children': 'Direct child nodes',

  'designer.props.subProcess.childrenHelp':
    'Select exactly one start event, one end event, and all nodes owned directly by this subprocess',

  'designer.props.subProcess.boundaryHint':
    'Sequence flows must remain within the embedded subprocess boundary.',

  'designer.props.node.exclusiveGateway.title': 'Exclusive gateway (XOR)',

  'designer.props.node.exclusiveGateway.desc':
    'Execute one branch when its edge condition is true. Configure conditions on edges.',

  'designer.props.node.inclusiveGateway.title': 'Inclusive gateway',

  'designer.props.node.inclusiveGateway.desc':
    'All true outgoing edges are activated; join waits for all activated incoming edges.',

  'designer.props.node.parallelGateway.desc':
    'Fork activates all outgoing edges in parallel; join waits for all incoming edges.',

  'designer.props.node.edgeConditionHint':
    'Select an edge and edit its condition in the Edge properties panel',

  'designer.validation.title': 'Process validation',

  'designer.validation.revalidate': 'Re-validate',

  'designer.validation.notRun': 'Not validated',

  'designer.validation.notRunDesc': 'Select Validate in the toolbar to check the flow',

  'designer.validation.passed': 'Validation passed',

  'designer.validation.passedDesc': 'No issues found',

  'designer.validation.errors': 'Errors',

  'designer.validation.warnings': 'Warnings',

  'designer.validation.infos': 'Info',

  'designer.validation.suggestion': 'Suggested fix:',

  'designer.validation.involvedNodes': 'Affected nodes:',

  'designer.validation.involvedEdges': 'Affected edges:',

  'designer.validation.type.cycle': 'Cycle',

  'designer.validation.type.isolated': 'Isolated',

  'designer.validation.type.deadlock': 'Deadlock',

  'designer.validation.type.unreachable': 'Unreachable',

  'designer.validation.type.multiStart': 'Multiple starts',

  'designer.validation.type.multiEnd': 'Multiple ends',

  'designer.validation.type.noEnd': 'No end',

  'designer.validation.type.orphanEdge': 'Orphan edge',

  'designer.validation.type.property': 'Node property',

  'designer.validation.toast.passed': 'Validation passed — no issues found',

  'designer.validation.toast.errors': '{{count}} error(s) found. Open Validation for details.',

  'designer.validation.toast.warnings': '{{count}} warning(s) found. Open Validation for details.',

  'designer.validation.property.start.mustHaveOutgoing':
    'Start node must have at least one outgoing edge',

  'designer.validation.property.start.shouldNotHaveIncoming':
    'Start node should not have incoming edges',

  'designer.validation.property.end.mustHaveIncoming':
    'End node must have at least one incoming edge',

  'designer.validation.property.end.shouldNotHaveOutgoing':
    'End node should not have outgoing edges',

  'designer.validation.property.gateway.invalidShape':
    'Gateway must be a split (1 incoming, 2+ outgoing) or a join (2+ incoming, 1 outgoing)',

  'designer.validation.property.gateway.joinConditionUnsupported':
    'Outgoing connections from a join gateway cannot have conditions',

  'designer.validation.property.gateway.parallelConditionUnsupported':
    'Outgoing connections from a parallel gateway cannot have conditions',

  'designer.validation.property.gateway.parallelJoinConditionUnsupported':
    'Incoming connections to a parallel join cannot have conditions',

  'designer.validation.property.gateway.multipleDefaultBranches':
    'An inclusive gateway can have at most one unconditional default branch',

  'designer.validation.property.gateway.nestedConcurrencyUnsupported':
    'Parallel and inclusive splits are not supported inside loops',

  'designer.validation.property.node.requiresExplicitGateway':
    'Only gateways can create branches. Add a gateway before branching.',

  'designer.validation.property.node.conditionRequiresGateway':
    'Conditions are supported only on outgoing transitions of an exclusive or inclusive split gateway',

  'designer.validation.property.node.inapplicableProperty':
    'Property "{{property}}" is not valid for TBBPM node type "{{nodeType}}"',

  'designer.validation.property.condition.directMutation':
    'Conditions must be side-effect free; direct mutation "{{operator}}" is not allowed',

  'designer.validation.property.mapping.inapplicableDefault':
    'defaultValue can be used only for an input mapping without source ({{reason}})',

  'designer.validation.property.action.missingType': 'Select an action type',

  'designer.validation.property.action.missingClass': 'Enter the Java class for this action',

  'designer.validation.property.action.invalidClass': 'Enter a fully qualified Java class name',

  'designer.validation.property.action.invalidMethod': 'Enter a valid Java method name',

  'designer.validation.property.action.missingBean': 'Enter the Spring Bean name for this action',

  'designer.validation.property.action.invalidBean':
    'Action Spring bean name must not contain surrounding whitespace or control characters',

  'designer.validation.property.action.missingScriptLanguage': 'Select a script language',

  'designer.validation.property.action.missingScriptSource': 'Enter the script',

  'designer.validation.property.action.unsupportedType':
    'Action type "{{actionType}}" is not supported',

  'designer.validation.property.scriptTask.unsupportedActionType':
    'scriptTask action type "{{actionType}}" is not supported; use script',

  'designer.validation.property.autoTask.unsupportedActionType':
    'autoTask action type "{{actionType}}" is not supported; use java or spring-bean',

  'designer.validation.property.invocationPolicy.invalid':
    'Invocation policy is invalid: {{message}}',

  'designer.validation.property.effectPolicy.invalid':
    'Effect recovery policy is invalid: {{message}}',

  'designer.validation.property.waitEventTask.missingEvent': 'Enter an event name for this task',

  'designer.validation.property.timerTask.schedule':
    'Set exactly one timer value: duration, durationExpression, or wakeAtExpression',

  'designer.validation.property.bpmCall.missingCode': 'Enter the BPM code to call',

  'designer.validation.property.processCall.targetRequired':
    'Select a classpath location or an exact version for the called process',

  'designer.validation.property.processCall.targetConflict':
    'Choose either classpath or version for the called process, not both',

  'designer.validation.property.processCall.invalidReference':
    'The called process has an invalid code, classpath, or version',

  'designer.validation.property.container.unknownParent': 'Parent "{{parentId}}" does not exist',

  'designer.validation.property.container.invalidParent':
    'Parent "{{parentId}}" cannot contain TBBPM nodes',

  'designer.validation.property.container.invalidChildType':
    'Node type {{nodeType}} is not allowed here',

  'designer.validation.property.container.parentCycle':
    'The parent-child relationship contains a cycle',

  'designer.validation.property.container.crossBoundaryTransition':
    'A connection cannot cross the container boundary',

  'designer.validation.property.loop.endHasOutgoing':
    'A loop body end node cannot have outgoing transitions',

  'designer.validation.property.loop.missingBody': 'Add at least one node to the loop body',

  'designer.validation.property.loop.missingCondition': 'Enter a condition for the while loop',

  'designer.validation.property.loop.invalidMaxIterations':
    'maxIterations must be an integer from 1 to 2,147,483,647',

  'designer.validation.property.loop.missingCollection': 'Select a collection for the foreach loop',

  'designer.validation.property.loop.unknownCollection': 'collection "{{name}}" is not declared',

  'designer.validation.property.loop.missingItem': 'Enter an element variable for the foreach loop',

  'designer.validation.property.loop.invalidLocalVariable':
    '{{property}} "{{value}}" is not a valid variable name',

  'designer.validation.property.loop.variableShadowing':
    'Local variable "{{name}}" cannot shadow an existing variable',

  'designer.validation.property.loop.itemIndexCollision': 'item and index must differ',

  'designer.validation.property.loop.invalidItemType': 'itemType must be a valid Java type name',

  'designer.validation.property.loop.unknownOutputReference':
    'Output variable "{{name}}" is not declared',

  'designer.validation.property.loop.outputReferenceCollision':
    'Output target and output source must be different',

  'designer.validation.property.loop.outputSourceNotInner':
    'Select an internal process variable as the output source',

  'designer.validation.property.loop.parallelBreakUnsupported':
    'Parallel foreach loops do not support break',

  'designer.validation.property.loop.rootOnlyChildType':
    'break/continue must be inside a loop body',

  'designer.validation.property.conn.missingSource': 'Source node "{{nodeId}}" does not exist',

  'designer.validation.property.conn.missingTarget': 'Target node "{{nodeId}}" does not exist',

  'designer.validation.property.conn.selfLoop': 'A node cannot connect to itself',

  'designer.validation.property.note.transitionNotAllowed':
    'Note nodes cannot be connected to executable nodes',

  'designer.validation.property.bpmn.start.mustHaveOutgoing':
    'Start event must have an outgoing sequence flow',

  'designer.validation.property.bpmn.start.shouldNotHaveIncoming':
    'Start event should not have incoming flows',

  'designer.validation.property.bpmn.end.mustHaveIncoming':
    'End event must have an incoming sequence flow',

  'designer.validation.property.bpmn.end.shouldNotHaveOutgoing':
    'End event should not have outgoing flows',

  'designer.validation.property.bpmn.serviceTask.missingAction':
    'Configure a CompileFlow action for the service task',

  'designer.validation.property.bpmn.serviceTask.unsupportedActionType':
    'Service task only supports Java Method and Spring Bean actions',

  'designer.validation.property.bpmn.scriptTask.missingScriptFormat':
    'Select a scriptFormat for the script task',

  'designer.validation.property.bpmn.scriptTask.missingScript': 'Enter the script task body',

  'designer.validation.property.bpmn.gateway.minTwoOutgoing':
    'Gateway should have at least 2 outgoing branches',

  'designer.validation.property.bpmn.gateway.invalidShape':
    'Gateway must be a split (1 incoming, 2+ outgoing) or a join (2+ incoming, 1 outgoing)',

  'designer.validation.property.bpmn.node.inapplicableProperty':
    'Property "{{property}}" is not valid for BPMN node type "{{nodeType}}"',

  'designer.validation.property.bpmn.gateway.defaultUnsupported':
    'Parallel gateways cannot declare a default flow',

  'designer.validation.property.bpmn.gateway.parallelConditionUnsupported':
    'Parallel gateway outgoing flows cannot declare conditions',

  'designer.validation.property.bpmn.gateway.parallelJoinConditionUnsupported':
    'Parallel join incoming flows cannot be conditional',

  'designer.validation.property.bpmn.gateway.nestedConcurrencyUnsupported':
    'Parallel and inclusive splits are not supported inside embedded subprocesses',

  'designer.validation.property.bpmn.gateway.defaultOnJoin':
    'Join gateways cannot declare a default flow',

  'designer.validation.property.bpmn.gateway.joinConditionUnsupported':
    'Join gateway outgoing flows cannot declare conditions',

  'designer.validation.property.bpmn.gateway.invalidDefault':
    'Default flow "{{connectionId}}" is not an outgoing flow of this gateway',

  'designer.validation.property.bpmn.gateway.defaultHasCondition':
    'Default flow "{{connectionId}}" must not declare a condition',

  'designer.validation.property.bpmn.gateway.branchMissingCondition':
    'Non-default flow "{{connectionId}}" must declare a Java condition',

  'designer.validation.property.bpmn.gateway.duplicateCondition':
    'Exclusive gateway outgoing conditions must be unique',

  'designer.validation.property.bpmn.node.requiresExplicitGateway':
    'Only gateways can create branches. Add a gateway before branching.',

  'designer.validation.property.bpmn.node.conditionRequiresGateway':
    'Conditions are supported only on outgoing flows of an exclusive or inclusive split gateway',

  'designer.validation.property.bpmn.receiveTask.missingMessageRef':
    'Select a BPMN message for the receive task',

  'designer.validation.property.bpmn.receiveTask.unknownMessageRef':
    'messageRef "{{messageRef}}" must identify exactly one BPMN message',

  'designer.validation.property.bpmn.message.missingId': 'Enter a BPMN message ID',

  'designer.validation.property.bpmn.message.duplicateId':
    'BPMN message ID "{{messageId}}" is duplicated',

  'designer.validation.property.bpmn.message.idCollision':
    'BPMN message ID "{{messageId}}" conflicts with a node or sequence-flow ID',

  'designer.validation.property.bpmn.message.missingName':
    'Enter a runtime event name for BPMN message "{{messageId}}"',

  'designer.validation.property.bpmn.callActivity.missingCalledElement':
    'Enter calledElement for the call activity',

  'designer.validation.property.bpmn.subProcess.unknownParent':
    'Node references unknown subprocess parent "{{parentId}}"',

  'designer.validation.property.bpmn.subProcess.invalidParent':
    'Node parent "{{parentId}}" is not an embedded subprocess',

  'designer.validation.property.bpmn.subProcess.triggerEntryChild':
    'Trigger-entry activities are not supported inside an embedded subprocess',

  'designer.validation.property.bpmn.subProcess.parentCycle':
    'Embedded subprocess hierarchy contains a parent cycle',

  'designer.validation.property.bpmn.subProcess.crossContainerTransition':
    'Sequence flows cannot cross an embedded subprocess boundary',

  'designer.validation.property.bpmn.process.duplicateNodeId':
    'BPMN node IDs must be unique across the complete document',

  'designer.validation.property.bpmn.loop.unsupportedNode':
    'Loop characteristics are supported only on synchronous service, script, call, and embedded subprocess activities',

  'designer.validation.property.bpmn.loop.invalid': 'Loop configuration is invalid: {{message}}',

  'designer.validation.property.bpmn.loop.unknownCollection':
    'Multi-instance collection "{{name}}" is not a declared process variable',

  'designer.validation.property.bpmn.loop.unknownOutputReference':
    'Multi-instance output "{{name}}" is not a declared process variable',

  'designer.validation.property.bpmn.loop.outputSourceNotInner':
    'Multi-instance output source must be an inner process variable',

  'designer.validation.property.bpmn.loop.variableShadowing':
    'Multi-instance variable "{{name}}" must not shadow process state',

  'designer.validation.property.bpmn.invocationPolicy.unsupportedNode':
    'InvocationPolicy is supported only on service and script tasks',

  'designer.validation.property.bpmn.invocationPolicy.invalid':
    'Invocation policy is invalid: {{message}}',

  'designer.validation.property.bpmn.mapping.incomplete':
    'Variable mapping is missing a required field',

  'designer.validation.property.bpmn.mapping.duplicate':
    'Variable mapping name "{{name}}" is duplicated',

  'designer.validation.property.bpmn.mapping.missingOutputTarget':
    'Output mappings must declare a target',

  'designer.validation.property.bpmn.mapping.unknownOutputTarget':
    'Output target "{{target}}" is not a declared process variable',

  'designer.validation.property.bpmn.mapping.duplicateOutputTarget':
    'Output target "{{target}}" is assigned more than once',

  'designer.validation.property.bpmn.mapping.multipleOutputs':
    'An action may declare at most one output mapping',

  'designer.validation.property.tbbpm.mapping.incomplete':
    'Variable mapping requires name and Java type',

  'designer.validation.property.tbbpm.mapping.duplicate':
    'Variable mapping name "{{name}}" is duplicated',

  'designer.validation.property.tbbpm.mapping.missingOutputTarget':
    'Called-process output mappings must declare a target',

  'designer.validation.property.tbbpm.mapping.unknownOutputTarget':
    'Output target "{{target}}" is not a declared process variable',

  'designer.validation.property.tbbpm.mapping.duplicateOutputTarget':
    'Output target "{{target}}" is assigned more than once',

  'designer.validation.property.tbbpm.mapping.multipleOutputs':
    'An action may declare at most one output mapping',

  'designer.validation.property.process.duplicateNodeId': 'Node ID "{{nodeId}}" is duplicated',

  'designer.validation.property.process.variable.missingName': 'Process variable name is required',

  'designer.validation.property.process.variable.invalidName':
    'Process variable "{{name}}" must be a valid Java identifier',

  'designer.validation.property.process.variable.reservedName':
    'Process variable "{{name}}" uses a reserved CompileFlow prefix',

  'designer.validation.property.process.variable.duplicateName':
    'Process variable "{{name}}" is duplicated',

  'designer.validation.property.process.variable.missingType':
    'Process variable "{{name}}" must declare a Java type',

  'designer.validation.property.process.variable.invalidDirection':
    'Process variable "{{name}}" must use param, return, or inner direction',

  'designer.validation.summary.passed': '✓ Validation passed',

  'designer.validation.summary.failed': '✗ {{details}}',

  'designer.validation.summary.errors': '{{count}} errors',

  'designer.validation.summary.warnings': '{{count}} warnings',

  'designer.validation.summary.infos': '{{count}} info items',

  'designer.validation.issue.multiStart.missing': 'Flow has no start node',

  'designer.validation.issue.multiStart.missingSuggestion': 'Add a Start node',

  'designer.validation.issue.multiStart.multiple': 'Flow has {{count}} start nodes',

  'designer.validation.issue.multiStart.multipleSuggestion':
    'A flow should usually have only one start node',

  'designer.validation.issue.noEnd.message': 'Flow has no end node',

  'designer.validation.issue.noEnd.suggestion': 'Add at least one End node',

  'designer.validation.issue.multiEnd.message': 'Flow has {{count}} end nodes',

  'designer.validation.issue.multiEnd.suggestion': 'Keep exactly one End node',

  'designer.validation.issue.cycle.message': 'Cycle #{{index}} detected: {{path}}',

  'designer.validation.issue.cycle.suggestion':
    'Remove a connection in the cycle or add a termination condition',

  'designer.validation.issue.isolated.message': '{{count}} node(s) have no connections',

  'designer.validation.issue.isolated.suggestion': 'Connect these nodes to the flow or delete them',

  'designer.validation.issue.unreachable.message': '{{count}} unreachable node(s) from the start',

  'designer.validation.issue.unreachable.suggestion':
    'Add paths from the start or remove these nodes',

  'designer.validation.issue.orphanEdge.message':
    '{{count}} connection(s) have a missing source or target node',

  'designer.validation.issue.orphanEdge.suggestion': 'Delete these orphan edges',

  'designer.validation.issue.deadlock.ambiguous':
    'Parallel gateway "{{name}}" has an invalid connection pattern (in={{inDegree}}, out={{outDegree}})',

  'designer.validation.issue.deadlock.ambiguousSuggestion':
    'A split needs one incoming and multiple outgoing connections; a join needs multiple incoming and one outgoing connection',

  'designer.validation.issue.deadlock.missingJoin':
    'Parallel split gateway "{{name}}" has no matching join gateway',

  'designer.validation.issue.deadlock.missingJoinSuggestion':
    'Add a join gateway where the parallel branches merge',

  'designer.clipboard.nothingToCopy': 'Nothing to copy',

  'designer.clipboard.selectNodeFirst': 'Select a node to copy first',

  'designer.clipboard.cannotCopyStartEnd': 'Start/end nodes cannot be copied',

  'designer.clipboard.copied': 'Copied {{count}} node(s)',

  'designer.clipboard.copyFailed': 'Copy failed',

  'designer.clipboard.openProcessFirst': 'Open a flow first',

  'designer.clipboard.empty': 'Clipboard is empty',

  'designer.clipboard.expired': 'Clipboard data has expired',

  'designer.clipboard.noNodes': 'No nodes in clipboard',

  'designer.clipboard.pasted': 'Pasted {{count}} node(s)',

  'designer.clipboard.pasteFailed': 'Paste failed',

  'designer.clipboard.cleared': 'Clipboard cleared',

  'designer.properties.panel.tbbpm': 'Properties',

  'designer.properties.panel.bpmn': 'BPMN properties',

  'designer.properties.tab.note': 'Note',

  'designer.properties.tab.task': 'Task',

  'designer.properties.tab.script': 'Script',

  'designer.properties.tab.parallel': 'Parallel',

  'designer.properties.tab.inclusive': 'Inclusive',

  'designer.properties.tab.subBpm': 'Embedded BPM',

  'designer.properties.tab.bpmCall': 'BPM call',

  'designer.properties.tab.wait': 'Wait',

  'designer.properties.tab.timer': 'Timer',

  'designer.properties.tab.loop': 'Loop',

  'designer.properties.tab.break': 'Break',

  'designer.properties.tab.continue': 'Continue',

  'designer.properties.tab.service': 'Service',

  'designer.properties.tab.receive': 'Message',

  'designer.properties.tab.exclusive': 'XOR gateway',

  'designer.properties.tab.parallelGateway': 'AND gateway',

  'designer.properties.tab.inclusiveGateway': 'OR gateway',

  'designer.properties.tab.callActivity': 'Call activity',

  'designer.properties.tab.subProcess': 'Embedded subprocess',

  'designer.xmlEditor.generationFailed': 'XML generation failed',

  'designer.scriptEditor.placeholder': '// Enter script code…',

  'designer.localSnapshots.title': 'Local snapshots',

  'designer.localSnapshots.empty': 'No snapshots yet. Save the flow to create one.',

  'designer.localSnapshots.loadFailed': 'Local snapshots could not be loaded',

  'designer.localSnapshots.restore': 'Restore',

  'designer.localSnapshots.restored': 'Snapshot restored',

  'designer.localSnapshots.restoreFailed': 'Restore failed: {{message}}',

  'designer.localSnapshots.snapshotSize': '{{chars}} characters',

  'designer.debug.sim.errorNoStart': 'Process has no start node',

  'designer.debug.sim.errorStepNotPaused': 'Step is only available while paused',

  'designer.debug.sim.errorContinueNotPaused': 'Continue is only available while paused',

  'designer.debug.sim.errorNodeNotFound': 'Node not found: {{nodeId}}',

  'designer.debug.sim.errorConcurrentGatewayUnsupported':
    'Browser simulation does not support parallel or inclusive branches. Use server execution to verify them.',

  'designer.debug.sim.errorNoBranchMatched': 'No outgoing branch matched gateway {{nodeId}}',

  'designer.debug.sim.errorTriggerEntryUnsupported':
    'Browser simulation does not support trigger entry {{nodeId}}. Use server execution.',

  'designer.debug.sim.errorTimerUnsupported':
    'Browser simulation cannot advance timer task {{nodeId}}. Use Durable Runtime to verify timer behavior.',

  'designer.debug.sim.errorLoopUnsupported':
    'Browser simulation does not support the loop at node {{nodeId}}. Use server execution.',

  'designer.debug.sim.errorCalledProcessUnsupported':
    'Browser simulation cannot run the process called by node {{nodeId}}. Use server execution.',

  'designer.debug.sim.errorEmbeddedProcessUnsupported':
    'Browser simulation does not support the embedded subprocess at node {{nodeId}}. Use server execution.',

  'designer.debug.sim.errorExpressionEvaluationFailed':
    'Browser preview cannot safely evaluate the expression at {{elementId}}',

  'designer.debug.sim.errorDeadEnd':
    'Execution stopped at non-end node {{nodeId}} with no outgoing flow',
  'designer.debug.sim.errorCycle': 'Simulation stopped after returning to node {{nodeId}}.',
  'designer.debug.sim.errorStepLimit':
    'Simulation reached its step limit near node {{nodeId}} and stopped.',
  'designer.debug.sim.errorRunSuperseded': 'A newer simulation replaced this run.',

  'designer.node.bpmn.start': 'Start',

  'designer.node.bpmn.end': 'End',

  'designer.node.bpmn.serviceTask': 'Service Task',

  'designer.node.bpmn.scriptTask': 'Script Task',

  'designer.node.bpmn.receiveTask': 'Receive Task',

  'designer.node.bpmn.exclusiveGateway': 'XOR Gateway',

  'designer.node.bpmn.parallelGateway': 'AND Gateway',

  'designer.node.bpmn.inclusiveGateway': 'OR Gateway',

  'designer.node.bpmn.callActivity': 'Call Activity',

  'designer.node.bpmn.subProcess': 'Embedded Subprocess',

  'designer.palette.dndFailed': 'Drag and drop is unavailable on the canvas',

  'designer.props.common.timeoutPlaceholder': 'PT0S (no timeout)',

  'designer.props.common.invocationTimeoutPlaceholder': 'e.g. PT2M',

  'designer.props.common.attemptTimeoutPlaceholder': 'e.g. PT30S',

  'designer.props.ph.varName': 'e.g. order',

  'designer.props.ph.javaType': 'java.lang.String',

  'designer.props.ph.mappingReference': 'Select a process variable',

  'designer.props.ph.defaultValue': 'Enter a literal value',

  'designer.props.ph.description': 'Describe this mapping (optional)',

  'designer.props.ph.javaClass': 'com.example.OrderService',

  'designer.props.ph.springExpression': '${orderService.process(order)}',

  'designer.props.ph.condition': 'e.g. amount > 1000',

  'designer.props.ph.loopWhile': 'e.g. counter < 10 && !cancelled',

  'designer.props.ph.loopCollection': 'e.g. orderList',

  'designer.props.ph.loopElement': 'e.g. order',

  'designer.props.ph.loopElementClass': 'e.g. com.example.model.Order',

  'designer.props.ph.loopIteration': 'e.g. iteration',

  'designer.props.ph.loopIndex': 'e.g. index',

  'designer.props.ph.defaultProcess': 'Flow_default',

  'designer.props.ph.messageId': 'message_order_confirmed',

  'designer.props.ph.callActivity': 'order.approval.flow',

  'designer.props.ph.script': '// script',

  'designer.props.ph.processCode': 'e.g. user.approval.flow',

  'designer.props.ph.eventName': 'e.g. approval.completed',

  'designer.props.ph.methodName': 'processOrder',

  'designer.props.ph.methodNotify': 'notify',

  'designer.props.ph.methodExecute': 'execute',

  'designer.props.ph.beanName': 'orderService',

  'designer.props.ph.beanNotify': 'notifyService',

  'designer.props.ph.scriptLanguage': 'java or qlexpress',

  'designer.props.ph.scriptSource': 'return value + 1;',

  'designer.props.section.scriptConfig': 'Script configuration',

  'designer.props.section.messageConfig': 'Message configuration',

  'designer.props.section.callConfig': 'Call configuration',

  'designer.props.section.execConfig': 'Execution configuration',

  'designer.props.section.whileConfig': 'While loop configuration',

  'designer.props.section.foreachConfig': 'Foreach loop configuration',

  'designer.props.section.loopBody': 'Loop body',

  'designer.props.section.edgeConditionNote': 'Outgoing edge conditions',

  'designer.props.table.emptyActionVars': 'No variable parameters',

  'designer.props.node.parallelGateway.title': 'AND gateway (parallel)',

  'designer.props.node.waitTask.triggerTip':
    'Start an execution from this entry with engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM, "processCode", "flows/process.bpm"), ProcessTrigger.at(node.id), data)',

  'designer.props.node.loop.whileExampleTitle': '// While loop example',

  'designer.props.node.loop.forEachExampleTitle': '// Foreach loop example',

  'designer.props.node.loop.controlTitle': 'Loop control',

  'designer.props.node.loop.controlSupported': 'Available control statements:',

  'designer.props.node.loop.breakDesc': '— exit the loop immediately',

  'designer.props.node.loop.continueDesc': '— skip current iteration and continue',

  'designer.props.node.loop.limitBehaviorHint':
    'Execution fails if the condition remains true at the iteration limit',

  'designer.props.node.scriptTask.scriptFormat': 'Script format (scriptFormat)',

  'designer.props.node.scriptTask.script': 'Script content (script)',

  'designer.props.node.receiveTask.messageId': 'Message ID (messageRef)',

  'designer.props.node.receiveTask.messageHelp':
    'References a top-level BPMN message. Workbench creates it when needed.',

  'designer.props.node.receiveTask.eventName': 'Runtime event name',

  'designer.props.node.receiveTask.eventNameHelp':
    'ProcessTrigger.event must match this name. Changing it affects every receive task that uses the message.',

  'designer.props.node.receiveTask.eventNamePlaceholder': 'payment.received',

  'designer.props.node.callActivity.calledElement': 'Called element (calledElement)',

  'designer.props.node.callActivity.calledElementHelp': 'Called process code or ID',

  'designer.props.processCall.targetType': 'Target type',

  'designer.props.processCall.targetType.classpath': 'Classpath',

  'designer.props.processCall.targetType.version': 'Exact version',

  'designer.props.processCall.classpath': 'Classpath location',

  'designer.props.processCall.classpathPlaceholder': 'e.g. flows/payment.bpmn',

  'designer.props.processCall.version': 'Exact child version',

  'designer.props.processCall.versionPlaceholder': 'e.g. 2026.07.29-1',

  'designer.actions.selectNodeToCopy': 'Select a node to copy first',

  'designer.actions.unsavedLeaveTitle': 'Unsaved changes',

  'designer.actions.unsavedLeaveContent': 'You have unsaved changes. Leave anyway?',

  'designer.actions.saveAndLeave': 'Save and leave',

  'designer.actions.leaveWithoutSaving': 'Leave without saving',

  'designer.actions.noProcessToExport': 'No flow to export',

  'designer.actions.exportXmlSuccess': 'XML exported',

  'designer.actions.exportXmlFailed': 'Could not export XML',

  'designer.actions.importXmlSuccess': 'XML imported',

  'designer.actions.importXmlFailed': 'Import failed: {{message}}',

  'designer.actions.importXmlInvalid': 'Check XML format',

  'designer.actions.canvasNotReady': 'The canvas is not ready. Try again in a moment.',

  'designer.actions.duplicateSuccess': 'Flow copied. Open the copy from the flow list.',

  'designer.actions.duplicateFailed': 'Flow could not be copied',

  'designer.actions.duplicateSuffix': ' (copy)',

  'designer.actions.deleteFailed': 'Delete failed',

  'designer.actions.deleteSuccess': 'Process deleted',

  'designer.actions.xmlFormatError': 'Invalid XML: {{message}}',

  'designer.actions.xmlParseFailed': 'XML parsing failed',

  'designer.actions.xmlEditorTitle': 'XML source editor — {{name}}',

  'designer.actions.xmlEditorTitleDefault': 'XML source editor',

  'designer.actions.exportImageFailed': 'Image export failed',

  'designer.actions.exportImageSuccess': 'PNG and SVG images exported',

  'designer.actions.duplicateOperateUnavailable':
    'Flows opened from flow management cannot be copied',

  'designer.flow.defaultName': 'New {{type}} flow',

  'designer.flow.importedName': 'Imported flow',

  'designer.flow.exampleName': 'Example flow',

  'designer.header.backToOperate': 'Back to flow management',

  'designer.header.operate': 'Process management',

  'designer.warnings.title': 'Import warnings',

  'designer.warnings.dismiss': 'Close',

  'designer.xmlParse.warning.NODE_PARSE_WARNING':
    'Could not parse a node ({{location}}): {{message}}',

  'designer.xmlParse.warning.CONNECTION_PARSE_WARNING':
    'Could not parse a connection ({{location}}): {{message}}',

  'designer.xmlParse.warning.INVALID_SOURCE_REF':
    'Invalid connection source ({{location}}): {{message}}',

  'designer.xmlParse.warning.INVALID_TARGET_REF':
    'Invalid connection target ({{location}}): {{message}}',

  'designer.xmlParse.warning.SCHEMA_VALIDATION_WARNING':
    'Schema validation warning ({{location}}): {{message}}',

  'designer.split.canvasStaleHint':
    'The canvas has unsaved changes. Apply any XML edits, or switch views to refresh the preview.',

  'designer.properties.selectNode': 'Select a node',

  'designer.properties.selectNodeHint':
    'Click a node on the canvas to view and edit its properties',

  'designer.properties.selectEdge': 'Select a connection',

  'designer.properties.selectEdgeHint':
    'Click a connection on the canvas to view and edit its properties',

  'designer.properties.updateFailed': 'Properties could not be updated',

  'designer.properties.generalTab': 'General',

  'designer.edge.title': 'Connection properties',

  'designer.edge.name': 'Connection name',

  'designer.edge.nameTooltip': 'A label for the connection, such as Approve, Reject, or Default',

  'designer.edge.namePlaceholder': 'Enter connection name',

  'designer.edge.condition': 'Condition expression',

  'designer.edge.advancedEdit': 'Advanced editor',

  'designer.edge.expressionTooltip':
    'Exclusive/inclusive gateway exits require a condition expression',

  'designer.edge.expressionHelp':
    'Use a side-effect-free Java boolean expression, e.g. amount > 1000',

  'designer.edge.expressionPlaceholder': 'e.g. amount > 1000',

  'designer.edge.sourceNode': 'Source node',

  'designer.edge.targetNode': 'Target node',

  'designer.connection.createTitle': 'Create connection',

  'designer.connection.create': 'Create',

  'designer.connection.createHint':
    'Choose a source and target. This alternative requires no precise port dragging and works with the keyboard.',

  'designer.connection.source': 'Source node',

  'designer.connection.target': 'Target node',

  'designer.connection.sourcePlaceholder': 'Search and choose a source node',

  'designer.connection.targetPlaceholder': 'Search and choose a target node',

  'designer.connection.sourceRequired': 'Choose a source node',

  'designer.connection.targetRequired': 'Choose a target node',

  'designer.connection.invalid':
    'Choose connectable nodes in the same process scope without duplicating an existing connection',

  'designer.variableManager.title': 'Process variables',

  'designer.variableManager.add': 'Add variable',

  'designer.variableManager.edit': 'Edit variable',

  'designer.variableManager.empty': 'No variables yet. Click Add variable to create one.',

  'designer.variableManager.tip':
    'Use a fully qualified Java class name for the data type, such as java.lang.String.',

  'designer.variableManager.col.name': 'Name',

  'designer.variableManager.col.dataType': 'Data type',

  'designer.variableManager.col.direction': 'Direction',

  'designer.variableManager.col.defaultValue': 'Default value',

  'designer.variableManager.col.description': 'Description',

  'designer.variableManager.col.actions': 'Actions',

  'designer.variableManager.dir.param': 'Input',

  'designer.variableManager.dir.return': 'Return',

  'designer.variableManager.dir.inner': 'Internal',

  'designer.variableManager.deleted': 'Variable deleted',

  'designer.variableManager.updated': 'Variable updated',

  'designer.variableManager.added': 'Variable added',

  'designer.variableManager.deleteConfirm': 'Delete this variable?',

  'designer.variableManager.field.name': 'Variable name',

  'designer.variableManager.field.nameRequired': 'Enter a variable name',

  'designer.variableManager.field.nameDuplicate': 'Variable names must be unique',

  'designer.variableManager.field.nameInvalid': 'Enter a valid Java identifier',

  'designer.variableManager.field.nameReserved': 'This prefix is reserved by CompileFlow',

  'designer.variableManager.field.namePlaceholder': 'e.g. orderData',

  'designer.variableManager.field.dataType': 'Data type (Java class)',

  'designer.variableManager.field.dataTypeRequired': 'Enter a Java class name',

  'designer.variableManager.field.dataTypePlaceholder':
    'e.g. java.lang.String or com.example.OrderData',

  'designer.variableManager.field.direction': 'Direction',

  'designer.variableManager.field.description': 'Description',

  'designer.variableManager.field.descriptionPlaceholder': 'Optional description',

  'designer.variableManager.field.defaultValue': 'Default value',

  'designer.variableManager.field.defaultValuePlaceholder':
    'Literal interpreted as the declared data type',

  'designer.nodeSearch.title': 'Search nodes',

  'designer.nodeSearch.total': '({{count}} nodes)',

  'designer.nodeSearch.placeholder': 'Search by name, ID, or type…',

  'designer.nodeSearch.noResults': 'No matching nodes',

  'designer.nodeSearch.startHint': 'Enter a keyword',

  'designer.nodeSearch.startSubhint': 'Search by node name, ID, or type',

  'designer.nodeSearch.position': 'Position',

  'designer.nodeSearch.nodeId': 'ID',

  'designer.nodeSearch.tipsTitle': 'Search tips',

  'designer.nodeSearch.tip1': 'Search is fuzzy and case-insensitive',

  'designer.nodeSearch.tip2': 'Select a result to focus the node on the canvas',

  'designer.nodeSearch.tip3': 'Shortcut: Ctrl/Cmd + F to open search',

  'designer.nodeSearch.tip4': 'Shortcut: Esc to close search',

  'designer.nodeSearch.type.start': 'Start',

  'designer.nodeSearch.type.end': 'End',

  'designer.nodeSearch.type.autoTask': 'Auto task',

  'designer.nodeSearch.type.scriptTask': 'Script task',

  'designer.nodeSearch.type.exclusive': 'Exclusive gateway',

  'designer.nodeSearch.type.parallel': 'Parallel gateway',

  'designer.nodeSearch.type.inclusive': 'Inclusive gateway',

  'designer.nodeSearch.type.subBpm': 'Embedded BPM',

  'designer.nodeSearch.type.while': 'While',

  'designer.nodeSearch.type.foreach': 'Foreach',

  'designer.nodeSearch.type.bpmCall': 'BPM call',

  'designer.nodeSearch.type.waitTask': 'Wait task',

  'designer.nodeSearch.type.waitEventTask': 'Wait event',

  'designer.nodeSearch.type.timerTask': 'Timer task',

  'designer.nodeSearch.type.break': 'Break loop',

  'designer.nodeSearch.type.continue': 'Continue loop',

  'designer.nodeSearch.type.note': 'Note',

  'designer.errorBoundary.title': 'Designer error',

  'designer.errorBoundary.unknown': 'The designer encountered an unexpected error',

  'designer.errorBoundary.devInfo': 'Technical details',

  'designer.errorBoundary.retry': 'Try again',

  'designer.errorBoundary.reload': 'Reload page',

  'designer.loading.default': 'Loading…',

  'designer.loading.suspense': 'Loading…',

  'designer.loading.rightPanel': 'Loading panel…',

  'designer.loading.monaco': 'Loading code editor…',

  'designer.flowInit.exampleMissingXml': 'Example has no importable flow XML',

  'designer.flowInit.exampleLoaded': 'Example loaded: {{name}}',

  'designer.flowInit.exampleLoadFailed': 'Could not load example: {{message}}',

  'designer.flowInit.exampleCheckData': 'Check the example data',

  'designer.flowInit.flowNotFound': 'Process not found',

  'designer.flowInit.templateNotFound': 'Template not found: {{id}}',

  'designer.flowInit.templateCreated': 'Flow created from template "{{name}}"',

  'designer.flowInit.templateFrom': 'From {{name}}',

  'designer.flowInit.templateLoadFailed': 'Template load failed',

  'designer.flowInit.createFailed': 'Could not create flow',

  'designer.flowInit.operateLoaded': 'Flow loaded: {{name}}',

  'designer.flowInit.operateLoadFailed': 'Flow could not be loaded: {{message}}',

  'designer.flowInit.operateLoadFailedGeneric': 'Flow could not be loaded',

  'designer.flowInit.errorTitle': 'Could not open designer',

  'designer.flowInit.invalidEntry': 'The designer link is incomplete or invalid.',

  'designer.flowInit.errorGeneric':
    'The flow could not be loaded. Try again or return to the workspace.',

  'designer.flowInit.retry': 'Retry',

  'designer.flowInit.backToWorkspace': 'Back to workspace',

  'designer.condition.title': 'Condition expression editor',

  'designer.condition.tab.editor': 'Editor',

  'designer.condition.tab.templates': 'Templates',

  'designer.condition.tab.help': 'Help',

  'designer.condition.content': 'Condition',

  'designer.condition.variables': 'Available variables',

  'designer.condition.operators': 'Operators',

  'designer.condition.validation.directMutation':
    'Conditions must be side-effect free; direct mutation "{{operator}}" is not allowed',

  'designer.condition.help.syntaxTitle': 'Expression syntax',

  'designer.condition.help.syntax1': 'BPMN conditions use a <code>Java boolean expression</code>',

  'designer.condition.help.syntax2':
    'TBBPM conditions use the same <code>Java boolean expression</code>',

  'designer.condition.help.syntax3': 'Keep condition expressions free of side effects',

  'designer.condition.help.examplesTitle': 'Examples',

  'designer.condition.help.notesTitle': 'Notes',

  'designer.condition.help.note1': 'Expression must evaluate to boolean',

  'designer.condition.help.note2': 'Variable names are case-sensitive',

  'designer.condition.help.note3':
    'Use double quotes and null-safe value equality such as <code>"READY".equals(status)</code>',

  'designer.condition.help.note4':
    'Browser simulation supports a restricted subset. Use server execution for final verification.',

  'designer.condition.tpl.compare': 'Numeric compare',

  'designer.condition.tpl.compare.amountGt': 'Amount greater than 1000',

  'designer.condition.tpl.compare.qtyRange': 'Quantity in range',

  'designer.condition.tpl.compare.priceNotEmpty': 'Price not empty',

  'designer.condition.tpl.string': 'String checks',

  'designer.condition.tpl.string.statusPending': 'Status is pending',

  'designer.condition.tpl.string.vipUser': 'VIP user type',

  'designer.condition.tpl.string.nameContains': 'Name contains keyword',

  'designer.condition.tpl.logic': 'Boolean logic',

  'designer.condition.tpl.logic.approvedPaid': 'Approved and paid',

  'designer.condition.tpl.logic.urgentOrHigh': 'Urgent or high priority',

  'designer.condition.tpl.logic.notCancelled': 'Not cancelled',

  'designer.condition.tpl.collection': 'Collection checks',

  'designer.condition.tpl.collection.itemsNotEmpty': 'Order items not empty',

  'designer.condition.tpl.collection.listGt3': 'List length greater than 3',

  'designer.condition.tpl.collection.tagsContains': 'Contains tag',

  'designer.shortcuts.copyNode': 'Copy node',

  'designer.shortcuts.pasteNode': 'Paste node',

  'designer.shortcuts.deleteNode': 'Delete selected node',

  'designer.shortcuts.searchNodes': 'Search nodes',

  'designer.shortcuts.help': 'Keyboard shortcuts',

  'designer.loading.propertiesPanel': 'Loading properties panel…',

  'designer.clipboard.pasteSuffix': '_copy',

  'designer.expr.modeVisual': 'Visual',

  'designer.expr.modeText': 'Text',

  'designer.expr.insertVariable': 'Insert variable',

  'designer.expr.selectVariable': 'Select variable…',

  'designer.expr.insertOperator': 'Insert operator',

  'designer.expr.label': 'Expression',

  'designer.expr.placeholder': 'Enter expression, e.g. variable > 100 && "active".equals(status)',

  'designer.expr.panelFunctions': 'Functions',

  'designer.expr.panelTemplates': 'Templates',

  'designer.expr.apply': 'Apply',

  'designer.expr.syntaxHint': 'Syntax tips',

  'designer.expr.syntaxTip1': 'Variable names: letters, digits, underscore',

  'designer.expr.syntaxTip2':
    'Use double-quoted strings and compare values with "active".equals(status)',

  'designer.expr.syntaxTip3': 'Guards use side-effect-free Java expressions',

  'designer.expr.syntaxTip4': 'Ctrl+Space for autocomplete (text mode)',

  'designer.expr.validation.empty': 'Expression cannot be empty',

  'designer.expr.validation.parens': 'Unmatched parentheses',

  'designer.expr.validation.illegal': 'Contains illegal characters',

  'designer.expr.validation.ok': 'Expression is valid',

  'designer.expr.op.eq': 'Equals',

  'designer.expr.op.ne': 'Does not equal',

  'designer.expr.op.gt': 'Greater than',

  'designer.expr.op.lt': 'Less than',

  'designer.expr.op.gte': 'Greater than or equal to',

  'designer.expr.op.lte': 'Less than or equal to',

  'designer.expr.op.and': 'And',

  'designer.expr.op.or': 'Or',

  'designer.expr.op.not': 'Not',

  'designer.expr.op.contains': 'Contains',

  'designer.expr.op.startsWith': 'Starts with',

  'designer.expr.op.endsWith': 'Ends with',

  'designer.expr.op.isNull': 'Is null',

  'designer.expr.op.isNotNull': 'Is not null',

  'designer.expr.cat.compare': 'Comparison',

  'designer.expr.cat.logic': 'Logic',

  'designer.expr.cat.string': 'String',

  'designer.expr.cat.null': 'Null check',

  'designer.expr.fn.isEmpty': 'Check if string is empty',

  'designer.expr.fn.isNotEmpty': 'Check if string is not empty',

  'designer.expr.fn.contains': 'Check if string contains substring',

  'designer.expr.fn.length': 'Get string length',

  'designer.expr.fn.toUpperCase': 'Convert to uppercase',

  'designer.expr.fn.toLowerCase': 'Convert to lowercase',

  'designer.expr.fn.parseInt': 'Parse integer',

  'designer.expr.fn.parseDouble': 'Parse decimal number',

  'designer.expr.tpl.strEq': 'String equality',

  'designer.expr.tpl.strEqDesc': 'Compare string value',

  'designer.expr.tpl.numCmp': 'Numeric comparison',

  'designer.expr.tpl.numCmpDesc': 'Compare numeric value',

  'designer.expr.tpl.range': 'Range check',

  'designer.expr.tpl.rangeDesc': 'Check if value is in range',

  'designer.expr.tpl.notEmpty': 'Not empty',

  'designer.expr.tpl.notEmptyDesc': 'Not null and not empty string',

  'designer.expr.tpl.contains': 'Contains keyword',

  'designer.expr.tpl.containsDesc': 'Check if string contains keyword',

  'designer.expr.tpl.multiOr': 'Match any value',

  'designer.expr.tpl.multiOrDesc': 'Match one of several values',

  'designer.shortcutsModal.title': 'Keyboard shortcuts',

  'designer.shortcutsModal.searchPlaceholder': 'Search shortcuts or actions…',

  'designer.shortcutsModal.col.category': 'Category',

  'designer.shortcutsModal.col.shortcut': 'Shortcut',

  'designer.shortcutsModal.col.description': 'Description',

  'designer.shortcutsModal.tipTitle': 'Tips',

  'designer.shortcutsModal.tipMac': 'On Mac, use Cmd instead of Ctrl',

  'designer.shortcutsModal.tipConflict': 'Some shortcuts may conflict with browser or OS shortcuts',

  'designer.shortcutsModal.tipF1': 'Press F1 to open this help anytime',

  'designer.shortcutsModal.tipHelp': 'Press Ctrl+/ to open this help anytime',

  'designer.shortcuts.cat.file': 'File',

  'designer.shortcuts.cat.edit': 'Edit',

  'designer.shortcuts.cat.canvas': 'Canvas',

  'designer.shortcuts.cat.node': 'Node',

  'designer.shortcuts.cat.nav': 'Navigation',

  'designer.shortcuts.cat.debug': 'Validate & debug',

  'designer.shortcuts.cat.other': 'Other',

  'designer.shortcuts.item.save': 'Save flow',

  'designer.shortcuts.item.import': 'Import XML file',

  'designer.shortcuts.item.export': 'Export XML file',

  'designer.shortcuts.item.undo': 'Undo',

  'designer.shortcuts.item.redo': 'Redo',

  'designer.shortcuts.item.copy': 'Copy selected node',

  'designer.shortcuts.item.paste': 'Paste node',

  'designer.shortcuts.item.delete': 'Delete selected node',

  'designer.shortcuts.item.selectAll': 'Select all nodes',

  'designer.shortcuts.item.deselect': 'Clear selection',

  'designer.shortcuts.item.zoomIn': 'Zoom in',

  'designer.shortcuts.item.zoomOut': 'Zoom out',

  'designer.shortcuts.item.zoomReset': 'Reset zoom',

  'designer.shortcuts.item.zoomFit': 'Fit to canvas',

  'designer.shortcuts.item.pan': 'Pan canvas',

  'designer.shortcuts.item.rubberband': 'Rubber-band select',

  'designer.shortcuts.item.nodeProperties': 'Edit node properties',

  'designer.shortcuts.item.nodeConnect': 'Connect nodes',

  'designer.shortcuts.item.nodeMove': 'Move node',

  'designer.shortcuts.item.search': 'Search nodes',

  'designer.shortcuts.item.refresh': 'Refresh canvas',

  'designer.shortcuts.item.validate': 'Validate flow',

  'designer.shortcuts.item.debug': 'Open debugger',

  'designer.shortcuts.item.breakpoint': 'Toggle breakpoint on selected node',

  'designer.shortcuts.item.help': 'Show this help',

  'designer.shortcuts.item.fullscreen': 'Toggle fullscreen',

  'designer.shortcuts.gesture.doubleClickNode': 'Double-click node',

  'designer.shortcuts.gesture.dragPort': 'Drag port',

  'designer.shortcuts.gesture.dragNode': 'Drag node',

  'designer.help.title': 'Help',

  'designer.help.section.quickStart': 'Quick start',

  'designer.help.section.nodeTypes': 'Node types',

  'designer.help.section.faq': 'FAQ',

  'designer.help.section.bestPractices': 'Best practices',

  'designer.help.section.links': 'Links',

  'designer.help.quickStart.step1.title': 'Step 1: Create a flow',

  'designer.help.quickStart.step1.li1': 'Drag a Start node from the palette onto the canvas',

  'designer.help.quickStart.step1.li2': 'Add the tasks and gateways your flow needs',

  'designer.help.quickStart.step1.li3': 'Add an End node',

  'designer.help.quickStart.step2.title': 'Step 2: Connect nodes',

  'designer.help.quickStart.step2.li1':
    'Drag from one node connector to another to create a connection',

  'designer.help.quickStart.step2.li2': 'Click a connection to edit its condition expression',

  'designer.help.quickStart.step3.title': 'Step 3: Configure properties',

  'designer.help.quickStart.step3.li1': 'Select a node and edit its properties in the right panel',

  'designer.help.quickStart.step3.li2': 'Auto task: configure a Java method or Spring Bean action',

  'designer.help.quickStart.step3.li3': 'Script task: choose a language and enter the script',

  'designer.help.quickStart.step3.li4': 'Exclusive gateway: configure conditions on outgoing edges',

  'designer.help.quickStart.step4.title': 'Step 4: Validate and debug',

  'designer.help.quickStart.step4.li1': 'Click Validate in the toolbar to check for errors',

  'designer.help.quickStart.step4.li2': 'Select Debug to simulate the flow in the browser',

  'designer.help.quickStart.step4.li3': 'Set breakpoints and step through the flow',

  'designer.help.quickStart.step5.title': 'Step 5: Save and export',

  'designer.help.quickStart.step5.li1': 'Select Save to save the flow',

  'designer.help.quickStart.step5.li2': 'Export XML to download the flow definition',

  'designer.help.quickStart.step5.li3': 'Export the diagram as PNG and SVG images',

  'designer.help.nodeTypes.flowControl': 'Process control',

  'designer.help.nodeTypes.start': 'Start — the flow entry point',

  'designer.help.nodeTypes.end': 'End — the flow exit point',

  'designer.help.nodeTypes.tasks': 'Tasks',

  'designer.help.nodeTypes.autoTask': 'Auto task — invoke a Java method or Spring Bean',

  'designer.help.nodeTypes.waitTask': 'Wait task — wait for external trigger',

  'designer.help.nodeTypes.waitEvent': 'Wait event — wait for a specific event',

  'designer.help.nodeTypes.timerTask': 'Timer task — Durable wait for a duration or instant',

  'designer.help.nodeTypes.scriptTask': 'Script task — run inline code',

  'designer.help.nodeTypes.gateways': 'Gateways',

  'designer.help.nodeTypes.exclusive': 'Exclusive gateway — select one branch (XOR)',

  'designer.help.nodeTypes.parallel': 'Parallel gateway — run all branches (AND)',

  'designer.help.nodeTypes.inclusive': 'Inclusive gateway — run all matching branches (OR)',

  'designer.help.nodeTypes.subprocess': 'Subprocess',

  'designer.help.nodeTypes.subBpm': 'Embedded BPM — group nodes within the current BPM',

  'designer.help.nodeTypes.bpmCall': 'BPM call — invoke another BPM definition',

  'designer.help.nodeTypes.while': 'While loop — repeat while a Java condition is true',

  'designer.help.nodeTypes.foreach':
    'Foreach loop — traverse a collection sequentially or in Durable parallel mode',

  'designer.help.nodeTypes.loopControl': 'Loop control',

  'designer.help.nodeTypes.continue': 'Continue — skip to next iteration',

  'designer.help.nodeTypes.break': 'Break — exit the loop',

  'designer.help.nodeTypes.other': 'Other',

  'designer.help.nodeTypes.note': 'Note — annotation only, not executed',

  'designer.help.faq.branch.q': 'How do I create conditional branches?',

  'designer.help.faq.branch.a':
    'Use an Exclusive gateway, then set a condition on each outgoing connection.',

  'designer.help.faq.branch.example': 'Example: edge 1 → amount > 1000, edge 2 → amount <= 1000',

  'designer.help.faq.panel.q': 'Why does the properties panel not respond when I select a node?',

  'designer.help.faq.panel.a': 'Try these steps:',

  'designer.help.faq.panel.li1': 'Refresh the page and reload the flow',

  'designer.help.faq.panel.li2': 'Check the browser console for errors',

  'designer.help.faq.panel.li3':
    'Ensure the node is on the canvas (check node count in status bar)',

  'designer.help.faq.variables.q': 'How do I reference flow variables?',

  'designer.help.faq.variables.a': 'Use the variable name directly in expressions.',

  'designer.help.faq.variables.example': 'Example: amount > 1000 && status == "active"',

  'designer.help.faq.variables.tip': 'Define process variables before using them in expressions',

  'designer.help.faq.validation.q': 'How do I fix validation issues?',

  'designer.help.faq.validation.a': 'Select an issue in the Validation panel to view its details:',

  'designer.help.faq.validation.li1': 'Process must have start and end nodes',

  'designer.help.faq.validation.li2': 'Auto tasks must configure a complete action',

  'designer.help.faq.validation.li3':
    'Exclusive gateways need at least 2 outgoing edges with conditions',

  'designer.help.faq.validation.li4': 'Check for isolated nodes (not connected)',

  'designer.help.faq.debug.q': 'How do I use the debugger?',

  'designer.help.faq.debug.a': 'To debug a flow:',

  'designer.help.faq.debug.li1': 'Open the Debug panel from the toolbar',

  'designer.help.faq.debug.li2': 'Enter initial variables as JSON',

  'designer.help.faq.debug.li3': 'Optionally set breakpoints on nodes',

  'designer.help.faq.debug.li4': 'Select Start to run the simulation',

  'designer.help.faq.debug.li5': 'Watch Variables and Execution log panels',

  'designer.help.faq.rubberband.q': 'How do I select multiple nodes with a selection box?',

  'designer.help.faq.rubberband.a': 'Hold Shift and drag on the canvas to select multiple nodes.',

  'designer.help.faq.rubberband.tip':
    'You can then move, copy, or delete the selected nodes together',

  'designer.help.practices.naming.title': 'Naming conventions',

  'designer.help.practices.naming.li1':
    'Node names: verb + noun, e.g. "Validate order", "Send email"',

  'designer.help.practices.naming.li2': 'Variables: camelCase, e.g. orderAmount, userName',

  'designer.help.practices.naming.li3':
    'Process code: lowercase with underscores, e.g. order_process',

  'designer.help.practices.design.title': 'Design principles',

  'designer.help.practices.design.li1': 'Single responsibility: one flow, one purpose',

  'designer.help.practices.design.li2':
    'Keep flows readable by splitting large sections into subprocesses',

  'designer.help.practices.design.li3': 'Define failure handling for critical actions',

  'designer.help.practices.design.li4': 'Readability: use Note nodes for complex logic',

  'designer.help.practices.performance.title': 'Performance',

  'designer.help.practices.performance.li1': 'Avoid slow operations inside loops',

  'designer.help.practices.performance.li2':
    'Use parallel gateways when branches can run concurrently',

  'designer.help.practices.performance.li3': 'Use subprocesses to keep definitions manageable',

  'designer.help.practices.performance.li4': 'Prefer Java methods over heavy script logic',

  'designer.help.practices.security.title': 'Security',

  'designer.help.practices.security.li1': 'Do not hardcode secrets in scripts',

  'designer.help.practices.security.li2':
    'Store sensitive data in your application configuration or secret store',

  'designer.help.practices.security.li3': 'Do not build executable code from untrusted input',

  'designer.help.practices.security.li4': 'Review scripts and Java actions before server execution',

  'designer.help.links.github': 'CompileFlow on GitHub',

  'designer.help.links.githubDesc': '— source code and documentation',

  'designer.help.links.docsZh': 'CompileFlow docs (Chinese)',

  'designer.help.links.docsZhDesc': '— full user guide',

  'designer.help.links.nodeSupport': 'Node support list',

  'designer.help.links.nodeSupportDesc': '— supported TBBPM nodes',

  'designer.help.links.issues': 'Issue tracker',

  'designer.help.links.issuesDesc': '— report bugs or suggest features',
} as const

export default enAuthoring
