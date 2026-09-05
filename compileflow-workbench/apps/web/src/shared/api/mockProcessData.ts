import type {
  ProcessCreateRequest,
  ProcessDefinition,
  ProcessListParams,
  ProcessListResponse,
  ProcessSummary,
  ProcessUpdateRequest,
  ProcessVersion,
  ProcessVersionListParams,
  ProcessVersionListResponse,
} from '@/shared/contracts'
import { DEFAULT_BPMN_WITH_EVENTS_XML } from '@/shared/processes/bpmnTemplates'
import { DEFAULT_TBBPM_WITH_NODES_XML } from '@/shared/processes/tbbpmTemplates'

function resolveMockProcessXml(flow: ProcessDefinition): string {
  if (flow.xml && !flow.xml.endsWith('...')) {
    return flow.xml
  }
  return flow.type === 'TBBPM' ? DEFAULT_TBBPM_WITH_NODES_XML : DEFAULT_BPMN_WITH_EVENTS_XML
}

const initialMockProcesses: Array<Omit<ProcessDefinition, 'revision'>> = [
  {
    code: 'order-approval-bpmn',
    name: '订单审批流程',
    type: 'BPMN',
    description: '基于BPMN的订单审批流程，支持多级审批和条件判断',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-02-08T14:30:00Z',
    createdBy: 'admin',
  },
  {
    code: 'payment-process-tbbpm',
    name: '支付处理流程',
    type: 'TBBPM',
    description: '支付业务流程，包含支付验证、扣款、回调处理',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-20T09:00:00Z',
    updatedAt: '2026-02-09T16:45:00Z',
    createdBy: 'kangzhiqiang',
  },
  {
    code: 'user-registration-bpmn',
    name: '用户注册流程',
    type: 'BPMN',
    description: '新用户注册流程，包含邮箱验证和欢迎通知',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-10T15:20:00Z',
    updatedAt: '2026-02-01T11:00:00Z',
    createdBy: 'yusu',
  },
  {
    code: 'refund-workflow-tbbpm',
    name: '退款工作流',
    type: 'TBBPM',
    description: '订单退款流程，支持部分退款和全额退款',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-25T13:40:00Z',
    updatedAt: '2026-02-05T10:15:00Z',
    createdBy: 'admin',
  },
  {
    code: 'inventory-check-bpmn',
    name: '库存检查流程',
    type: 'BPMN',
    description: '商品库存检查和预留流程',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-08T08:00:00Z',
    updatedAt: '2026-02-07T14:00:00Z',
    createdBy: 'kangzhiqiang',
  },
  {
    code: 'notification-flow-tbbpm',
    name: '消息通知流程',
    type: 'TBBPM',
    description: '多渠道消息推送流程（邮件/短信/推送）',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-18T11:30:00Z',
    updatedAt: '2026-02-09T09:20:00Z',
    createdBy: 'yusu',
  },
  {
    code: 'product-launch-bpmn',
    name: '商品上架流程',
    type: 'BPMN',
    description: '新商品上架审核流程',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-02-08T16:00:00Z',
    updatedAt: '2026-02-09T10:30:00Z',
    createdBy: 'admin',
  },
  {
    code: 'coupon-distribute-tbbpm',
    name: '优惠券发放流程',
    type: 'TBBPM',
    description: '营销活动优惠券批量发放流程',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-12T14:00:00Z',
    updatedAt: '2026-02-06T15:45:00Z',
    createdBy: 'kangzhiqiang',
  },
  {
    code: 'order-cancel-bpmn',
    name: '订单取消流程',
    type: 'BPMN',
    description: '订单取消和退款处理流程',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2026-01-22T09:30:00Z',
    updatedAt: '2026-02-04T13:20:00Z',
    createdBy: 'yusu',
  },
  {
    code: 'customer-feedback-tbbpm',
    name: '客户反馈处理流程',
    type: 'TBBPM',
    description: '客户意见和建议收集处理流程',
    xml: '<?xml version="1.0" encoding="UTF-8"?>...',
    tags: [],
    createdAt: '2025-12-20T10:00:00Z',
    updatedAt: '2026-01-15T16:00:00Z',
    createdBy: 'admin',
  },
]

const mockProcesses: ProcessDefinition[] = initialMockProcesses.map((flow) => ({
  ...flow,
  revision: 0,
}))

const INITIAL_PUBLISHED_VERSIONS: Readonly<Record<string, string>> = {
  'order-approval-bpmn': '1.2.0',
  'payment-process-tbbpm': '2.0.1',
  'user-registration-bpmn': '1.0.5',
  'refund-workflow-tbbpm': '1.1.0',
  'inventory-check-bpmn': '1.3.2',
  'notification-flow-tbbpm': '2.1.0',
  'coupon-distribute-tbbpm': '1.2.1',
  'order-cancel-bpmn': '1.1.3',
  'customer-feedback-tbbpm': '1.0.2',
}

const mockProcessVersions = new Map<string, ProcessVersion[]>(
  mockProcesses.flatMap((flow) => {
    const version = INITIAL_PUBLISHED_VERSIONS[flow.code]
    if (!version) return []
    return [
      [
        flow.code,
        [
          {
            processCode: flow.code,
            version,
            modelType: flow.type,
            changelog: 'Initial mock publication',
            createdAt: flow.updatedAt,
            publishedBy: flow.createdBy ?? 'mock-user',
          },
        ],
      ],
    ]
  })
)
const mockPublicationIntents = new Map<string, ProcessVersion>()

