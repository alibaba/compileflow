const MAX_JAVA_MILLISECONDS = 9_223_372_036_854_775_807n
const DURATION_PATTERN = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)(?:\.(\d{1,9}))?S)?)?$/

export interface ProtocolDurationConstraints {
  positive?: boolean
  maximumMilliseconds?: bigint
}

export function parseProtocolDuration(
  rawValue: string,
  fieldName: string,
  constraints: ProtocolDurationConstraints = {}
): bigint {
  const match = matchProtocolDuration(rawValue, fieldName)
  const milliseconds = durationMilliseconds(match, fieldName)
  validateDurationBounds(milliseconds, fieldName, constraints)
  return milliseconds
}

function matchProtocolDuration(rawValue: string, fieldName: string): RegExpMatchArray {
  if (rawValue !== rawValue.trim()) {
    throw new Error(`${fieldName} must not contain surrounding whitespace`)
  }
  const match = DURATION_PATTERN.exec(rawValue)
  if (!match || rawValue === 'P' || rawValue.endsWith('T')) {
    throw new Error(`${fieldName} must be a non-negative ISO-8601 duration`)
  }
  return match
}

function durationMilliseconds(match: RegExpMatchArray, fieldName: string): bigint {
  const [, days = '0', hours = '0', minutes = '0', seconds = '0', fraction = ''] = match
  const wholeSeconds =
    ((BigInt(days) * 24n + BigInt(hours)) * 60n + BigInt(minutes)) * 60n + BigInt(seconds)
  const nanoseconds = wholeSeconds * 1_000_000_000n + BigInt(fraction.padEnd(9, '0') || '0')
  if (nanoseconds % 1_000_000n !== 0n) {
    throw new Error(`${fieldName} must use whole-millisecond precision`)
  }
  const milliseconds = nanoseconds / 1_000_000n
  if (milliseconds > MAX_JAVA_MILLISECONDS) {
    throw new Error(`${fieldName} must be representable as Java milliseconds`)
  }
  return milliseconds
}

function validateDurationBounds(
  milliseconds: bigint,
  fieldName: string,
  constraints: ProtocolDurationConstraints
): void {
  if (constraints.positive && milliseconds === 0n) {
    throw new Error(`${fieldName} must be positive`)
  }
  if (
    constraints.maximumMilliseconds !== undefined &&
    milliseconds > constraints.maximumMilliseconds
  ) {
    throw new Error(`${fieldName} exceeds the protocol maximum`)
  }
}

export { MAX_JAVA_MILLISECONDS }
