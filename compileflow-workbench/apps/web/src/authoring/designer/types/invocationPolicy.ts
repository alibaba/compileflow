import { MAX_JAVA_MILLISECONDS, parseProtocolDuration } from './protocolDuration'
import { validateSymbolicIdentifier } from './symbolicIdentifier'

/** Canonical synchronous invocation policy shared by TBBPM and BPMN authoring. */
export interface InvocationPolicy {
  timeout?: string
  attemptTimeout?: string
  maxAttempts?: number
  initialBackoff?: string
  backoffMultiplier?: number
  maxBackoff?: string
  jitter?: 'none' | 'full'
  retryOn?: string
  onFailure?: string
}

const MAX_ATTEMPTS = 100
const DEFAULT_INITIAL_BACKOFF = 'PT1S'
const DEFAULT_MAX_BACKOFF_MULTIPLIER = 100n

export function validateInvocationPolicy(policy: InvocationPolicy): void {
  const timeout =
    policy.timeout === undefined
      ? undefined
      : parseProtocolDuration(policy.timeout, 'timeout', { positive: true })
  const attemptTimeout =
    policy.attemptTimeout === undefined
      ? undefined
      : parseProtocolDuration(policy.attemptTimeout, 'attemptTimeout', { positive: true })
  if (timeout !== undefined && attemptTimeout !== undefined && attemptTimeout > timeout) {
    throw new Error('attemptTimeout must be less than or equal to timeout')
  }
  const initialBackoff = parseProtocolDuration(
    policy.initialBackoff ?? DEFAULT_INITIAL_BACKOFF,
    'initialBackoff'
  )
  const maxBackoff =
    policy.maxBackoff === undefined
      ? undefined
      : parseProtocolDuration(policy.maxBackoff, 'maxBackoff')
  validateBackoffWindow(initialBackoff, maxBackoff)
  validateMaxAttempts(policy.maxAttempts)
  validateBackoffMultiplier(policy.backoffMultiplier)
  validateJitter(policy.jitter)
  validatePolicyIdentifier(policy.retryOn, 'retryOn')
  validatePolicyIdentifier(policy.onFailure, 'onFailure')
}

function validateBackoffWindow(initialBackoff: bigint, maxBackoff?: bigint): void {
  if (
    maxBackoff === undefined &&
    initialBackoff > MAX_JAVA_MILLISECONDS / DEFAULT_MAX_BACKOFF_MULTIPLIER
  ) {
    throw new Error('default maxBackoff must be representable as Java milliseconds')
  }
  if (maxBackoff !== undefined && maxBackoff < initialBackoff) {
    throw new Error('maxBackoff must be greater than or equal to initialBackoff')
  }
}

function validateMaxAttempts(maxAttempts?: number): void {
  if (maxAttempts === undefined) return
  if (!Number.isSafeInteger(maxAttempts) || maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS) {
    throw new Error(`maxAttempts must be an integer from 1 through ${MAX_ATTEMPTS}`)
  }
}

function validateBackoffMultiplier(multiplier?: number): void {
  if (multiplier === undefined) return
  if (!Number.isFinite(multiplier) || multiplier < 1) {
    throw new Error('backoffMultiplier must be finite and greater than or equal to 1')
  }
}

function validateJitter(jitter?: InvocationPolicy['jitter']): void {
  if (jitter !== undefined && jitter !== 'none' && jitter !== 'full') {
    throw new Error('jitter must be one of: none, full')
  }
}

function validatePolicyIdentifier(value: string | undefined, fieldName: string): void {
  if (value === undefined) return
  validateSymbolicIdentifier(value, fieldName)
}
