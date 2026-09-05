#!/usr/bin/env node

/**
 * Minimal trusted edge for Vite secured e2e:
 * strips client credential headers and injects the server API key.
 * Upstream: Workbench Server :8082  |  Listen: :4174
 */
import { createServer, request as createUpstreamRequest } from 'node:http'

const LISTEN_HOST = '127.0.0.1'
const LISTEN_PORT = Number(process.env.COMPILEFLOW_SECURED_EDGE_PORT ?? 4174)
const UPSTREAM = new URL(process.env.COMPILEFLOW_SECURED_UPSTREAM ?? 'http://127.0.0.1:8082')
const UPSTREAM_TIMEOUT_MS = 30_000
const apiKey = process.env.COMPILEFLOW_E2E_SERVER_API_KEY

if (!apiKey) {
  throw new Error('COMPILEFLOW_E2E_SERVER_API_KEY is required')
}
if (apiKey.length < 32 || apiKey.length > 256 || !/^[A-Za-z0-9._~-]+$/.test(apiKey)) {
  throw new Error('COMPILEFLOW_E2E_SERVER_API_KEY must be 32..256 URL-safe characters')
}

const hopByHopHeaders = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'proxy-connection',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
])
const untrustedHeaders = new Set([
  'authorization',
  'cookie',
  'forwarded',
  'x-api-key',
  'x-forwarded-for',
  'x-forwarded-host',
  'x-forwarded-port',
  'x-forwarded-proto',
  'x-real-ip',
])

function upstreamHeaders(request) {
  const headers = {}
  for (const [name, value] of Object.entries(request.headers)) {
    if (
      value !== undefined &&
      !hopByHopHeaders.has(name) &&
      !untrustedHeaders.has(name) &&
      !name.startsWith('x-forwarded-')
    ) {
      headers[name] = value
    }
  }
  headers.host = UPSTREAM.host
  headers['x-forwarded-proto'] = 'http'
  headers['x-api-key'] = apiKey
  return headers
}

function downstreamHeaders(headers) {
  const filtered = { ...headers }
  for (const name of hopByHopHeaders) delete filtered[name]
  return filtered
}

function targetPath(requestUrl) {
  const url = new URL(requestUrl ?? '/', `http://${LISTEN_HOST}:${LISTEN_PORT}`)
  const pathname = url.pathname === '/health' ? '/actuator/health' : url.pathname
  return `${pathname}${url.search}`
}

const server = createServer((request, response) => {
  const upstreamRequest = createUpstreamRequest(
    {
      protocol: UPSTREAM.protocol,
      hostname: UPSTREAM.hostname,
      port: UPSTREAM.port,
      method: request.method,
      path: targetPath(request.url),
      headers: upstreamHeaders(request),
    },
    (upstreamResponse) => {
      response.writeHead(
        upstreamResponse.statusCode ?? 502,
        downstreamHeaders(upstreamResponse.headers)
      )
      upstreamResponse.pipe(response)
    }
  )

  upstreamRequest.setTimeout(UPSTREAM_TIMEOUT_MS, () => {
    upstreamRequest.destroy(new Error('Workbench Server request timed out'))
  })
  upstreamRequest.on('error', (error) => {
    if (!response.headersSent) {
      response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
    }
    response.end(`Workbench Server is unavailable: ${error.message}\n`)
  })
  request.on('aborted', () => upstreamRequest.destroy())
  request.pipe(upstreamRequest)
})

server.headersTimeout = UPSTREAM_TIMEOUT_MS + 5_000
server.requestTimeout = UPSTREAM_TIMEOUT_MS + 5_000
server.listen(LISTEN_PORT, LISTEN_HOST, () => {
  process.stdout.write(
    `Workbench secured edge listening on http://${LISTEN_HOST}:${LISTEN_PORT} → ${UPSTREAM.origin}\n`
  )
})

function shutdown() {
  server.close(() => process.exit(0))
  setTimeout(() => process.exit(1), 5_000).unref()
}

process.once('SIGINT', shutdown)
process.once('SIGTERM', shutdown)
