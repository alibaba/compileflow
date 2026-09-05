import type { TbbpmConnection, TbbpmNode } from '../types/tbbpm'

export interface ValidationContext {
  nodes: TbbpmNode[]
  connections: TbbpmConnection[]
  processVariableNames: ReadonlySet<string>
  processVariables: ReadonlyMap<
    string,
    { type?: string; dataType?: string; inOutType?: 'param' | 'return' | 'inner' }
  >
}

export interface NodeValidationStrategy {
  validate(node: TbbpmNode, context: ValidationContext): ValidationIssue[]
}

/** A single validation issue produced by a strategy. */
export interface ValidationIssue {
  elementId: string
  code: string
  params?: Record<string, string | number>
  severity: 'error' | 'warning' | 'info'
  category: 'structure' | 'property' | 'compileflow'
}

export abstract class BaseNodeValidator implements NodeValidationStrategy {
  abstract validate(node: TbbpmNode, context: ValidationContext): ValidationIssue[]

  protected issue(
    elementId: string,
    code: string,
    severity: 'error' | 'warning' | 'info' = 'error',
    category: 'structure' | 'property' | 'compileflow' = 'structure',
    params?: Record<string, string | number>
  ): ValidationIssue {
    return { elementId, code, severity, category, params }
  }

  protected outgoing(nodeId: string, ctx: ValidationContext): TbbpmConnection[] {
    return ctx.connections.filter((c) => c.sourceId === nodeId)
  }

  protected incoming(nodeId: string, ctx: ValidationContext): TbbpmConnection[] {
    return ctx.connections.filter((c) => c.targetId === nodeId)
  }
}
