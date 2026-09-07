import {
  createMockProcess,
  deleteMockProcess,
  duplicateMockProcess,
  getMockProcessByCode,
  getMockProcesses,
  getMockProcessVersions,
  publishMockProcess,
  updateMockProcess,
} from './mockProcessData'

import apiClient from '@/shared/api/client'
import { isOperateMockMode } from '@/shared/config/buildConfig'
import type {
  AsyncInvocationAttempt,
  AsyncInvocationAttemptListParams,
  AsyncInvocationAttemptListResponse,
  AsyncInvocationDeadLetterRequeueRequest,
  AsyncInvocationDeadLetterRequeueResponse,
  AsyncInvocationHealth,
  AsyncInvocationListParams,
  AsyncInvocationListResponse,
  AsyncInvocationRequest,
  AsyncInvocationResponse,
  ExecutionRoutingResult,
  ProcessCreateRequest,
  ProcessDefinition,
  ProcessListParams,
  ProcessListResponse,
  ProcessModelType,
  ProcessUpdateRequest,
  ProcessVersion,
  ProcessVersionListParams,
  ProcessVersionListResponse,
} from '@/shared/contracts'

const mockAsyncInvocations = new Map<string, AsyncInvocationResponse>()
const mockAsyncInvocationAttempts = new Map<string, AsyncInvocationAttempt[]>()
let mockAsyncSeeded = false

function ensureMockAsyncSeed(): void {
  if (mockAsyncSeeded) return
  mockAsyncSeeded = true
  const createdAt = '2026-08-02T00:00:00.000Z'
  const startedAt = '2026-08-02T00:00:10.000Z'
  const deadLetterAt = '2026-08-02T00:01:00.000Z'
  const deadLetter: AsyncInvocationResponse = {
    invocationId: 'order-async-42',
    processCode: 'order-approval-bpmn',
    status: 'dead_letter',
    currentAttemptCount: 1,
    totalAttemptCount: 1,
    redriveCount: 0,
    maxAttempts: 1,
    retryDelayMs: 1000,
    createdAt,
    updatedAt: deadLetterAt,
    startedAt,
    completedAt: deadLetterAt,
    errorCode: 'LEASE_EXPIRED',
    error: 'Async invocation lease expired',
    routing: {
      namespace: 'default',
      requestedAlias: 'PROD',
      effectiveVersion: '1.2.0',
    },
  }
  mockAsyncInvocations.set(deadLetter.invocationId, deadLetter)
  mockAsyncInvocationAttempts.set(deadLetter.invocationId, [
    {
      attemptId: 'att-order-async-42-1',
      invocationId: deadLetter.invocationId,
      sequence: 1,
      redriveCount: 0,
      attemptNumber: 1,
      workerId: 'mock-worker',
      outcome: 'failed',
      disposition: 'dead_lettered',
      startedAt,
      finishedAt: deadLetterAt,
      error: deadLetter.error,
      errorCode: deadLetter.errorCode,
    },
  ])
}

export async function getProcesses(params: ProcessListParams = {}): Promise<ProcessListResponse> {
  if (isOperateMockMode()) return Promise.resolve(getMockProcesses(params))
  return apiClient.get<ProcessListResponse>('/api/processes', { params })
}

export async function getProcessByCode(
  code: string,
  signal?: AbortSignal
): Promise<ProcessDefinition> {
  if (isOperateMockMode()) return Promise.resolve(getMockProcessByCode(code))
  return apiClient.get<ProcessDefinition>(`/api/processes/${code}`, { signal })
}

export async function createProcess(process: ProcessCreateRequest): Promise<ProcessDefinition> {
  if (isOperateMockMode()) return Promise.resolve(createMockProcess(process))
  return apiClient.post<ProcessDefinition>('/api/processes', process)
}

export async function updateProcess(
  code: string,
  process: ProcessUpdateRequest
): Promise<ProcessDefinition> {
  if (isOperateMockMode()) return Promise.resolve(updateMockProcess(code, process))
  return apiClient.put<ProcessDefinition>(`/api/processes/${code}`, process)
}

