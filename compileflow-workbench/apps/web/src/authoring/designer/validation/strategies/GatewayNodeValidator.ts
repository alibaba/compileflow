import type { TbbpmNode } from '../../types/tbbpm'
import {
  BaseNodeValidator,
  type ValidationContext,
  type ValidationIssue,
} from '../NodeValidationStrategy'

export class GatewayNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext): ValidationIssue[] {
    const incoming = this.incoming(node.id, ctx)
    const outgoing = this.outgoing(node.id, ctx)
    const incomingCount = this.effectiveIncomingCount(node, incoming.length, ctx)
    const split = incomingCount === 1 && outgoing.length > 1
    const join = incomingCount > 1 && outgoing.length === 1

    return [
      ...this.validateShape(node, incomingCount, outgoing.length, split, join),
      ...this.validateRouting(node, incoming, outgoing, join),
      ...this.validateContainer(node, split),
    ]
  }

  protected effectiveIncomingCount(
    _node: TbbpmNode,
    incomingCount: number,
    _ctx: ValidationContext
  ): number {
    return incomingCount
  }

  private validateShape(
    node: TbbpmNode,
    incomingCount: number,
    outgoingCount: number,
    split: boolean,
    join: boolean
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = []
    if (!split && !join) {
      issues.push(
        this.issue(node.id, 'gateway.invalidShape', 'error', 'structure', {
          incoming: incomingCount,
          outgoing: outgoingCount,
        })
      )
    }
    return issues
  }

  private validateRouting(
    node: TbbpmNode,
    incoming: Array<{ condition?: string }>,
    outgoing: Array<{ condition?: string }>,
    join: boolean
  ): ValidationIssue[] {
    const issues: ValidationIssue[] = []
    if (join && outgoing.some(hasCondition)) {
      issues.push(this.issue(node.id, 'gateway.joinConditionUnsupported', 'error', 'property'))
    }
    if (node.type === 'parallel' && outgoing.some(hasCondition)) {
      issues.push(this.issue(node.id, 'gateway.parallelConditionUnsupported', 'error'))
    }
    if (node.type === 'parallel' && join && incoming.some(hasCondition)) {
      issues.push(
        this.issue(node.id, 'gateway.parallelJoinConditionUnsupported', 'error', 'property')
      )
    }
    if (
      node.type === 'inclusive' &&
      outgoing.filter((connection) => !connection.condition?.trim()).length > 1
    ) {
      issues.push(this.issue(node.id, 'gateway.multipleDefaultBranches', 'error'))
    }
    return issues
  }

  private validateContainer(node: TbbpmNode, split: boolean): ValidationIssue[] {
    const nestedConcurrentSplit =
      Boolean(node.parentId) && split && (node.type === 'parallel' || node.type === 'inclusive')
    return nestedConcurrentSplit
      ? [this.issue(node.id, 'gateway.nestedConcurrencyUnsupported', 'error', 'structure')]
      : []
  }
}

function hasCondition(connection: { condition?: string }): boolean {
  return Boolean(connection.condition?.trim())
}
