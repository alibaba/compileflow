import { createRequire } from 'node:module'
import http from 'node:http'

import 'dotenv/config'

import express, { type NextFunction, type Request, type Response } from 'express'

import { loadDevGatewayConfig } from './config.js'
import { parseExecutionRequest } from './executionRequest.js'
import { createLogger } from './logger.js'
import { MockEngine } from './mockEngine.js'
import { sendProblem } from './problemDetails.js'

const require = createRequire(import.meta.url)
const packageVersion = (require('../package.json') as { version: string }).version
const app = express()
const config = loadDevGatewayConfig()
const jsonBody = express.json({ limit: config.maxRequestBytes })
const logger = createLogger('DevGateway', config.logLevel)
const mockEngine = new MockEngine(config.logLevel)

interface HttpError extends Error {
  status?: number
  type?: string
}

app.use((req: Request, _res: Response, next: NextFunction) => {
  logger.debug(`${req.method} ${req.path}`)
  next()
})

app.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    service: 'CompileFlow Workbench Development Gateway',
    version: packageVersion,
    timestamp: new Date().toISOString(),
  })
})

app.get('/api/status', (_req: Request, res: Response) => {
  res.json({
    engineAvailable: true,
    message: 'Development mock preview is available',
  })
})

app.post('/api/executions/preview', jsonBody, async (req: Request, res: Response) => {
  try {
    const parsed = parseExecutionRequest(req.body)
    if (!parsed.ok) {
      sendProblem(req, res, 400, 'INVALID_ARGUMENT', 'Invalid request', parsed.message)
      return
    }
    res.json(await mockEngine.execute(parsed.request))
  } catch (error) {
    logger.error('Mock preview failed', error)
    sendProblem(
      req,
      res,
      500,
      'DEV_GATEWAY_INTERNAL_ERROR',
      'Internal server error',
      'An unexpected development gateway error occurred'
    )
  }
})

app.use((req: Request, res: Response) => {
  sendProblem(
    req,
    res,
    404,
    'NOT_FOUND',
    'Not found',
    `No development gateway route matches ${req.method} ${req.path}`
  )
})

app.use((error: HttpError, req: Request, res: Response, _next: NextFunction) => {
  if (error.status === 413 || error.type === 'entity.too.large') {
    sendProblem(
      req,
      res,
      413,
      'REQUEST_TOO_LARGE',
      'Payload too large',
      `Request body exceeds ${config.maxRequestBytes} bytes`
    )
    return
  }
  if (error.status === 400 || error.type === 'entity.parse.failed') {
    sendProblem(
      req,
      res,
      400,
      'MALFORMED_REQUEST_BODY',
      'Malformed request body',
      'Invalid request: malformed JSON body'
    )
    return
  }
  logger.error('Unhandled request error', error)
  sendProblem(
    req,
    res,
    500,
    'DEV_GATEWAY_INTERNAL_ERROR',
    'Internal server error',
    'An unexpected development gateway error occurred'
  )
})

const server = http.createServer(app)

server.listen(config.port, config.host, () => {
  logger.info('HTTP server listening', {
    url: `http://${config.host}:${config.port}`,
  })
})

function gracefulShutdown(signal: string): void {
  logger.info('Shutdown signal received', { signal })
  const forceExitTimer = setTimeout(() => {
    logger.error('Graceful shutdown timed out, forcing exit')
    process.exit(1)
  }, 15_000)
  forceExitTimer.unref()

  server.close(() => {
    logger.info('Shutdown complete')
    clearTimeout(forceExitTimer)
    process.exit(0)
  })
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'))
process.on('SIGINT', () => gracefulShutdown('SIGINT'))
process.on('uncaughtException', (error) => {
  logger.error('Uncaught exception; exiting', error)
  process.exit(1)
})
process.on('unhandledRejection', (reason) => {
  logger.error('Unhandled promise rejection; exiting', reason)
  process.exit(1)
})
