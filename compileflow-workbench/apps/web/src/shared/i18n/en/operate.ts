const enOperate = {
  // Error Messages

  'process.management': 'Processes',

  'process.managementDesc': 'Manage, edit, and publish process definitions.',

  'process.publish': 'Publish',

  'process.code': 'Code',

  'process.name': 'Name',

  'process.type': 'Type',

  'process.updatedAt': 'Updated',

  'process.allTypes': 'All types',

  'process.create': 'Create process',

  'process.import': 'Import',

  'process.deleteSuccess': 'Process deleted',

  'process.duplicateSuccess': 'Process duplicated',

  'process.createdBy': 'Created by',

  'process.deleteError': 'Could not delete the process. Try again.',

  'process.duplicateError': 'Could not duplicate the process. Try again.',

  'process.createError': 'Could not create the process. Try again.',

  'process.newProcessDefaultName': 'Untitled process',

  'process.duplicateName': '{{name}} (copy)',

  'process.publishSuccess': 'Process published',

  'process.publishError': 'Could not publish. Try again.',

  'process.importSuccess': 'Process imported',

  'process.importError': 'Import failed. Check the XML and try again.',

  // Operate Module - Monitoring

  'monitoring.partialLoadError': 'Some monitoring sources could not be refreshed',

  'monitoring.partialLoadErrorDescription':
    'Unavailable sources: {{sources}}. Other panels continue to refresh; stale panels retain their last successful values.',

  'monitoring.staleData': 'Refresh failed; showing the last successful value',

  'monitoring.source.metrics': 'execution metrics',

  'monitoring.source.trends': 'execution trends',

  'monitoring.source.topProcesses': 'top processes',

  'monitoring.source.errors': 'recent errors',

  'monitoring.source.versionDistribution': 'version distribution',

  'monitoring.source.deployRuntime': 'deploy runtime',

  'monitoring.source.routingOutboxControl': 'deployment outbox',

  'monitoring.source.asyncHealth': 'async invocation queue',

  // Operate Module - Deployment Management

  'deployment.processName': 'Process',

  'deployment.version': 'Version',

  'deployment.alias': 'Alias',

  'deployment.alias.dev': 'Development (DEV)',

  'deployment.alias.staging': 'Staging (STAGING)',

  'deployment.alias.production': 'Production (PRODUCTION)',

  'deployment.loadDetailError': 'Could not load deployment details.',

  'deployment.operation': 'Operation',

  'deployment.operation.deploy': 'Deploy',

  'deployment.operation.rollback': 'Rollback',

  'deployment.strategy': 'Strategy',

  'deployment.strategy.all_at_once': 'All at once',

  'deployment.strategy.canary': 'Canary',

  'deployment.status': 'Status',

  'deployment.detail': 'Deployment',

  'deployment.baselineVersion': 'Baseline',

  'deployment.baseRouteRevision': 'Starting route version',

  'deployment.routeRevision': 'Committed route version',

  'deployment.deployedAt': 'Deployed At',

  'deployment.newDeployment': 'New deployment',

  'deployment.deploySuccess': 'Deployed',

  'deployment.rollback': 'Rollback',

  'deployment.rollbackSuccess': 'Rolled back',

  'deployment.rollbackConfirm': 'Confirm rollback',

  'deployment.rollbackWarning': 'This restores the process to a previous version. Continue?',

  'deployment.routeChanged':
    'This deployment no longer owns the active route. The list has been refreshed.',

  'deployment.routeSuperseded':
    'A newer deployment owns this route. Mutating operations are disabled.',

  'deployment.abortMissingBaseline': 'Cannot abort canary without a captured baseline version.',

  'deployment.rollbackMissingBaseline': 'Cannot roll back without a captured baseline version.',

  'deployment.routeControl': 'Route Control',

  'deployment.rollbackHint':
    'Rollback creates a new audited deployment that restores the captured baseline.',

  'deployment.partialRefresh':
    'The operation succeeded, but related route or event data could not be refreshed.',

  'deployment.abort': 'Abort',

  'deployment.abortSuccess': 'Aborted',

  'deployment.abortConfirm': 'Abort Canary',

  'deployment.abortWarning': 'Stop the canary and send all traffic back to the stable version?',

  'deployment.processCode': 'Process Code',

  'deployment.selectProcessPlaceholder': 'Select a process',

  'deployment.canaryWeightBps': 'Canary traffic weight',

  'deployment.notes': 'Notes',

  'deployment.notesPlaceholder': 'Add a deployment note…',

  'deployment.status.in_progress': 'In progress',

  'deployment.status.completed': 'Completed',

  'deployment.status.aborted': 'Aborted',

  'deployment.history': 'History',

  'deployment.noEvents': 'No deployment events',

  'deployment.event.CANARY_STARTED': 'Canary Started',

  'deployment.event.COMPLETED': 'Completed',

  'deployment.event.CANARY_WEIGHT_UPDATED': 'Canary weight updated',

  'deployment.event.PROMOTED': 'Promoted',

  'deployment.event.ABORTED': 'Aborted',

  'deployment.event.enteredPhase': 'Entered {{phase}}',

  'deployment.event.phaseTransition': '{{from}} -> {{to}}',

  'deployment.event.actor': 'Actor: {{actor}}',

  'deployment.phase.in_progress': 'In progress',

  'deployment.phase.completed': 'Completed',

  'deployment.phase.aborted': 'Aborted',

  'deployment.canaryControl': 'Canary release',

  'deployment.canaryControlHint':
    'Evaluate observed traffic, adjust the cohort, promote the candidate, or restore the stable version.',

  'deployment.updateCanary': 'Update weight',

  'deployment.canaryUpdated': 'Canary weight updated',

  'deployment.canaryUpdateError': 'Could not update the canary weight.',

  'deployment.evaluateHealth': 'Evaluate health',

  'deployment.healthEvaluationError': 'Could not evaluate Canary health.',

  'deployment.health.healthy': 'Canary is healthy',

  'deployment.health.unhealthy': 'Canary is unhealthy',

  'deployment.health.insufficient_data': 'More Canary samples are required',

  'deployment.health.candidateSamples': 'Candidate Samples',

  'deployment.health.baselineSamples': 'Baseline Samples',

  'deployment.health.candidateErrorRate': 'Candidate error rate',

  'deployment.health.candidateP95': 'Candidate p95',

  'deployment.promoteCanary': 'Promote to 100%',

  'deployment.canaryPromoted': 'Canary promoted to full traffic',

  'deployment.canaryPromoteError': 'Could not promote the Canary.',

  'deployment.promoteConfirm': 'Promote candidate',

  'deployment.promoteWarning.healthy':
    'The latest health evaluation passed. Move all traffic to the candidate version?',

  'deployment.promoteWarning.unhealthy':
    'The latest health evaluation failed. Promoting will still move all traffic to the candidate version.',

  'deployment.promoteWarning.insufficient_data':
    'The latest evaluation does not have enough samples. Promote without sufficient evidence?',

  'deployment.promoteWarning.notEvaluated':
    'No health evaluation has been run for the current Canary version. Promote anyway?',

  // Operate Module - Deployment Wizard

  'deployment.wizard.title': 'Deploy',

  'deployment.wizard.subtitle': 'Three steps to route an alias to a published process version.',

  'deployment.wizard.selectProcess': 'Select process',

  'deployment.wizard.selectAlias': 'Select alias',

  'deployment.wizard.confirm': 'Confirm',

  'deployment.wizard.step1.desc': 'Choose the process to deploy',

  'deployment.wizard.step2.desc': 'Choose the routing alias',

  'deployment.wizard.step3.desc': 'Review and deploy',

  'deployment.wizard.selectProcessLabel': 'Process',

  'deployment.wizard.selectProcessRequired': 'Select a process to deploy',

  'deployment.wizard.versionRequired': 'Select a published version',

  'deployment.wizard.versionPlaceholder': 'Published version',

  'deployment.wizard.noPublishedVersions': 'No published versions',

  'deployment.wizard.versionLoadFailed': 'Could not load published versions',

  'deployment.wizard.selectAliasRequired': 'Enter a routing alias',

  'deployment.wizard.selectAliasPlaceholder': 'Alias',

  'deployment.wizard.aliasInvalid':
    'Use 1–64 letters, digits, dots, underscores, or hyphens; start with a letter or digit.',

  'deployment.wizard.step1.hint': 'Make sure the process is tested before you deploy.',

  'deployment.wizard.step2.warningTitle': 'Before changing a live alias',

  'deployment.wizard.step2.warningDesc':
    'Confirm that the selected version is ready for traffic on this alias.',

  'deployment.wizard.confirmTitle': 'Confirm deployment',

  'deployment.wizard.processType': 'Process Type',

  'deployment.wizard.readyTitle': 'Ready to deploy',

  'deployment.wizard.readyDesc': 'Review the details above, then deploy.',

  'deployment.wizard.deployFailed': 'Deployment failed—try again.',

  'deployment.wizard.deploySuccess': 'Deployed',

  'deployment.wizard.deploySuccessDesc': '{{process}} is routed through {{alias}}',

  'deployment.wizard.viewDeployment': 'View deployment',

  'deployment.wizard.continueDeploy': 'Deploy another',

  'deployment.wizard.deploying': 'Deploying…',

  'monitoring.title': 'Monitoring',

  'monitoring.subtitle': 'Execution metrics observed by this Workbench server and runtime health.',

  'monitoring.totalExecutions': 'Executions',

  'monitoring.successExecutions': 'Succeeded',

  'monitoring.failedExecutions': 'Failed',

  'monitoring.successRate': 'Success rate',

  'monitoring.executionTrends': 'Trends',

  'monitoring.topProcesses': 'Top processes',

  'monitoring.recentErrors': 'Recent errors',

  'monitoring.noData': 'No data',

  'monitoring.noErrors': 'No errors',

  'monitoring.success': 'Success',

  'monitoring.failed': 'Failed',

  'monitoring.opsControlPlane': 'Control plane',

  'monitoring.opsActionFailed': 'Operation failed',

  'monitoring.opsRefreshFailed': 'Done, but status refresh failed',

  'monitoring.deploymentOutbox': 'Deployment outbox',

  'monitoring.deploymentRunning': 'Routing delivery is active',

  'monitoring.deploymentStopped': 'Routing delivery is stopped',

  'monitoring.outboxPending': 'Pending',

  'monitoring.outboxProcessing': 'Processing',

  'monitoring.outboxExpiredClaims': 'Expired claims',

  'monitoring.outboxDeadLetters': 'Dead letters',

  'monitoring.dispatcherRunning': 'Dispatcher',

  'monitoring.deploymentDeadLettersRequeued': 'Deployment dead letters requeued',

  'monitoring.requeueDeploymentDeadLetters': 'Requeue deployment dead letters',

  'monitoring.asyncInvocationQueue': 'Async invocation queue',

  'monitoring.asyncInvocationQueueTitle': 'Persisted async invocation retry pipeline',

  'monitoring.asyncReady': 'Ready',

  'monitoring.asyncOldestReady': 'Oldest ready',

  'monitoring.asyncDelayed': 'Delayed',

  'monitoring.asyncRunning': 'Running',

  'monitoring.asyncExpired': 'Expired leases',

  'monitoring.asyncDeadLetters': 'Dead letters',

  'monitoring.asyncWorker': 'Worker',

  'monitoring.asyncLease': 'Lease',

  'monitoring.asyncBatch': 'Batch',

  'monitoring.asyncDeadLettersRequeued': 'Async dead letters requeued',

  'monitoring.requeueAsyncInvocationDeadLetters': 'Requeue async dead letters',

  'monitoring.invocationLedgerEyebrow': 'Persisted runtime work',

  'monitoring.invocationLedger': 'Async invocation ledger',

  'monitoring.invocationLedgerDescription':
    'Inspect logical invocations, physical attempts, retry decisions, and engine traces.',

  'monitoring.invocationLedgerStale': 'Invocation refresh failed; showing the last successful page',

  'monitoring.invocationProcessFilter': 'Process code',

  'monitoring.invocationStatusFilter': 'Invocation status',

  'monitoring.invocationProcess': 'Process',

  'monitoring.invocationId': 'Invocation ID',

  'monitoring.invocationStatus': 'Status',

  'monitoring.invocationStatus.queued': 'Queued',

  'monitoring.invocationStatus.running': 'Running',

  'monitoring.invocationStatus.succeeded': 'Succeeded',

  'monitoring.invocationStatus.dead_letter': 'Dead letter',

  'monitoring.invocationAttempts': 'Attempts',

  'monitoring.invocationAttemptSummary':
    '{{total}} lifetime, {{current}}/{{maximum}} current generation, {{redrives}} redrives',

  'monitoring.invocationVersion': 'Effective version',

  'monitoring.invocationUpdatedAt': 'Updated',

  'monitoring.invocationDetail': 'Async invocation detail',

  'monitoring.invocationDetailFailed': 'Invocation detail could not be loaded',

  'monitoring.invocationAttemptHistory': 'Attempt history',

  'monitoring.invocationAttemptGeneration': 'redrive {{redrive}}, attempt {{attempt}}',

  'monitoring.invocationWorker': 'Worker',

  'monitoring.invocationStartedAt': 'Started',

  'monitoring.invocationFinishedAt': 'Finished',

  'monitoring.invocationNextAttemptAt': 'Next attempt',

  'monitoring.invocationDuration': 'Duration',

  'monitoring.invocationAttemptFailed': 'Attempt failed',

  'monitoring.invocationOutcome.running': 'Running',

  'monitoring.invocationOutcome.succeeded': 'Succeeded',

  'monitoring.invocationOutcome.failed': 'Failed',

  'monitoring.invocationOutcome.lease_expired': 'Lease expired',

  'monitoring.invocationDisposition.succeeded': 'Completed',

  'monitoring.invocationDisposition.retry_scheduled': 'Retry scheduled',

  'monitoring.invocationDisposition.dead_lettered': 'Dead-lettered',

  'monitoring.noInvocations': 'No async invocations',

  'monitoring.noInvocationAttempts': 'No attempts',

  'monitoring.requeueInvocation': 'Requeue',

  'monitoring.invocationRequeued': 'Async invocation requeued',

  'monitoring.invocationRequeueConfirm': 'Requeue this dead-letter invocation?',

  'monitoring.invocationRequeueWarning':
    'Repair the cause first. Redrive is at-least-once and may repeat external effects.',

  'monitoring.statusUnavailable': 'Status unavailable',

  'monitoring.unavailable': 'Unavailable',

  'monitoring.deployRuntime': 'Deploy runtime data plane',

  'monitoring.deployRuntimeLocalNode': 'Local node diagnostics',

  'monitoring.deployRuntimeAvailable': 'Runtime hot deploy is available',

  'monitoring.deployRuntimeUnavailable': 'Runtime hot deploy is unavailable',

  'monitoring.runtimeStarted': 'Started',

  'monitoring.runtimeInflight': 'Inflight installs',

  'monitoring.runtimeDesiredAliases': 'Desired aliases',

  'monitoring.runtimeLocalReadyAliases': 'Local-ready aliases',

  'monitoring.runtimePendingAliases': 'Pending aliases',

  'monitoring.runtimeFailedAliases': 'Failed aliases',

  'monitoring.runtimeDemanded': 'Demanded versions',

  'monitoring.runtimeBackedOff': 'Versions in retry backoff',

  'monitoring.runtimeAliases': 'Alias convergence',

  'monitoring.runtimeAliasState.local_ready': 'Local ready',

  'monitoring.runtimeAliasState.pending': 'Pending',

  'monitoring.runtimeAliasState.failed': 'Failed',

  'monitoring.runtimeAliasRevisions': 'desired r{{desired}}, local-ready r{{localReady}}',

  'monitoring.runtimeDemandedList': 'Demanded',

  'monitoring.runtimeBackedOffList': 'Retry backoff',

  'monitoring.runtimeDeployedList': 'Installed locally',

  'monitoring.versionDistribution': 'Version distribution',

  'monitoring.version': 'Version',

  'monitoring.timeRange.1h': '1h',
  'monitoring.timeRangeLabel': 'Time range',

  'monitoring.timeRange.6h': '6h',

  'monitoring.timeRange.24h': '24h',

  'monitoring.timeRange.7d': '7d',

  'monitoring.timeRange.30d': '30d',

  // Operate Module - Logs

  'logs.title': 'Logs',

  'logs.subtitle': 'Search and inspect process execution history.',

  'logs.namespace': 'Namespace',

  'logs.requestedVersion': 'Requested version',

  'logs.effectiveVersion': 'Effective version',

  'logs.routingSource': 'Routing source',

  'logs.routeAlias': 'Route alias',

  'logs.routeRevision': 'Route revision',

  'logs.endTime': 'Ended',

  'logs.modelType': 'Model type',

  'logs.sourceDigest': 'Source digest',

  'logs.dateRange': 'Execution date range',

  'logs.startDatePlaceholder': 'Start date',

  'logs.endDatePlaceholder': 'End date',

  'logs.processCode': 'Process',

  'logs.invocationId': 'Invocation',

  'logs.parentInvocationId': 'Parent invocation',

  'logs.callDepth': 'Depth',

  'logs.traceId': 'Trace',

  'logs.status': 'Status',

  'logs.duration': 'Duration',

  'logs.startTime': 'Started',

  'logs.searchPlaceholder': 'Search logs…',

  'logs.selectStatus': 'Status',

  'logs.export': 'Export',

  'logs.noData': 'No logs yet',

  // Logs extras

  'filters.clear': 'Clear filters',

  'filters.active': 'Active filters:',

  'filters.difficulty': 'Difficulty',

  'filters.category': 'Category',

  'filters.search': 'Search',

  'filters.resultCount': '{{count}} examples found',

  // Categories (ExampleList filter options)

  'sort.name': 'Name',

  'sort.difficulty': 'Difficulty',

  'sort.duration': 'Duration',

  // Process Type Filter

  'logs.detail': 'Log detail',

  'logs.errorCode': 'Error code',

  'logs.errorMessage': 'Error message',

  'logs.exportSuccess': 'Exported',

  'logs.status.success': 'Success',

  'logs.status.failed': 'Failed',

  // Error extras

  'process.searchPlaceholder': 'Search processes…',

  'process.deleteConfirm': 'Delete this process?',

  // Deployment extras

  'deployment.management': 'Deployments',

  'deployment.managementDesc':
    'Track and manage published versions routed through process aliases.',

  'deployment.canaryRange': 'Canary weight must be between 1 and 9,999 basis points.',
  'deployment.canaryInteger': 'Canary weight must be a whole number of basis points.',

  'deployment.canaryRequired': 'Enter a canary traffic weight.',

  'deployment.health.reason.withinThresholds':
    'Canary metrics are within the configured health thresholds.',

  'deployment.health.metricsScope': 'Metrics scope',

  'deployment.health.metricsScope.workbench_server': 'This Workbench server',

  'deployment.health.metricsSource': 'Metrics source',

  'deployment.health.metricsSource.execution_logs': 'Execution logs',

  'deployment.deploy': 'Deploy',

  'deployment.searchPlaceholder': 'Search by process code or deployment ID',

  'deployment.filterStatus': 'Status',

  'deployment.filterAlias': 'Alias',

  // Feedback

  'operate.title': 'Operate',

  'operate.subtitle':
    'Publish immutable versions, roll out safely, and trace every execution to its exact source.',

  'operate.deploy': 'Deploy',

  'operate.monitor': 'Monitoring',

  'operate.explore': 'Shortcuts',

  'monitoring.runtimeTopology': 'Runtime topology',

  'operate.processMgmt': 'Processes',

  'operate.processMgmtDesc': 'Browse and manage process definitions.',

  'operate.deployMgmt': 'Deployments',

  'operate.deployMgmtDesc': 'Control published versions and Alias rollouts.',

  'operate.realtimeMonitor': 'Monitoring',

  'operate.realtimeMonitorDesc': 'Execution health and performance metrics.',

  'operate.logQuery': 'Logs',

  'operate.logQueryDesc': 'Search and analyze execution logs.',

  'operate.processesWithAliases': 'Processes with aliases',

  'operate.unit.process': '',

  'operate.unit.items': '',

  'operate.unit.times': '',

  'operate.processExecutions': 'Executions (since start)',

  'operate.successRate': 'Success rate',

  'operate.failedExecutions': 'Failures',

  'operate.recentDeploys': 'Recent deployments',

  'operate.noRecentDeployments': 'No recent deployments',

  'operate.recentDeploymentsUnavailable': 'Recent deployments unavailable',

  'operate.viewAll': 'View all',

  'operate.deploying': 'Deploying',

  'operate.failed': 'Failed',

  'operate.pending': 'Pending',

  // Monitoring error detail

  'monitoring.errorCount': 'Count',

  'monitoring.lastOccurred': 'Last',

  // Learning progress

  'process.editInDesigner': 'Edit in designer',

  // Designer
} as const

export default enOperate
