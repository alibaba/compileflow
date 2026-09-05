import type { TbbpmNode } from '../../types/tbbpm'
import { BaseNodeValidator, type ValidationContext } from '../NodeValidationStrategy'

export class StartNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext) {
    const issues = []
    if (this.outgoing(node.id, ctx).length === 0) {
      issues.push(this.issue(node.id, 'start.mustHaveOutgoing'))
    }
    if (this.incoming(node.id, ctx).length > 0) {
      issues.push(this.issue(node.id, 'start.shouldNotHaveIncoming', 'error'))
    }
    return issues
  }
}
