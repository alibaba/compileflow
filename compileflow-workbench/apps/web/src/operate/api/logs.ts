import { getMockLogs, mockLogs } from './mockLogData'

import apiClient from '@/shared/api/client'
import { isOperateMockMode } from '@/shared/config/buildConfig'
import type {
  ExecutionLog,
  LogExportParams,
  LogFilterParams,
  LogListResponse,
  LogPurgeParams,
  LogPurgeResult,
} from '@/shared/contracts'

export async function getLogs(params: LogFilterParams = {}): Promise<LogListResponse> {
  if (isOperateMockMode()) return Promise.resolve(getMockLogs(params))
  return apiClient.get<LogListResponse>('/api/execution-logs', { params })
}

export async function getLogById(id: string): Promise<ExecutionLog> {
  if (isOperateMockMode()) {
    const found = mockLogs.find((log) => log.id === id)
    if (found) return Promise.resolve(found)
    return Promise.reject(new Error(`Mock: log ${id} not found`))
  }
  return apiClient.get<ExecutionLog>(`/api/execution-logs/${id}`)
}

export async function exportLogs(params: LogExportParams): Promise<Blob> {
  if (isOperateMockMode()) {
    const { data } = getMockLogs({ ...params, page: 1, pageSize: mockLogs.length })
    const header =
      'id,processCode,invocationId,parentInvocationId,callDepth,traceId,' +
      'modelType,sourceDigest,status,startTime,endTime,duration,' +
      'namespace,requestedVersion,effectiveVersion,routingSource,' +
      'routeAlias,routeRevision,errorCode,errorMessage\n'
    const rows = data.map((log) =>
      [
        log.id,
        log.processCode,
        log.invocationId,
        log.parentInvocationId ?? '',
        log.callDepth,
        log.traceId,
        log.modelType,
        log.sourceDigest ?? '',
        log.status,
        log.startTime,
        log.endTime,
        log.duration,
        log.namespace,
        log.requestedVersion ?? '',
        log.effectiveVersion ?? '',
        log.routingSource ?? '',
        log.routeAlias ?? '',
        log.routeRevision ?? '',
        log.errorCode ?? '',
        log.errorMessage ?? '',
      ]
        .map(escapeCsvCell)
        .join(',')
    )
    return new Blob([header + rows.join('\n') + '\n'], { type: 'text/csv;charset=utf-8' })
  }
  return apiClient.postBlob('/api/execution-logs/export', params)
}

function escapeCsvCell(value: unknown): string {
  const text = String(value ?? '')
  const firstContent = text.trimStart().charAt(0)
  const protectedText = ['=', '+', '-', '@'].includes(firstContent) ? `'${text}` : text
  if (
    protectedText.includes(',') ||
    protectedText.includes('"') ||
    protectedText.includes('\n') ||
    protectedText.includes('\r')
  ) {
    return `"${protectedText.replace(/"/g, '""')}"`
  }
  return protectedText
}

export async function purgeLogs(params: LogPurgeParams): Promise<LogPurgeResult> {
  if (isOperateMockMode()) {
    return Promise.resolve({
      deletedCount: 0,
      hasMore: false,
      purgedAt: new Date().toISOString(),
    })
  }
  return apiClient.post<LogPurgeResult>('/api/execution-logs/purge', params)
}
