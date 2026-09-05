import type { TbbpmNode } from '../../types/tbbpm'
import { BaseNodeValidator, type ValidationContext } from '../NodeValidationStrategy'

export class EndNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext) {
    const issues = []
    if (this.incoming(node.id, ctx).length === 0) {
      issues.push(this.issue(node.id, 'end.mustHaveIncoming'))
    }
    if (this.outgoing(node.id, ctx).length > 0) {
      issues.push(this.issue(node.id, 'end.shouldNotHaveOutgoing', 'error'))
    }
    return issues
  }
}
