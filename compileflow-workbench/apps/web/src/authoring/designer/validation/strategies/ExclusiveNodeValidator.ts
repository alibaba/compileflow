import type { TbbpmNode } from '../../types/tbbpm'
import type { ValidationContext } from '../NodeValidationStrategy'

import { GatewayNodeValidator } from './GatewayNodeValidator'

export class ExclusiveNodeValidator extends GatewayNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext) {
    const issues = super.validate(node, ctx)
    const outgoing = this.outgoing(node.id, ctx)
    const incomingCount = this.effectiveIncomingCount(node, this.incoming(node.id, ctx).length, ctx)
    const split = incomingCount === 1 && outgoing.length > 1
    if (outgoing.filter((connection) => !connection.condition?.trim()).length > 1) {
      issues.push(this.issue(node.id, 'exclusive.multipleDefaultBranches', 'error'))
    }
    const conditions = outgoing
      .map((connection) => connection.condition?.trim())
      .filter((condition): condition is string => Boolean(condition))
    if (split && new Set(conditions).size !== conditions.length) {
      issues.push(this.issue(node.id, 'exclusive.duplicateCondition', 'error', 'property'))
    }
    return issues
  }
}
