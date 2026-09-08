import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'

export enum ErrorSeverity {
  MEDIUM = 'medium',
  HIGH = 'high',
}

export class AppError extends Error {
  constructor(
    message: string,
    public code: string,
    public severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    public context?: Record<string, unknown>
  ) {
    super(message)
    this.name = 'AppError'
  }

  toJSON() {
    return {
      name: this.name,
      message: this.message,
      code: this.code,
      severity: this.severity,
      context: this.context,
      stack: APP_BUILD_CONFIG.buildMode === 'development' ? this.stack : undefined,
    }
  }
}

export class NetworkError extends AppError {
  constructor(message: string, context?: Record<string, unknown>) {
    super(message, 'NETWORK_ERROR', ErrorSeverity.HIGH, context)
    this.name = 'NetworkError'
  }
}

export function toError(error: unknown, fallbackMessage = 'Unknown error'): Error {
  if (error instanceof Error) {
    return error
  }
  if (error === null || error === undefined) {
    return new Error(fallbackMessage)
  }

  try {
    if (typeof error === 'object' && error !== null) {
      const serialized = JSON.stringify(error)
      if (serialized) return new Error(serialized)
    }
    return new Error(String(error))
  } catch {
    return new Error(fallbackMessage)
  }
}
