import type { TbbpmNode } from '../../types/tbbpm'
import { BaseNodeValidator, type ValidationContext } from '../NodeValidationStrategy'

export class TimerTaskNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, _ctx: ValidationContext) {
    void _ctx
    const schedules = [
      node.properties.duration,
      node.properties.durationExpression,
      node.properties.wakeAtExpression,
    ].filter((value) => Boolean(value?.trim()))
    return schedules.length === 1
      ? []
      : [this.issue(node.id, 'timerTask.schedule', 'error', 'property')]
  }
}
