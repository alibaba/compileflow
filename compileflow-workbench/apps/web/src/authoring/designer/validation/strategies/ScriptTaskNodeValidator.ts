import type { TbbpmNode } from '../../types/tbbpm'
import type { ValidationContext, ValidationIssue } from '../NodeValidationStrategy'

import { AutoTaskNodeValidator } from './AutoTaskNodeValidator'

export class ScriptTaskNodeValidator extends AutoTaskNodeValidator {
  override validate(node: TbbpmNode, ctx: ValidationContext): ValidationIssue[] {
    const issues = super.validate(node, ctx)
    const actionType = node.properties.action?.actionType.trim() || ''
    if (actionType !== 'script') {
      issues.push(
        this.issue(node.id, 'scriptTask.unsupportedActionType', 'error', 'compileflow', {
          actionType,
        })
      )
    }
    return issues
  }
}