export async function deleteProcess(code: string, expectedRevision: number): Promise<void> {
  if (isOperateMockMode()) {
    deleteMockProcess(code, expectedRevision)
    return
  }
  await apiClient.delete(`/api/processes/${code}`, { params: { expectedRevision } })
}

export async function getProcessVersions(
  code: string,
  params: ProcessVersionListParams = {}
): Promise<ProcessVersionListResponse> {
  if (isOperateMockMode()) {
    return Promise.resolve(getMockProcessVersions(code, params))
  }
  return apiClient.get<ProcessVersionListResponse>(`/api/processes/${code}/versions`, { params })
}

export async function publishProcess(
  code: string,
  expectedRevision: number,
  idempotencyKey: string,
  changelog?: string
): Promise<ProcessVersion> {
  if (isOperateMockMode()) {
    return Promise.resolve(publishMockProcess(code, expectedRevision, idempotencyKey, changelog))
  }
  return apiClient.post<ProcessVersion>(
    `/api/processes/${code}/publish`,
    { changelog, expectedRevision },
    { headers: { 'Idempotency-Key': idempotencyKey } }
  )
}

export async function submitAsyncInvocation(
  code: string,
  request: AsyncInvocationRequest
): Promise<AsyncInvocationResponse> {
  if (isOperateMockMode()) return Promise.resolve(createMockAsyncInvocation(code, request))
  return apiClient.post<AsyncInvocationResponse>(
    `/api/processes/${code}/async-invocations`,
    request
  )
}

export async function getAsyncInvocation(invocationId: string): Promise<AsyncInvocationResponse> {
  if (isOperateMockMode()) {
    ensureMockAsyncSeed()
    const invocation = mockAsyncInvocations.get(invocationId)
    if (!invocation) throw new Error(`Async invocation not found: ${invocationId}`)
    return Promise.resolve(invocation)
  }
  return apiClient.get<AsyncInvocationResponse>(`/api/async-invocations/${invocationId}`)
}

export async function listAsyncInvocationAttempts(
  invocationId: string,
  params: AsyncInvocationAttemptListParams = {}
): Promise<AsyncInvocationAttemptListResponse> {
  if (isOperateMockMode()) {
    return Promise.resolve(listMockAsyncInvocationAttempts(invocationId, params))
  }
  return apiClient.get<AsyncInvocationAttemptListResponse>(
    `/api/async-invocations/${invocationId}/attempts`,
    { params }
  )
}

export async function listAsyncInvocations(
  params: AsyncInvocationListParams = {}
): Promise<AsyncInvocationListResponse> {
  if (isOperateMockMode()) return Promise.resolve(listMockAsyncInvocations(params))
  return apiClient.get<AsyncInvocationListResponse>('/api/async-invocations', { params })
}

export async function requeueAsyncInvocation(
  invocationId: string
): Promise<AsyncInvocationResponse> {
  if (isOperateMockMode()) {
    ensureMockAsyncSeed()
    const invocation = mockAsyncInvocations.get(invocationId)
    if (!invocation) throw new Error(`Async invocation not found: ${invocationId}`)
    if (invocation.status !== 'dead_letter') {
      throw new Error(`Only dead-letter invocations can be requeued: ${invocationId}`)
    }
    const now = new Date().toISOString()
    const accepted: AsyncInvocationResponse = {
      ...invocation,
      status: 'queued',
      currentAttemptCount: 0,
      redriveCount: invocation.redriveCount + 1,
      error: undefined,
      errorCode: undefined,
      updatedAt: now,
      nextAttemptAt: now,
      startedAt: undefined,
      completedAt: undefined,
      traceId: undefined,
      leaseUntil: undefined,
      durationMs: undefined,
      response: undefined,
    }
    const completed: AsyncInvocationResponse = {
      ...accepted,
      status: 'succeeded',
      currentAttemptCount: 1,
      totalAttemptCount: accepted.totalAttemptCount + 1,
      nextAttemptAt: undefined,
      startedAt: now,
      completedAt: now,
      traceId: invocation.traceId ?? `trace-${invocationId}`,
      durationMs: invocation.durationMs ?? 0,
      response: {
        success: true,
        message: 'Mock async invocation requeued and completed',
        traceId: invocation.traceId ?? `trace-${invocationId}`,
        invocationId,
        processCode: invocation.processCode,
        durationMs: invocation.durationMs ?? 0,
        routing: invocation.routing ?? { namespace: 'default' },
        result: { requeued: true },
      },
    }
    appendMockSucceededAttempt(completed, now)
    mockAsyncInvocations.set(invocationId, completed)
    return Promise.resolve(accepted)
  }
  return apiClient.post<AsyncInvocationResponse>(`/api/async-invocations/${invocationId}/requeue`)
}

