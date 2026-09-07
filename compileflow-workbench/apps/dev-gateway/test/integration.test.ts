import { type ChildProcess, spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'

import { afterAll, beforeAll, describe, expect, it } from 'vitest'

const DEFAULT_TEST_PORT = '3101'
const BASE_URL = process.env.DEV_GATEWAY_TEST_URL ?? `http://localhost:${DEFAULT_TEST_PORT}`
const TEST_PORT = new URL(BASE_URL).port || DEFAULT_TEST_PORT
const DEV_GATEWAY_READY_TIMEOUT = Number.parseInt(
  process.env.DEV_GATEWAY_TEST_TIMEOUT ?? '20000',
  10
)
const require = createRequire(import.meta.url)
const TSX_CLI = join(dirname(require.resolve('tsx/package.json')), 'dist/cli.mjs')
const VALID_PREVIEW = {
  code: 'bpm.hello-world',
  modelType: 'BPMN',
  xml: '<definitions/>',
  params: { userId: 'u-1' },
}

describe('CompileFlow Workbench Development Gateway integration', () => {
  let serverProcess: ChildProcess

  beforeAll(async () => {
    serverProcess = spawn(process.execPath, [TSX_CLI, 'src/server.ts'], {
      cwd: process.cwd(),
      stdio: 'pipe',
      env: {
        ...process.env,
        NODE_ENV: 'test',
        COMPILEFLOW_DEV_GATEWAY_PORT: TEST_PORT,
        COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES: '1024',
      },
    })

    serverProcess.stderr?.on('data', (chunk: Buffer) => {
      console.error(`[dev-gateway-test] ${chunk.toString()}`)
    })

    await waitForServer(BASE_URL, DEV_GATEWAY_READY_TIMEOUT)
  })

  afterAll(async () => {
    if (!serverProcess) return
    await new Promise<void>((resolve) => {
      const onExit = () => resolve()
      serverProcess.once('exit', onExit)
      serverProcess.once('close', onExit)
      serverProcess.kill('SIGTERM')
      setTimeout(resolve, 3000)
    })
  })

  it('exposes health and product status', async () => {
    const health = await fetch(`${BASE_URL}/health`)
    const status = await fetch(`${BASE_URL}/api/status`)

    expect(health.status).toBe(200)
    expect(await responseJsonObject(health)).toHaveProperty('status', 'ok')
    expect(status.status).toBe(200)
    expect(await responseJsonObject(status)).toHaveProperty('engineAvailable', true)
  })

  it('executes the Workbench Server preview contract', async () => {
    const response = await postPreview(VALID_PREVIEW)
    const data = await responseJsonObject(response)

    expect(response.status).toBe(200)
    expect(data).toEqual({
      success: true,
      message: expect.any(String),
      traceId: expect.any(String),
      invocationId: expect.any(String),
      processCode: VALID_PREVIEW.code,
      durationMs: expect.any(Number),
      routing: { namespace: 'default' },
      result: { output: 'Mock preview result', inputParams: VALID_PREVIEW.params },
    })
  })

  it('rejects invalid preview bodies', async () => {
    const response = await postPreview({
      ...VALID_PREVIEW,
      modelType: 'UNKNOWN',
    })
    const data = await responseJsonObject(response)

    expect(response.status).toBe(400)
    expect(data.code).toBe('INVALID_ARGUMENT')
    expect(data.detail).toContain('modelType')
  })

  it('rejects malformed JSON using the shared problem contract', async () => {
    const response = await fetch(`${BASE_URL}/api/executions/preview`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"code":',
    })
    const data = await responseJsonObject(response)

    expect(response.status).toBe(400)
    expect(data).toMatchObject({
      status: 400,
      instance: '/api/executions/preview',
      code: 'MALFORMED_REQUEST_BODY',
    })
    expect(response.headers.get('content-type')).toContain('application/problem+json')
  })

  it('rejects bodies above the configured development limit', async () => {
    const response = await postPreview({
      ...VALID_PREVIEW,
      xml: `<definitions>${'x'.repeat(2048)}</definitions>`,
    })
    const data = await responseJsonObject(response)

    expect(response.status).toBe(413)
    expect(data).toMatchObject({
      status: 413,
      instance: '/api/executions/preview',
      code: 'REQUEST_TOO_LARGE',
    })
  })

  it('returns problem details for routes outside the development gateway contract', async () => {
    const response = await fetch(`${BASE_URL}/api/unknown`)
    const data = await responseJsonObject(response)

    expect(response.status).toBe(404)
    expect(response.headers.get('content-type')).toContain('application/problem+json')
    expect(data).toMatchObject({
      status: 404,
      instance: '/api/unknown',
      code: 'NOT_FOUND',
    })
  })
})

function postPreview(body: unknown): Promise<Response> {
  return fetch(`${BASE_URL}/api/executions/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

async function waitForServer(url: string, timeout: number): Promise<void> {
  const startTime = Date.now()
  while (Date.now() - startTime < timeout) {
    try {
      const response = await fetch(`${url}/health`, {
        signal: AbortSignal.timeout(1000),
      })
      if (response.ok) return
    } catch {
      // Retry until the process binds its loopback listener.
    }
    await new Promise((resolve) => setTimeout(resolve, 300))
  }
  throw new Error(`Server did not start within ${timeout}ms`)
}

async function responseJsonObject(response: Response): Promise<Record<string, unknown>> {
  const data: unknown = await response.json()
  if (data === null || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Expected JSON object response')
  }
  return data as Record<string, unknown>
}
