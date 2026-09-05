import type { TbbpmNode } from '../../types/tbbpm'
import { BaseNodeValidator, type ValidationContext } from '../NodeValidationStrategy'

export class WaitTaskNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, _ctx: ValidationContext) {
    void _ctx
    const issues = []
    if (node.type === 'waitEventTask' && !node.properties.event) {
      issues.push(this.issue(node.id, 'waitEventTask.missingEvent', 'error', 'property'))
    }
    return issues
  }
}