export async function getAsyncInvocationHealth(): Promise<AsyncInvocationHealth> {
  if (isOperateMockMode()) return Promise.resolve(mockAsyncInvocationHealth())
  return apiClient.get<AsyncInvocationHealth>('/api/async-invocations/health')
}

export async function requeueAsyncInvocationDeadLetters(
  request: AsyncInvocationDeadLetterRequeueRequest = {}
): Promise<AsyncInvocationDeadLetterRequeueResponse> {
  if (isOperateMockMode()) return Promise.resolve(mockRequeueAsyncInvocationDeadLetters(request))
  return apiClient.post<AsyncInvocationDeadLetterRequeueResponse>(
    '/api/async-invocations/dead-letters/requeue',
    request
  )
}

export async function duplicateProcess(
  code: string,
  newCode: string,
  newName: string
): Promise<ProcessDefinition> {
  if (isOperateMockMode()) return Promise.resolve(duplicateMockProcess(code, newCode, newName))
  return apiClient.post<ProcessDefinition>(`/api/processes/${code}/duplicate`, { newCode, newName })
}

export async function exportProcessXml(code: string): Promise<Blob> {
  if (isOperateMockMode()) {
    const flow = getMockProcessByCode(code)
    const xml =
      flow.xml && !flow.xml.endsWith('...')
        ? flow.xml
        : '<?xml version="1.0" encoding="UTF-8"?><flow/>'
    return new Blob([xml], { type: 'application/xml' })
  }
  return apiClient.getBlob(`/api/processes/${code}/export`)
}

export async function importProcessXml(file: File): Promise<ProcessDefinition> {
  if (isOperateMockMode()) {
    const xml = await file.text()
    const imported = parseImportedProcessXml(xml)
    return createMockProcess({
      code: imported.code,
      name: imported.name,
      type: imported.type,
      xml,
      description: 'Imported from XML',
    })
  }
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<ProcessDefinition>('/api/processes/import', formData)
}

function parseImportedProcessXml(xml: string): {
  code: string
  name: string
  type: ProcessModelType
} {
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  if (document.querySelector('parsererror')) {
    throw new Error('Imported file is not well-formed XML')
  }
  const root = document.documentElement
  if (root.localName === 'bpm') {
    const code = requiredXmlAttribute(root, 'code')
    return {
      code,
      name: root.getAttribute('name')?.trim() || code,
      type: 'TBBPM',
    }
  }
  const bpmnNamespace = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
  if (root.localName === 'definitions' && root.namespaceURI === bpmnNamespace) {
    const processes = Array.from(root.children).filter(
      (element) => element.localName === 'process' && element.namespaceURI === bpmnNamespace
    )
    if (processes.length !== 1) {
      throw new Error('Imported BPMN must contain exactly one process')
    }
    const code = requiredXmlAttribute(processes[0], 'id')
    return {
      code,
      name: processes[0].getAttribute('name')?.trim() || code,
      type: 'BPMN',
    }
  }
  throw new Error(
    'Imported XML must be a TBBPM <bpm> document or a BPMN 2.0 <definitions> document'
  )
}

