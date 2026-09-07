import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { mkdir, readdir, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const workbenchRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const repositoryRoot = dirname(workbenchRoot)
const resultDirectory =
  process.env.COMPILEFLOW_E2E_RESULT_DIRECTORY ?? join(workbenchRoot, 'apps', 'web', 'test-results')
const resultPath = join(resultDirectory, 'async-invocation-process-lifecycle.json')
const serverPort = Number(process.env.COMPILEFLOW_E2E_LIFECYCLE_PORT ?? '18080')
const serverUrl = `http://127.0.0.1:${serverPort}`
const apiKey = requiredEnvironment('COMPILEFLOW_E2E_SERVER_API_KEY')

requiredEnvironment('SPRING_DATASOURCE_URL')
requiredEnvironment('SPRING_DATASOURCE_USERNAME')
requiredEnvironment('SPRING_DATASOURCE_PASSWORD')
assert(Number.isInteger(serverPort) && serverPort >= 1024 && serverPort <= 65_535, 'invalid port')

const jar = process.env.COMPILEFLOW_E2E_SERVER_JAR ?? (await findBundledJar())
const serverHistory = []
let activeServer
let interrupted
const interrupt = (signal) => {
  interrupted ??= new Error(`Lifecycle verification interrupted by ${signal}`)
  for (const server of serverHistory) server.process.kill('SIGTERM')
}
const onSigint = () => interrupt('SIGINT')
const onSigterm = () => interrupt('SIGTERM')
process.on('SIGINT', onSigint)
process.on('SIGTERM', onSigterm)

try {
  activeServer = await startServer('graceful-owner')
  const flow = await publishSlowProcess()

  const gracefulInvocationId = `graceful-${randomUUID()}`
  await submitInvocation(flow.code, flow.version, gracefulInvocationId)
  await waitForInvocation(gracefulInvocationId, (invocation) =>
    invocation.status === 'running' && invocation.currentAttemptCount === 1 ? invocation : undefined
  )
  await sleep(250)

  const gracefulSignalAt = Date.now()
  activeServer.process.kill('SIGTERM')
  const httpAdmissionClosed = await waitForHttpAdmissionToClose(2_500)
  const gracefulExit = await waitForExit(activeServer, 12_000)
  assertGracefulExit(gracefulExit)
  assert.match(activeServer.logs(), /Async invocation worker stopped/)
  activeServer = undefined

  activeServer = await startServer('graceful-verifier')
  const graceful = await waitForInvocation(
    gracefulInvocationId,
    (invocation) => (invocation.status === 'succeeded' ? invocation : undefined),
    10_000
  )
  assert.equal(graceful.currentAttemptCount, 1, 'graceful shutdown must not replay the invocation')
  assert.equal(graceful.totalAttemptCount, 1)
  assert.equal(graceful.redriveCount, 0)
  assert.equal(graceful.routing?.effectiveVersion, flow.version)
  assert.equal(graceful.response?.result?.version_marker, 'process-lifecycle')
  await assertSingleSuccessfulExecution(gracefulInvocationId, flow.version)
  const gracefulAttempts = await listInvocationAttempts(gracefulInvocationId)
  assert.deepEqual(gracefulAttempts.map(attemptIdentity), [
    {
      sequence: 1,
      redriveCount: 0,
      attemptNumber: 1,
      outcome: 'succeeded',
      disposition: 'succeeded',
    },
  ])
  assert.equal(gracefulAttempts[0].traceId, graceful.traceId)
  assert.equal(gracefulAttempts[0].errorCode, undefined)

  const crashInvocationId = `crash-${randomUUID()}`
  await submitInvocation(flow.code, flow.version, crashInvocationId)
  await waitForInvocation(crashInvocationId, (invocation) =>
    invocation.status === 'running' && invocation.currentAttemptCount === 1 ? invocation : undefined
  )
  await sleep(250)

  const crashSignalAt = Date.now()
  activeServer.process.kill('SIGKILL')
  const crashExit = await waitForExit(activeServer, 3_000)
  assert.equal(crashExit.signal, 'SIGKILL')
  activeServer = undefined

  activeServer = await startServer('crash-recovery')
  const recovered = await waitForInvocation(
    crashInvocationId,
    (invocation) => (invocation.status === 'succeeded' ? invocation : undefined),
    25_000
  )
  assert.equal(
    recovered.currentAttemptCount,
    2,
    'crashed ownership must recover as the second attempt'
  )
  assert.equal(recovered.totalAttemptCount, 2)
  assert.equal(recovered.redriveCount, 0)
  assert.equal(recovered.routing?.effectiveVersion, flow.version)
  assert.equal(recovered.response?.result?.version_marker, 'process-lifecycle')
  await assertSingleSuccessfulExecution(crashInvocationId, flow.version)
  const crashAttempts = await listInvocationAttempts(crashInvocationId)
  assert.deepEqual(crashAttempts.map(attemptIdentity), [
    {
      sequence: 1,
      redriveCount: 0,
      attemptNumber: 1,
      outcome: 'lease_expired',
      disposition: 'retry_scheduled',
    },
    {
      sequence: 2,
      redriveCount: 0,
      attemptNumber: 2,
      outcome: 'succeeded',
      disposition: 'succeeded',
    },
  ])
  assert.equal(crashAttempts[0].errorCode, 'LEASE_EXPIRED')
  assert.equal(crashAttempts[0].traceId, undefined)
  assert.equal(crashAttempts[1].traceId, recovered.traceId)

  const evidence = {
    schemaVersion: 2,
    artifact: {
      file: basename(jar),
      sha256: await sha256(jar),
    },
    generatedAt: new Date().toISOString(),
    experiment: {
      executionDurationMs: 4_000,
      leaseDurationMs: 2_000,
      derivedLeaseRenewalDelayMs: Math.floor(2_000 / 3),
    },
    gracefulShutdown: {
      invocationId: gracefulInvocationId,
      currentAttemptCount: graceful.currentAttemptCount,
      totalAttemptCount: graceful.totalAttemptCount,
      redriveCount: graceful.redriveCount,
      status: graceful.status,
      attemptLedger: gracefulAttempts.map(attemptEvidence),
      httpAdmissionClosed,
      shutdownDurationMs: gracefulExit.exitedAt - gracefulSignalAt,
    },
    crashRecovery: {
      invocationId: crashInvocationId,
      currentAttemptCount: recovered.currentAttemptCount,
      totalAttemptCount: recovered.totalAttemptCount,
      redriveCount: recovered.redriveCount,
      status: recovered.status,
      attemptLedger: crashAttempts.map(attemptEvidence),
      recoveryDurationMs: Date.now() - crashSignalAt,
    },
  }
  await stopServer(activeServer)
  activeServer = undefined
  if (interrupted) throw interrupted
  await mkdir(resultDirectory, { recursive: true })
  await writeFile(resultPath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
  process.stdout.write(`Verified async invocation process lifecycle: ${resultPath}\n`)
} catch (failure) {
  for (const server of serverHistory) {
    process.stderr.write(`\n--- ${server.label} log tail ---\n${server.logs()}\n`)
  }
  throw failure
} finally {
  try {
    for (const server of serverHistory) await stopServer(server)
  } finally {
    process.off('SIGINT', onSigint)
    process.off('SIGTERM', onSigterm)
  }
}

async function stopServer(server) {
  if (server.process.exitCode !== null || server.process.signalCode !== null || !server.process.pid)
    return
  server.process.kill('SIGTERM')
  try {
    await waitForExit(server, 12_000)
  } catch {
    server.process.kill('SIGKILL')
    await waitForExit(server, 3_000)
  }
}

async function assertPortAvailable() {
  const probe = createServer()
  await new Promise((resolve, reject) => {
    probe.once('error', reject)
    probe.listen({ host: '127.0.0.1', port: serverPort, exclusive: true }, resolve)
  })
  await new Promise((resolve, reject) =>
    probe.close((error) => (error ? reject(error) : resolve()))
  )
}

async function findBundledJar() {
  const target = join(repositoryRoot, 'compileflow-workbench-server', 'target')
  const entries = await readdir(target)
  const matches = entries.filter(
    (entry) =>
      entry.startsWith('compileflow-workbench-all-in-one-') &&
      entry.endsWith('.jar') &&
      !entry.endsWith('.jar.original')
  )
  assert.equal(
    matches.length,
    1,
    'expected exactly one bundled Workbench JAR; run verify:delivery --assembly-only first'
  )
  return join(target, matches[0])
}

async function startServer(label) {
  if (interrupted) throw interrupted
  await assertPortAvailable()
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java'
  const child = spawn(
    java,
    [
      '-jar',
      jar,
      '--spring.profiles.active=prod',
      '--spring.main.banner-mode=off',
      '--server.shutdown=graceful',
      '--server.address=127.0.0.1',
      `--server.port=${serverPort}`,
      '--spring.lifecycle.timeout-per-shutdown-phase=10s',
      '--compileflow.workbench.server.async-invocation.concurrency=1',
      '--compileflow.workbench.server.async-invocation.dispatch-interval=100ms',
      '--compileflow.workbench.server.async-invocation.lease-duration=2s',
      '--compileflow.workbench.server.async-invocation.lease-recovery-interval=250ms',
    ],
    {
      cwd: repositoryRoot,
      env: {
        ...process.env,
        COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE: 'API_KEY',
        COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY: apiKey,
        COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL:
          'workbench-process-lifecycle-test',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    }
  )
  let output = ''
  const append = (chunk) => {
    output = `${output}${chunk.toString()}`.slice(-200_000)
  }
  child.stdout.on('data', append)
  child.stderr.on('data', append)
  let spawnFailure
  const server = {
    label,
    process: child,
    logs: () => output,
    exit: new Promise((resolve) => {
      child.once('error', (error) => {
        spawnFailure = error
        resolve({ code: null, signal: null, error, exitedAt: Date.now() })
      })
      child.once('exit', (code, signal) => resolve({ code, signal, exitedAt: Date.now() }))
    }),
  }
  serverHistory.push(server)
  try {
    await waitUntil(
      async () => {
        if (spawnFailure) throw spawnFailure
        if (child.exitCode !== null || child.signalCode !== null) {
          throw new Error(`${label} exited before readiness\n${output}`)
        }
        try {
          const response = await fetch(`${serverUrl}/actuator/health`, {
            signal: AbortSignal.timeout(500),
          })
          return response.ok ? true : undefined
        } catch {
          return undefined
        }
      },
      60_000,
      `${label} did not become ready`,
      false
    )
    return server
  } catch (failure) {
    child.kill('SIGKILL')
    await waitForExit(server, 3_000)
    throw new Error(`${failure.message}\n${output}`, { cause: failure })
  }
}

async function publishSlowProcess() {
  const code = `workbench.process-lifecycle.${randomUUID()}`
  const draft = await requestJson('/api/processes', {
    method: 'POST',
    body: {
      code,
      name: 'Async Invocation Process Lifecycle Evidence',
      type: 'TBBPM',
      xml: slowProcessXml(code),
      tags: ['integration', 'process-lifecycle'],
    },
  })
  const published = await requestJson(`/api/processes/${code}/publish`, {
    method: 'POST',
    body: {
      changelog: 'Process lifecycle evidence',
      expectedRevision: draft.revision,
    },
    headers: { 'Idempotency-Key': randomUUID() },
  })
  const deployment = await requestJson('/api/deployments', {
    method: 'POST',
    body: {
      processCode: code,
      version: published.version,
      alias: 'production',
      expectedRouteRevision: 0,
      strategy: 'all_at_once',
      notes: 'Process lifecycle evidence',
    },
    headers: { 'Idempotency-Key': randomUUID() },
  })
  assert.equal(deployment.status, 'completed')
  return { code, version: published.version }
}

async function submitInvocation(processCode, version, invocationId) {
  const accepted = await requestJson(`/api/processes/${processCode}/async-invocations`, {
    method: 'POST',
    body: {
      invocationId,
      params: { delay_ms: 4_000 },
      routing: { version },
      maxAttempts: 2,
      retryDelayMs: 50,
    },
  })
  assert.equal(accepted.invocationId, invocationId)
  assert.equal(accepted.status, 'queued')
  assert.equal(accepted.currentAttemptCount, 0)
  assert.equal(accepted.totalAttemptCount, 0)
  assert.equal(accepted.redriveCount, 0)
}

async function waitForInvocation(invocationId, select, timeoutMs = 12_000) {
  return waitUntil(
    async () => select(await requestJson(`/api/async-invocations/${invocationId}`)),
    timeoutMs,
    `invocation ${invocationId} did not reach the expected state`
  )
}

async function assertSingleSuccessfulExecution(invocationId, version) {
  const logs = await requestJson(
    `/api/execution-logs?invocationId=${encodeURIComponent(invocationId)}`
  )
  assert.equal(logs.total, 1)
  assert.equal(logs.data.length, 1)
  assert.equal(logs.data[0].status, 'success')
  assert.equal(logs.data[0].effectiveVersion, version)
}

async function listInvocationAttempts(invocationId) {
  const page = await requestJson(
    `/api/async-invocations/${encodeURIComponent(invocationId)}/attempts?afterSequence=0&limit=100`
  )
  assert.equal(page.hasMore, false)
  assert.equal(page.nextAfterSequence, undefined)
  return page.data
}

function attemptIdentity(attempt) {
  return {
    sequence: attempt.sequence,
    redriveCount: attempt.redriveCount,
    attemptNumber: attempt.attemptNumber,
    outcome: attempt.outcome,
    disposition: attempt.disposition,
  }
}

function attemptEvidence(attempt) {
  return {
    attemptId: attempt.attemptId,
    sequence: attempt.sequence,
    redriveCount: attempt.redriveCount,
    attemptNumber: attempt.attemptNumber,
    workerId: attempt.workerId,
    outcome: attempt.outcome,
    disposition: attempt.disposition,
    startedAt: attempt.startedAt,
    finishedAt: attempt.finishedAt,
    nextAttemptAt: attempt.nextAttemptAt,
    traceId: attempt.traceId,
    errorCode: attempt.errorCode,
    durationMs: attempt.durationMs,
  }
}

async function requestJson(path, { method = 'GET', body, headers = {} } = {}) {
  const response = await fetch(`${serverUrl}${path}`, {
    method,
    headers: {
      'X-API-Key': apiKey,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(5_000),
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`${method} ${path} returned ${response.status}: ${text}`)
  }
  return text ? JSON.parse(text) : undefined
}

async function waitForHttpAdmissionToClose(timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${serverUrl}/api/async-invocations/health`, {
        headers: { 'X-API-Key': apiKey },
        signal: AbortSignal.timeout(250),
      })
      if (!response.ok) {
        return true
      }
    } catch {
      return true
    }
    await sleep(25)
  }
  throw new Error('HTTP admission remained open while the async worker was draining')
}

async function waitForExit(server, timeoutMs) {
  let timeout
  try {
    return await Promise.race([
      server.exit,
      new Promise((_, reject) => {
        timeout = setTimeout(
          () => reject(new Error(`${server.label} did not exit within ${timeoutMs}ms`)),
          timeoutMs
        )
      }),
    ])
  } finally {
    clearTimeout(timeout)
  }
}

function assertGracefulExit(exit) {
  assert(
    exit.code === 0 || exit.code === 143 || exit.signal === 'SIGTERM',
    `unexpected graceful exit: ${JSON.stringify(exit)}`
  )
}

async function waitUntil(operation, timeoutMs, message, retryFailures = true) {
  const deadline = Date.now() + timeoutMs
  let lastFailure
  while (Date.now() < deadline) {
    if (interrupted) throw interrupted
    try {
      const result = await operation()
      if (interrupted) throw interrupted
      if (result !== undefined && result !== false) {
        return result
      }
    } catch (failure) {
      if (!retryFailures) {
        throw failure
      }
      lastFailure = failure
    }
    await sleep(50)
  }
  throw new Error(message, { cause: lastFailure })
}

async function sha256(path) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(path)) {
    hash.update(chunk)
  }
  return hash.digest('hex')
}

function slowProcessXml(code) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="${code}" name="${code}">
  <var name="delay_ms" dataType="java.lang.Long" inOutType="param"/>
  <var name="version_marker" dataType="java.lang.String" inOutType="return"/>
  <start id="start" name="Start" g="50,50,32,32">
    <transition to="delay"/>
  </start>
  <autoTask id="delay" name="Delay" g="150,40,88,48">
    <action type="java" class="java.lang.Thread" method="sleep">
      <input source="delay_ms" target="delay_ms" dataType="java.lang.Long"/>
    </action>
    <transition to="marker"/>
  </autoTask>
  <scriptTask id="marker" name="Marker" g="280,40,88,48">
    <action type="script" language="qlexpress">
      <output target="version_marker" dataType="java.lang.String"/>
      <code><![CDATA["process-lifecycle"]]></code>
    </action>
    <transition to="end"/>
  </scriptTask>
  <end id="end" name="End" g="420,50,32,32"/>
</bpm>`
}

function requiredEnvironment(name) {
  const value = process.env[name]
  assert(value, `${name} is required`)
  return value
}

function sleep(durationMs) {
  return new Promise((resolve) => setTimeout(resolve, durationMs))
}
