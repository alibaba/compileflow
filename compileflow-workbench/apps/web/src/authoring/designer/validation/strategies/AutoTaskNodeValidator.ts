import { type InvocationPolicy, validateInvocationPolicy } from '../../types/invocationPolicy'
import type { TbbpmNode } from '../../types/tbbpm'
import { actionFindings } from '../actionValidation'
import {
  BaseNodeValidator,
  type ValidationContext,
  type ValidationIssue,
} from '../NodeValidationStrategy'

import { toError } from '@/shared/errors'

export class AutoTaskNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext): ValidationIssue[] {
    void ctx
    const action = node.properties.action

    const issues = actionFindings(action).map((finding) =>
      this.issue(node.id, finding.code, 'error', 'property', finding.params)
    )
    if (node.type === 'autoTask' && action?.actionType === 'script') {
      issues.push(
        this.issue(node.id, 'autoTask.unsupportedActionType', 'error', 'compileflow', {
          actionType: action.actionType,
        })
      )
    }
    issues.push(...this.validateInvocationPolicy(node, action?.invocationPolicy))
    return issues
  }

  private validateInvocationPolicy(
    node: TbbpmNode,
    policy: InvocationPolicy | undefined
  ): ValidationIssue[] {
    if (!policy) return []
    const issues: ValidationIssue[] = []
    try {
      validateInvocationPolicy(policy)
    } catch (error) {
      issues.push(
        this.issue(node.id, 'invocationPolicy.invalid', 'error', 'property', {
          message: toError(error).message,
        })
      )
    }
    return issues
  }
}