function requiredXmlAttribute(element: Element, name: string): string {
  const value = element.getAttribute(name)?.trim()
  if (!value) {
    throw new Error(`Imported ${element.localName} element requires a non-blank ${name} attribute`)
  }
  return value
}

function listMockAsyncInvocations(params: AsyncInvocationListParams): AsyncInvocationListResponse {
  ensureMockAsyncSeed()
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? 20
  if (!Number.isInteger(page) || page < 1) {
    throw new Error('page must be greater than or equal to 1')
  }
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100) {
    throw new Error('pageSize must be between 1 and 100')
  }
  const filtered = Array.from(mockAsyncInvocations.values()).filter((invocation) => {
    if (params.status && invocation.status !== params.status) return false
    return !(params.processCode && invocation.processCode !== params.processCode)
  })
  const start = (page - 1) * pageSize
  return {
    data: filtered.slice(start, start + pageSize),
    total: filtered.length,
    page,
    pageSize,
  }
}

function listMockAsyncInvocationAttempts(
  invocationId: string,
  params: AsyncInvocationAttemptListParams
): AsyncInvocationAttemptListResponse {
  ensureMockAsyncSeed()
  if (!mockAsyncInvocations.has(invocationId)) {
    throw new Error(`Async invocation not found: ${invocationId}`)
  }
  const afterSequence = params.afterSequence ?? 0
  const limit = params.limit ?? 20
  if (!Number.isSafeInteger(afterSequence) || afterSequence < 0) {
    throw new Error('afterSequence must be a non-negative safe integer')
  }
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('limit must be between 1 and 100')
  }
  const candidates = (mockAsyncInvocationAttempts.get(invocationId) ?? []).filter(
    (attempt) => attempt.sequence > afterSequence
  )
  const data = candidates.slice(0, limit)
  const hasMore = candidates.length > limit
  return {
    data,
    hasMore,
    nextAfterSequence: hasMore ? data[data.length - 1]?.sequence : undefined,
  }
}

function mockAsyncInvocationHealth(): AsyncInvocationHealth {
  ensureMockAsyncSeed()
  const invocations = Array.from(mockAsyncInvocations.values())
  const now = Date.now()
  const queuedInvocations = invocations.filter((invocation) => invocation.status === 'queued')
  const readyInvocations = queuedInvocations.filter(
    (invocation) => !invocation.nextAttemptAt || Date.parse(invocation.nextAttemptAt) <= now
  )
  const oldestReadyAt = readyInvocations.reduce(
    (oldest, invocation) =>
      Math.min(oldest, Date.parse(invocation.nextAttemptAt ?? invocation.createdAt)),
    now
  )
  const queuedCount = queuedInvocations.length
  const runningCount = invocations.filter((invocation) => invocation.status === 'running').length
  const succeededCount = invocations.filter(
    (invocation) => invocation.status === 'succeeded'
  ).length
  const deadLetterCount = invocations.filter(
    (invocation) => invocation.status === 'dead_letter'
  ).length
  const expiredRunningCount = 0
  return {
    status: deadLetterCount > 0 || expiredRunningCount > 0 ? 'degraded' : 'healthy',
    queuedCount,
    readyQueuedCount: readyInvocations.length,
    oldestReadyAgeMs: readyInvocations.length === 0 ? 0 : Math.max(0, now - oldestReadyAt),
    delayedQueuedCount: queuedCount - readyInvocations.length,
    runningCount,
    succeededCount,
    deadLetterCount,
    expiredRunningCount,
    localRunningCount: runningCount,
    dispatchedCount: 0,
    workerId: 'mock-worker',
    leaseDurationMs: 30000,
    concurrency: 4,
    checkedAt: new Date().toISOString(),
  }
}

