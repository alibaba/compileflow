import { APP_BUILD_CONFIG, sanitizeDiagnosticValue } from '@/shared/config/buildConfig'

interface LogContext {
  component?: string
  action?: string
  data?: Record<string, unknown>

  [key: string]: unknown
}

type LogTransport = Pick<
  Console,
  'error' | 'group' | 'groupEnd' | 'log' | 'time' | 'timeEnd' | 'warn'
>

const consoleTransport: LogTransport = globalThis.console

class Logger {
  private static readonly PREFIX = '[CompileFlow]'

  constructor(private readonly transport: LogTransport = consoleTransport) {}

  debug(message: string, context?: LogContext): void {
    this.log('DEBUG', message, context)
  }

  info(message: string, context?: LogContext): void {
    this.log('INFO', message, context)
  }

  warn(message: string, context?: LogContext): void {
    this.log('WARN', message, context)
  }

  error(message: string, context?: LogContext, error?: Error): void {
    this.log('ERROR', message, context, error)
  }

  time(label: string): void {
    if (APP_BUILD_CONFIG.enableDebug) {
      this.transport.time(`${Logger.PREFIX} ${sanitizeText(label)}`)
    }
  }

  timeEnd(label: string): void {
    if (APP_BUILD_CONFIG.enableDebug) {
      this.transport.timeEnd(`${Logger.PREFIX} ${sanitizeText(label)}`)
    }
  }

  group(label: string): void {
    if (APP_BUILD_CONFIG.enableDebug) {
      this.transport.group(`${Logger.PREFIX} ${sanitizeText(label)}`)
    }
  }

  groupEnd(): void {
    if (APP_BUILD_CONFIG.enableDebug) {
      this.transport.groupEnd()
    }
  }

  createChild(component: string): ComponentLogger {
    return new ComponentLogger(this, component)
  }

  private format(level: string, message: string, context?: LogContext): string {
    const parts = [Logger.PREFIX, new Date().toISOString(), `[${level}]`]
    if (context?.component) {
      parts.push(`[${sanitizeText(context.component)}]`)
    }
    if (context?.action) {
      parts.push(`{${sanitizeText(context.action)}}`)
    }
    parts.push(sanitizeText(message))
    return parts.join(' ')
  }

  private log(levelName: string, message: string, context?: LogContext, error?: Error): void {
    if (!APP_BUILD_CONFIG.enableDebug) {
      return
    }

    const formattedMessage = this.format(levelName, message, context)
    const args: unknown[] = [formattedMessage]
    if (context?.data) {
      args.push(sanitizeDiagnosticValue(context.data))
    }
    if (error) {
      args.push(sanitizeDiagnosticValue(error))
    }

    switch (levelName) {
      case 'DEBUG':
      case 'INFO':
        this.transport.log(...args)
        break
      case 'WARN':
        this.transport.warn(...args)
        break
      case 'ERROR':
        this.transport.error(...args)
        break
      default:
        throw new Error(`Unsupported log level: ${levelName}`)
    }
  }
}

function sanitizeText(value: string): string {
  return String(sanitizeDiagnosticValue(value))
}

class ComponentLogger {
  constructor(
    private parent: Logger,
    private component: string
  ) {}

  debug(message: string, data?: Record<string, unknown>): void {
    this.parent.debug(message, { component: this.component, data })
  }

  info(message: string, data?: Record<string, unknown>): void {
    this.parent.info(message, { component: this.component, data })
  }

  warn(message: string, data?: Record<string, unknown>): void {
    this.parent.warn(message, { component: this.component, data })
  }

  error(message: string, error?: Error, data?: Record<string, unknown>): void {
    this.parent.error(message, { component: this.component, data }, error)
  }

  action(action: string, message: string, data?: Record<string, unknown>): void {
    this.parent.debug(message, { component: this.component, action, data })
  }
}

export const logger = new Logger()

export function createLogger(component: string): ComponentLogger {
  return logger.createChild(component)
}
