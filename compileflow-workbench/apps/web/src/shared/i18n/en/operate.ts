const enOperate = {
  // Process management

  'process.management': 'Processes',

  'process.managementDesc': 'Create, edit, and publish process definitions.',

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

  // Monitoring

  'monitoring.partialLoadError': 'Some monitoring sources could not be refreshed',

  'monitoring.partialLoadErrorDescription':
    'Unavailable sources: {{sources}}. Other panels will continue to refresh; affected panels show their latest available data.',

  'monitoring.staleData': 'Could not refresh; showing the latest available data',

  'monitoring.source.metrics': 'execution metrics',

  'monitoring.source.trends': 'execution trends',

  'monitoring.source.topProcesses': 'top processes',

  'monitoring.source.errors': 'recent errors',

  'monitoring.source.versionDistribution': 'version distribution',

  'monitoring.source.deployRuntime': 'deployment runtime',

  'monitoring.source.routingOutboxControl': 'deployment delivery queue',

  'monitoring.source.asyncHealth': 'async invocation queue',

  // Deployment management

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

  'deployment.strategy.canary': 'Canary rollout',

  'deployment.status': 'Status',

  'deployment.detail': 'Deployment details',

  'deployment.baselineVersion': 'Baseline version',

  'deployment.baseRouteRevision': 'Starting route revision',

  'deployment.routeRevision': 'Applied route revision',

  'deployment.deployedAt': 'Deployed at',

  'deployment.newDeployment': 'New deployment',

  'deployment.deploySuccess': 'Deployed',

  'deployment.rollback': 'Rollback',

  'deployment.rollbackSuccess': 'Rolled back',

  'deployment.rollbackConfirm': 'Confirm rollback',

  'deployment.rollbackWarning': 'Restore the baseline version for this deployment?',

  'deployment.routeChanged': 'The active route changed. Deployment data has been refreshed.',

  'deployment.routeSuperseded':
    'A newer deployment changed this route. Further changes are disabled.',

  'deployment.abortMissingBaseline':
    'Cannot abort the canary because its baseline version is unavailable.',

  'deployment.rollbackMissingBaseline':
    'Cannot roll back because the baseline version is unavailable.',

  'deployment.routeControl': 'Route control',

  'deployment.rollbackHint':
    'Rollback creates a deployment record and restores the baseline version.',

  'deployment.partialRefresh':
    'The operation completed, but route or event data could not be refreshed.',

  'deployment.abort': 'Abort canary',

  'deployment.abortSuccess': 'Canary aborted',

  'deployment.abortConfirm': 'Abort canary',

  'deployment.abortWarning': 'Stop the canary and return all traffic to the stable version?',

  'deployment.processCode': 'Process code',

  'deployment.selectProcessPlaceholder': 'Select a process',

  'deployment.canaryWeightBps': 'Canary traffic weight',

  'deployment.notes': 'Notes',

  'deployment.notesPlaceholder': 'Add a deployment note…',

  'deployment.status.in_progress': 'In progress',

  'deployment.status.completed': 'Completed',

  'deployment.status.aborted': 'Aborted',

  'deployment.history': 'Deployment history',

  'deployment.noEvents': 'No deployment events',

  'deployment.event.CANARY_STARTED': 'Canary started',

  'deployment.event.COMPLETED': 'Deployment completed',

  'deployment.event.CANARY_WEIGHT_UPDATED': 'Canary weight updated',

  'deployment.event.PROMOTED': 'Promoted to full traffic',

  'deployment.event.ABORTED': 'Canary aborted',

  'deployment.event.enteredPhase': 'Status changed to {{phase}}',

  'deployment.event.phaseTransition': '{{from}} → {{to}}',

  'deployment.event.actor': 'Changed by: {{actor}}',

  'deployment.phase.in_progress': 'In progress',

  'deployment.phase.completed': 'Completed',

  'deployment.phase.aborted': 'Aborted',

  'deployment.canaryControl': 'Canary release',

  'deployment.canaryControlHint':
    'Review traffic metrics, adjust the canary weight, promote the candidate, or restore the stable version.',

  'deployment.updateCanary': 'Adjust weight',

  'deployment.canaryUpdated': 'Canary weight updated',

  'deployment.canaryUpdateError': 'Could not update the canary weight.',

  'deployment.evaluateHealth': 'Evaluate health',

  'deployment.healthEvaluationError': 'Could not evaluate canary health.',

  'deployment.health.healthy': 'Canary is healthy',

  'deployment.health.unhealthy': 'Canary is unhealthy',

  'deployment.health.insufficient_data': 'More canary samples are required',

  'deployment.health.candidateSamples': 'Candidate samples',

  'deployment.health.baselineSamples': 'Baseline samples',

  'deployment.health.candidateErrorRate': 'Candidate error rate',

  'deployment.health.candidateP95': 'Candidate P95 latency',

  'deployment.promoteCanary': 'Promote to full traffic',

  'deployment.canaryPromoted': 'Candidate promoted to full traffic',

  'deployment.canaryPromoteError': 'Could not promote the candidate.',

  'deployment.promoteConfirm': 'Confirm full rollout',

  'deployment.promoteWarning.healthy':
    'The latest health evaluation passed. Move all traffic to the candidate version?',

  'deployment.promoteWarning.unhealthy':
    'The latest health evaluation failed. Continue and move all traffic to the candidate version?',

  'deployment.promoteWarning.insufficient_data':
    'The latest evaluation does not have enough samples. Promote the candidate anyway?',

  'deployment.promoteWarning.notEvaluated':
    'The current candidate has not been evaluated. Promote it anyway?',

  // Deployment wizard

  'deployment.wizard.title': 'Deploy',

  'deployment.wizard.subtitle': 'Route an alias to a published process version in three steps.',

  'deployment.wizard.selectProcess': 'Select process',

  'deployment.wizard.selectAlias': 'Select alias',

  'deployment.wizard.confirm': 'Review',

  'deployment.wizard.step1.desc': 'Choose the process to deploy',

  'deployment.wizard.step2.desc': 'Choose the route alias',

  'deployment.wizard.step3.desc': 'Review and deploy',

  'deployment.wizard.selectProcessLabel': 'Process',

  'deployment.wizard.selectProcessRequired': 'Select a process to deploy',

  'deployment.wizard.versionRequired': 'Select a published version',

  'deployment.wizard.versionPlaceholder': 'Select a published version',

  'deployment.wizard.noPublishedVersions': 'No published versions',

  'deployment.wizard.versionLoadFailed': 'Could not load published versions',

  'deployment.wizard.selectAliasRequired': 'Enter a route alias',

  'deployment.wizard.selectAliasPlaceholder': 'Alias',

  'deployment.wizard.aliasInvalid':
    'Use 1–64 letters, digits, dots, underscores, or hyphens; start with a letter or digit.',

  'deployment.wizard.step1.hint': 'Make sure the process has been tested before deployment.',

  'deployment.wizard.step2.warningTitle': 'Before changing an active alias',

  'deployment.wizard.step2.warningDesc':
    'Confirm that the selected version is ready for traffic on this alias.',

  'deployment.wizard.confirmTitle': 'Confirm deployment',

  'deployment.wizard.processType': 'Process type',

  'deployment.wizard.readyTitle': 'Ready to deploy',

  'deployment.wizard.readyDesc': 'Review the details above, then deploy.',

  'deployment.wizard.deployFailed': 'Could not deploy. Try again.',

  'deployment.wizard.deploySuccess': 'Deployed',

  'deployment.wizard.deploySuccessDesc': '{{process}} has been deployed to {{alias}}',

  'deployment.wizard.viewDeployment': 'View deployment',

  'deployment.wizard.continueDeploy': 'Deploy another',

  'deployment.wizard.deploying': 'Deploying…',

  'monitoring.title': 'Monitoring',

  'monitoring.subtitle': 'Execution metrics and runtime health for this Workbench server.',

  'monitoring.totalExecutions': 'Executions',

  'monitoring.successExecutions': 'Succeeded',

  'monitoring.failedExecutions': 'Failed',

  'monitoring.successRate': 'Success rate',

  'monitoring.executionTrends': 'Trends',

  'monitoring.topProcesses': 'Process ranking',

  'monitoring.recentErrors': 'Recent errors',

  'monitoring.noData': 'No data',

  'monitoring.noErrors': 'No errors',

  'monitoring.success': 'Success',

  'monitoring.failed': 'Failed',

  'monitoring.opsControlPlane': 'Operations',

  'monitoring.opsActionFailed': 'Operation failed',

  'monitoring.opsRefreshFailed': 'Operation completed, but status could not be refreshed',

  'monitoring.deploymentOutbox': 'Deployment delivery queue',

  'monitoring.deploymentRunning': 'Deployment delivery is running',

  'monitoring.deploymentStopped': 'Deployment delivery is stopped',

  'monitoring.outboxPending': 'Pending',

  'monitoring.outboxProcessing': 'Processing',

  'monitoring.outboxExpiredClaims': 'Expired claims',

  'monitoring.outboxDeadLetters': 'Dead letters',

  'monitoring.dispatcherRunning': 'Delivery worker',

  'monitoring.deploymentDeadLettersRequeued': 'Dead-lettered deployment tasks requeued',

  'monitoring.requeueDeploymentDeadLetters': 'Requeue dead-lettered deployment tasks',

  'monitoring.requeueDeploymentDeadLettersConfirm': 'Requeue all deployment dead letters?',

  'monitoring.requeueDeploymentDeadLettersWarning':
    'These deployment tasks will be attempted again.',

  'monitoring.asyncInvocationQueue': 'Async invocation queue',

  'monitoring.asyncInvocationQueueTitle': 'Persistent queue for async invocation retries',

  'monitoring.asyncReady': 'Ready',

  'monitoring.asyncOldestReady': 'Longest wait',

  'monitoring.asyncDelayed': 'Delayed',

  'monitoring.asyncRunning': 'Running',

  'monitoring.asyncExpired': 'Expired leases',

  'monitoring.asyncDeadLetters': 'Dead letters',

  'monitoring.asyncWorker': 'Worker',

  'monitoring.asyncLease': 'Lease',

  'monitoring.asyncConcurrency': 'Concurrency limit',

  'monitoring.asyncDeadLettersRequeued': 'Dead-lettered async invocations requeued',

  'monitoring.requeueAsyncInvocationDeadLetters': 'Requeue dead-lettered async invocations',

  'monitoring.requeueAsyncDeadLettersConfirm': 'Requeue all async invocation dead letters?',

  'monitoring.requeueAsyncDeadLettersWarning':
    'These invocations may execute business actions again.',

  'monitoring.invocationLedgerEyebrow': 'Persistent async execution',

  'monitoring.invocationLedger': 'Async invocation history',

  'monitoring.invocationLedgerDescription':
    'Review each invocation, its execution attempts, retry decisions, and engine traces.',

  'monitoring.invocationLedgerStale':
    'Could not refresh invocations; showing the latest available data',

  'monitoring.invocationProcessFilter': 'Process code',

  'monitoring.invocationStatusFilter': 'Invocation status',

  'monitoring.invocationProcess': 'Process',

  'monitoring.invocationId': 'Invocation ID',

  'monitoring.invocationTraceId': 'Trace ID',

  'monitoring.invocationStatus': 'Status',

  'monitoring.invocationStatus.queued': 'Queued',

  'monitoring.invocationStatus.running': 'Running',

  'monitoring.invocationStatus.succeeded': 'Succeeded',

  'monitoring.invocationStatus.dead_letter': 'Dead letter',

  'monitoring.invocationAttempts': 'Execution attempts',

  'monitoring.invocationAttemptSummary':
    '{{total}} total · {{current}}/{{maximum}} this run · {{redrives}} requeues',

  'monitoring.invocationVersion': 'Effective version',

  'monitoring.invocationUpdatedAt': 'Updated',

  'monitoring.invocationDetail': 'Async invocation detail',

  'monitoring.invocationDetailFailed': 'Could not load invocation details',

  'monitoring.invocationAttemptHistory': 'Execution history',

  'monitoring.invocationAttemptGeneration': 'Requeues: {{redrive}} · Attempt: {{attempt}}',

  'monitoring.invocationWorker': 'Worker',

  'monitoring.invocationStartedAt': 'Started',

  'monitoring.invocationFinishedAt': 'Finished',

  'monitoring.invocationNextAttemptAt': 'Next attempt',

  'monitoring.invocationDuration': 'Duration',

  'monitoring.invocationAttemptFailed': 'Execution attempt failed',

  'monitoring.invocationOutcome.running': 'Running',

  'monitoring.invocationOutcome.succeeded': 'Succeeded',

  'monitoring.invocationOutcome.failed': 'Failed',

  'monitoring.invocationOutcome.lease_expired': 'Lease expired',

  'monitoring.invocationDisposition.succeeded': 'Completed',

  'monitoring.invocationDisposition.retry_scheduled': 'Retry scheduled',

  'monitoring.invocationDisposition.dead_lettered': 'Moved to dead-letter queue',

  'monitoring.noInvocations': 'No async invocations',

  'monitoring.noInvocationAttempts': 'No execution attempts',

  'monitoring.requeueInvocation': 'Requeue',

  'monitoring.invocationRequeued': 'Invocation requeued',

  'monitoring.invocationRequeueConfirm': 'Requeue this dead-lettered invocation?',

  'monitoring.invocationRequeueWarning':
    'Resolve the underlying issue first. Requeuing uses at-least-once delivery and may repeat external operations.',

  'monitoring.statusUnavailable': 'Status unavailable',

  'monitoring.unavailable': 'Unavailable',

  'monitoring.runtimeStatus.healthy': 'Healthy',

  'monitoring.runtimeStatus.degraded': 'Degraded',

  'monitoring.runtimeStatus.down': 'Down',

  'monitoring.runtimeStatus.stopped': 'Stopped',

  'monitoring.runtimeStatus.unavailable': 'Unavailable',

  'monitoring.deployRuntime': 'Deployment runtime',

  'monitoring.deployRuntimeLocalNode': 'Local node diagnostics',

  'monitoring.deployRuntimeAvailable': 'Runtime hot deploy is available',

  'monitoring.deployRuntimeUnavailable': 'Runtime hot deploy is unavailable',

  'monitoring.runtimeStarted': 'Started',

  'monitoring.runtimeInflight': 'Concurrent installs',

  'monitoring.runtimeDesiredAliases': 'Target aliases',

  'monitoring.runtimeLocalReadyAliases': 'Aliases ready locally',

  'monitoring.runtimePendingAliases': 'Aliases syncing',

  'monitoring.runtimeFailedAliases': 'Aliases with sync errors',

  'monitoring.runtimeDemanded': 'Required versions',

  'monitoring.runtimeBackedOff': 'Versions waiting to retry',

  'monitoring.runtimeAliases': 'Alias sync status',

  'monitoring.runtimeAliasState.local_ready': 'Local ready',

  'monitoring.runtimeAliasState.pending': 'Syncing',

  'monitoring.runtimeAliasState.failed': 'Failed',

  'monitoring.runtimeAliasRevisions': 'target r{{desired}}, local ready r{{localReady}}',

  'monitoring.runtimeDemandedList': 'Required versions',

  'monitoring.runtimeBackedOffList': 'Waiting to retry',

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

  'logs.subtitle': 'Search and review process execution history.',

  'logs.namespace': 'Namespace',

  'logs.requestedVersion': 'Requested version',

  'logs.effectiveVersion': 'Effective version',

  'logs.routingSource': 'Routing source',

  'logs.routingSource.alias': 'Alias route',

  'logs.routingSource.version': 'Explicit version',

  'logs.routingSource.definition': 'Current definition',

  'logs.routeAlias': 'Route alias',

  'logs.routeRevision': 'Route revision',

  'logs.endTime': 'Ended',

  'logs.modelType': 'Model type',

  'logs.sourceDigest': 'Source digest',

  'logs.dateRange': 'Execution date range',

  'logs.startDatePlaceholder': 'Start date',

  'logs.endDatePlaceholder': 'End date',

  'logs.processCode': 'Process code',

  'logs.invocationId': 'Invocation ID',

  'logs.parentInvocationId': 'Parent invocation ID',

  'logs.callDepth': 'Depth',

  'logs.traceId': 'Trace ID',

  'logs.status': 'Status',

  'logs.duration': 'Duration',

  'logs.startTime': 'Started',

  'logs.searchPlaceholder': 'Search logs…',

  'logs.selectStatus': 'Select status',

  'logs.export': 'Export',

  'logs.purge': 'Purge logs',

  'logs.purgeTitle': 'Purge historical execution logs',

  'logs.purgeBefore': 'Purge logs before this time',

  'logs.purgeWarning': 'Execution logs before the cutoff will be permanently deleted.',

  'logs.purgeConfirm': 'Purge logs',

  'logs.purgeSuccess': 'Purged {{count}} execution logs',

  'logs.purgeFailed': 'Failed to purge logs',

  'logs.noData': 'No execution logs',

  // Shared filters

  'filters.clear': 'Clear filters',

  'filters.active': 'Active filters:',

  'filters.difficulty': 'Difficulty',

  'filters.category': 'Category',

  'filters.search': 'Search',

  'filters.resultCount': '{{count}} examples found',

  // Sorting

  'sort.name': 'Name',

  'sort.difficulty': 'Difficulty',

  'sort.duration': 'Duration',

  // Log details

  'logs.detail': 'Log detail',

  'logs.errorCode': 'Error code',

  'logs.errorMessage': 'Error message',

  'logs.exportSuccess': 'Exported',

  'logs.status.success': 'Success',

  'logs.status.failed': 'Failed',

  // Process actions

  'process.searchPlaceholder': 'Search processes…',

  'process.deleteConfirm': 'Delete this process?',

  // Deployment filters and validation

  'deployment.management': 'Deployments',

  'deployment.managementDesc': 'Manage published versions and their route aliases.',

  'deployment.canaryRange': 'Canary weight must be between 1 and 9,999 basis points.',
  'deployment.canaryInteger': 'Canary weight must be a whole number of basis points.',

  'deployment.canaryRequired': 'Enter a canary traffic weight.',

  'deployment.health.reason.withinThresholds':
    'Canary metrics are within the configured thresholds.',

  'deployment.health.metricsScope': 'Metrics scope',

  'deployment.health.metricsScope.workbench_server': 'This Workbench server',

  'deployment.health.metricsSource': 'Metrics source',

  'deployment.health.metricsSource.execution_logs': 'Execution logs',

  'deployment.deploy': 'Deploy',

  'deployment.searchPlaceholder': 'Search by process code or deployment ID',

  'deployment.filterStatus': 'Status',

  'deployment.filterAlias': 'Alias',

  // Operate overview

  'operate.title': 'Operate',

  'operate.subtitle':
    'Manage process versions and releases, monitor execution health, and review logs.',

  'operate.deploy': 'Deploy process',

  'operate.monitor': 'Monitoring',

  'operate.explore': 'Quick access',

  'monitoring.runtimeTopology': 'Runtime topology',

  'monitoring.runtimeTopology.embedded': 'Embedded',

  'monitoring.runtimeTopology.distributed': 'Distributed',

  'operate.processMgmt': 'Processes',

  'operate.processMgmtDesc': 'Browse and manage process definitions.',

  'operate.deployMgmt': 'Deployments',

  'operate.deployMgmtDesc': 'Manage published versions and alias routing.',

  'operate.realtimeMonitor': 'Monitoring',

  'operate.realtimeMonitorDesc': 'Execution status and performance metrics.',

  'operate.logQuery': 'Logs',

  'operate.logQueryDesc': 'Search and review execution logs.',

  'operate.processesWithAliases': 'Processes with aliases',

  'operate.unit.process': '',

  'operate.unit.items': '',

  'operate.unit.times': '',

  'operate.processExecutions': 'Executions (since start)',

  'operate.successRate': 'Success rate',

  'operate.failedExecutions': 'Failures',

  'operate.recentDeploys': 'Recent deployments',

  'operate.noRecentDeployments': 'No deployments yet',

  'operate.recentDeploymentsUnavailable': 'Recent deployments unavailable',

  'operate.viewAll': 'View all',

  'operate.deploying': 'Deploying',

  'operate.failed': 'Failed',

  'operate.pending': 'Pending',

  // Monitoring error details

  'monitoring.errorCount': 'Count',

  'monitoring.lastOccurred': 'Last seen',

  // Designer

  'process.editInDesigner': 'Edit in designer',
} as const

export default enOperate