function mockRequeueAsyncInvocationDeadLetters(
  request: AsyncInvocationDeadLetterRequeueRequest
): AsyncInvocationDeadLetterRequeueResponse {
  const limit = request.limit ?? 100
  if (!Number.isInteger(limit) || limit < 1 || limit > 500) {
    throw new Error('limit must be between 1 and 500')
  }
  const selected = Array.from(mockAsyncInvocations.values())
    .filter((invocation) => invocation.status === 'dead_letter')
    .filter((invocation) => !request.processCode || invocation.processCode === request.processCode)
    .slice(0, limit)
  const now = new Date().toISOString()
  const invocationIds: string[] = []
  for (const invocation of selected) {
    invocationIds.push(invocation.invocationId)
    mockAsyncInvocations.set(invocation.invocationId, {
      ...invocation,
      status: 'queued',
      currentAttemptCount: 0,
      redriveCount: invocation.redriveCount + 1,
      error: undefined,
      errorCode: undefined,
      traceId: undefined,
      leaseUntil: undefined,
      nextAttemptAt: now,
      durationMs: undefined,
      startedAt: undefined,
      completedAt: undefined,
      updatedAt: now,
      response: undefined,
    })
  }
  return {
    requeued: selected.length,
    requeuedAt: now,
    processCode: request.processCode,
    limit,
    invocationIds,
    health: mockAsyncInvocationHealth(),
  }
}

function createMockAsyncInvocation(
  code: string,
  request: AsyncInvocationRequest
): AsyncInvocationResponse {
  const now = new Date().toISOString()
  const invocationId =
    request.invocationId || `inv-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
  const routing: ExecutionRoutingResult = {
    namespace: 'default',
    requestedVersion: request.routing.version,
    requestedAlias: request.routing.alias,
    effectiveVersion: request.routing.version,
    alias: request.routing.alias,
  }
  const maxAttempts = request.maxAttempts ?? 1
  const retryDelayMs = request.retryDelayMs ?? 1000
  const accepted: AsyncInvocationResponse = {
    invocationId,
    processCode: code,
    status: 'queued',
    currentAttemptCount: 0,
    totalAttemptCount: 0,
    redriveCount: 0,
    maxAttempts,
    retryDelayMs,
    createdAt: now,
    updatedAt: now,
    nextAttemptAt: now,
    routing,
  }
  const completed: AsyncInvocationResponse = {
    ...accepted,
    status: 'succeeded',
    currentAttemptCount: 1,
    totalAttemptCount: 1,
    nextAttemptAt: undefined,
    startedAt: now,
    completedAt: now,
    traceId: `trace-${invocationId}`,
    durationMs: 12,
    routing,
    response: {
      success: true,
      message: 'Mock async invocation completed',
      traceId: `trace-${invocationId}`,
      invocationId,
      processCode: code,
      durationMs: 12,
      routing,
      result: { async: true, params: request.params ?? {} },
    },
  }
  appendMockSucceededAttempt(completed, now)
  mockAsyncInvocations.set(invocationId, completed)
  return accepted
}

function appendMockSucceededAttempt(invocation: AsyncInvocationResponse, timestamp: string): void {
  const attempt: AsyncInvocationAttempt = {
    attemptId: `att-${invocation.invocationId}-${invocation.totalAttemptCount}`,
    invocationId: invocation.invocationId,
    sequence: invocation.totalAttemptCount,
    redriveCount: invocation.redriveCount,
    attemptNumber: invocation.currentAttemptCount,
    workerId: 'mock-worker',
    outcome: 'succeeded',
    disposition: 'succeeded',
    startedAt: invocation.startedAt ?? timestamp,
    finishedAt: invocation.completedAt ?? timestamp,
    traceId: invocation.traceId,
    durationMs: invocation.durationMs,
  }
  const attempts = mockAsyncInvocationAttempts.get(invocation.invocationId) ?? []
  mockAsyncInvocationAttempts.set(invocation.invocationId, [...attempts, attempt])
}
