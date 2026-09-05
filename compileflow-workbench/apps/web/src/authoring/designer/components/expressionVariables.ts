import type { ProcessVariable } from '../types/flowDefinition'

export interface ExpressionVariable {
  name: string
  type: string
  description?: string
}

export function toExpressionVariables(
  variables: readonly ProcessVariable[] | undefined
): ExpressionVariable[] {
  return (variables ?? [])
    .filter((variable) => variable.name.trim().length > 0)
    .map((variable) => ({
      name: variable.name,
      type: variable.type.trim() || 'java.lang.Object',
      description: variable.description,
    }))
}