function toProcessSummary(flow: ProcessDefinition): ProcessSummary {
  return {
    code: flow.code,
    name: flow.name,
    type: flow.type,
    createdAt: flow.createdAt,
    updatedAt: flow.updatedAt,
    revision: flow.revision,
    description: flow.description,
    tags: flow.tags,
    createdBy: flow.createdBy,
  }
}

export function getMockProcesses(params: ProcessListParams = {}): ProcessListResponse {
  const { page = 1, pageSize = 10, type, keyword } = params

  let filtered = mockProcesses

  if (type) {
    filtered = filtered.filter((f) => f.type === type)
  }

  if (keyword) {
    const lowerKeyword = keyword.toLowerCase()
    filtered = filtered.filter(
      (f) =>
        f.name.toLowerCase().includes(lowerKeyword) ||
        f.code.toLowerCase().includes(lowerKeyword) ||
        (f.description && f.description.toLowerCase().includes(lowerKeyword))
    )
  }

  // Sort by updatedAt descending (most recently updated first)
  filtered = [...filtered].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  )

  const start = (page - 1) * pageSize
  const end = start + pageSize

  return {
    data: filtered.slice(start, end).map(toProcessSummary),
    total: filtered.length,
    page,
    pageSize,
  }
}

export function getMockProcessByCode(code: string): ProcessDefinition {
  const flow = mockProcesses.find((f) => f.code === code)
  if (!flow) throw new Error(`Process not found: ${code}`)
  return { ...flow, xml: resolveMockProcessXml(flow) }
}

export function createMockProcess(flow: ProcessCreateRequest): ProcessDefinition {
  const now = new Date().toISOString()
  const newProcess: ProcessDefinition = {
    ...flow,
    xml: flow.xml ?? '',
    createdBy: 'mock-user',
    createdAt: now,
    updatedAt: now,
    revision: 0,
    tags: flow.tags ?? [],
  }
  mockProcesses.push(newProcess)
  return newProcess
}

export function updateMockProcess(code: string, updates: ProcessUpdateRequest): ProcessDefinition {
  const current = getMockProcessByCode(code)
  requireRevision(current, updates.expectedRevision)
  return applyMockProcessUpdate(code, updates)
}

function applyMockProcessUpdate(code: string, updates: ProcessUpdateRequest): ProcessDefinition {
  const idx = mockProcesses.findIndex((f) => f.code === code)
  if (idx === -1) throw new Error(`Process not found: ${code}`)
  mockProcesses[idx] = {
    ...mockProcesses[idx],
    name: updates.name,
    xml: updates.xml,
    description: updates.description,
    tags: updates.tags,
    revision: mockProcesses[idx].revision + 1,
    updatedAt: new Date().toISOString(),
  }
  return mockProcesses[idx]
}

export function deleteMockProcess(code: string, expectedRevision: number): void {
  const idx = mockProcesses.findIndex((f) => f.code === code)
  if (idx === -1) return
  requireRevision(mockProcesses[idx], expectedRevision)
  mockProcesses.splice(idx, 1)
}

export function publishMockProcess(
  code: string,
  expectedRevision: number,
  idempotencyKey: string,
  changelog?: string
): ProcessVersion {
  const flow = getMockProcessByCode(code)
  requireRevision(flow, expectedRevision)
  const normalizedKey = idempotencyKey.trim()
  if (!normalizedKey || normalizedKey.length > 128) {
    throw new Error('Idempotency key must contain between 1 and 128 characters')
  }
  const intentKey = `${code}\u0000${normalizedKey}`
  const existing = mockPublicationIntents.get(intentKey)
  if (existing) {
    return existing
  }
  const version: ProcessVersion = {
    processCode: code,
    version: `r-${crypto.randomUUID().replace(/-/g, '')}`,
    modelType: flow.type,
    changelog: changelog ?? '',
    createdAt: new Date().toISOString(),
    publishedBy: 'mock-user',
  }
  mockProcessVersions.set(code, [version, ...(mockProcessVersions.get(code) ?? [])])
  mockPublicationIntents.set(intentKey, version)
  return version
}

export function getMockProcessVersions(
  code: string,
  params: ProcessVersionListParams = {}
): ProcessVersionListResponse {
  const { cursor, limit = 100, versionPrefix } = params
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('limit must be between 1 and 100')
  }
  const normalizedPrefix = versionPrefix?.trim()
  const filtered = (mockProcessVersions.get(code) ?? []).filter(
    (version) => !normalizedPrefix || version.version.startsWith(normalizedPrefix)
  )
  const cursorIndex = cursor ? filtered.findIndex((version) => version.version === cursor) : -1
  if (cursor && cursorIndex < 0) throw new Error('Invalid process version cursor')
  const start = cursorIndex + 1
  const data = filtered.slice(start, start + limit).map((version) => ({ ...version }))
  const hasMore = start + data.length < filtered.length
  return {
    data,
    nextCursor: hasMore ? (data[data.length - 1]?.version ?? null) : null,
    hasMore,
  }
}

export function duplicateMockProcess(
  code: string,
  newCode: string,
  newName: string
): ProcessDefinition {
  const source = getMockProcessByCode(code)
  return createMockProcess({
    code: newCode,
    name: newName,
    type: source.type,
    xml: source.xml,
    description: source.description,
    tags: source.tags,
  })
}

function requireRevision(flow: ProcessDefinition, expectedRevision: number): void {
  if (flow.revision !== expectedRevision) {
    throw new Error(
      `Process revision mismatch: code=${flow.code}, expected=${expectedRevision}, current=${flow.revision}`
    )
  }
}
