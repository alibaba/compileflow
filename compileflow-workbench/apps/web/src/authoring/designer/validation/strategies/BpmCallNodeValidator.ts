import type { TbbpmNode } from '../../types/tbbpm'
import { BaseNodeValidator, type ValidationContext } from '../NodeValidationStrategy'
import { processCallReferenceIssue } from '../processCallReference'

export class BpmCallNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, _ctx: ValidationContext) {
    void _ctx
    const { classpath, code, version } = node.properties
    if (!code) {
      return [this.issue(node.id, 'bpmCall.missingCode', 'error', 'property')]
    }
    const referenceIssue = processCallReferenceIssue({
      code,
      classpath,
      version,
    })
    if (referenceIssue) {
      return [this.issue(node.id, `processCall.${referenceIssue}`, 'error', 'property')]
    }
    return []
  }
}
