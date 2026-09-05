export const MAX_LOOP_ITERATIONS = 2_147_483_647

export function isValidLoopIterationLimit(value: unknown): value is number {
  return (
    typeof value === 'number' &&
    Number.isSafeInteger(value) &&
    value >= 1 &&
    value <= MAX_LOOP_ITERATIONS
  )
}
