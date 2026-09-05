import type { TbbpmNode } from '../../types/tbbpm'
import { findDirectJavaMutation } from '../javaConditionExpression'
import {
  BaseNodeValidator,
  type ValidationContext,
  type ValidationIssue,
} from '../NodeValidationStrategy'

/** Rejects mutating expressions on break and continue nodes. */
export class BreakContinueNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext): ValidationIssue[] {
    void ctx
    const mutation = findDirectJavaMutation(node.properties.condition)
    return mutation
      ? [
          this.issue(node.id, 'condition.directMutation', 'error', 'property', {
            operator: mutation,
          }),
        ]
      : []
  }
}

/** Validates node types that intentionally require no per-property checks. */
export class NoOpNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext): ValidationIssue[] {
    void ctx
    void node
    return []
  }
}
