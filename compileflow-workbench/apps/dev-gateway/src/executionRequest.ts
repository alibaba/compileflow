import type { MockPreviewRequest } from './mockEngine.js'

const PREVIEW_FIELDS = new Set(['code', 'modelType', 'xml', 'invocationId', 'params'])
const IDENTIFIER_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]*$/
const INVOCATION_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:@-]*$/
const MAX_CODE_LENGTH = 128
const MAX_INVOCATION_ID_LENGTH = 128

export type ExecutionRequestParseResult =
  | { ok: true; request: MockPreviewRequest }
  | { ok: false; message: string }

function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseCode(value: unknown): { ok: true; value: string } | { ok: false; message: string } {
  if (typeof value !== 'string' || !value.trim()) {
    return { ok: false, message: 'Invalid request: code must be a non-empty string' }
  }
  const normalized = value.trim()
  if (normalized.length > MAX_CODE_LENGTH || !IDENTIFIER_PATTERN.test(normalized)) {
    return {
      ok: false,
      message:
        'Invalid request: code must be at most 128 characters and contain only ASCII letters, digits, dots, underscores, or hyphens',
    }
  }
  return { ok: true, value: normalized }
}

function parseInvocationId(
  value: unknown
): { ok: true; value: string } | { ok: false; message: string } {
  if (typeof value !== 'string' || !value.trim()) {
    return {
      ok: false,
      message: 'Invalid request: invocationId must be a non-empty string when provided',
    }
  }
  if (value.length > MAX_INVOCATION_ID_LENGTH || !INVOCATION_ID_PATTERN.test(value)) {
    return {
      ok: false,
      message: 'Invalid request: invocationId contains unsupported characters or is too long',
    }
  }
  return { ok: true, value }
}

export function parseExecutionRequest(body: unknown): ExecutionRequestParseResult {
  if (!isObjectRecord(body)) {
    return { ok: false, message: 'Invalid request: JSON object body is required' }
  }

  for (const key of Object.keys(body)) {
    if (!PREVIEW_FIELDS.has(key)) {
      return {
        ok: false,
        message: `Invalid request: unsupported preview field: ${key}`,
      }
    }
  }

  const code = parseCode(body.code)
  if (!code.ok) return code
  if (body.modelType !== 'BPMN' && body.modelType !== 'TBBPM') {
    return {
      ok: false,
      message: 'Invalid request: modelType must be BPMN or TBBPM',
    }
  }
  if (typeof body.xml !== 'string' || !body.xml.trim()) {
    return {
      ok: false,
      message: 'Invalid request: xml must be a non-empty string',
    }
  }

  const request: MockPreviewRequest = {
    code: code.value,
    modelType: body.modelType,
    xml: body.xml,
  }
  if (body.invocationId !== undefined) {
    const invocationId = parseInvocationId(body.invocationId)
    if (!invocationId.ok) return invocationId
    request.invocationId = invocationId.value
  }
  if (body.params !== undefined) {
    if (!isObjectRecord(body.params)) {
      return {
        ok: false,
        message: 'Invalid request: params must be a JSON object when provided',
      }
    }
    request.params = { ...body.params }
  }
  return { ok: true, request }
}
