import type { TbbpmNodeType } from '../types/tbbpm'

import type { NodeValidationStrategy } from './NodeValidationStrategy'
import {
  AutoTaskNodeValidator,
  BreakContinueNodeValidator,
  ExclusiveNodeValidator,
  EndNodeValidator,
  GatewayNodeValidator,
  LoopNodeValidator,
  ScriptTaskNodeValidator,
  StartNodeValidator,
  BpmCallNodeValidator,
  TimerTaskNodeValidator,
  WaitTaskNodeValidator,
} from './strategies'
import { NoOpNodeValidator } from './strategies/BreakContinueNodeValidator'

/** Registry of per-node-type validation strategies. */
export class TbbpmValidatorFactory {
  private readonly strategies: Readonly<Record<TbbpmNodeType, NodeValidationStrategy>> = {
    start: new StartNodeValidator(),
    end: new EndNodeValidator(),
    autoTask: new AutoTaskNodeValidator(),
    scriptTask: new ScriptTaskNodeValidator(),
    exclusive: new ExclusiveNodeValidator(),
    subBpm: new NoOpNodeValidator(),
    bpmCall: new BpmCallNodeValidator(),
    while: new LoopNodeValidator(),
    foreach: new LoopNodeValidator(),
    waitTask: new WaitTaskNodeValidator(),
    waitEventTask: new WaitTaskNodeValidator(),
    timerTask: new TimerTaskNodeValidator(),
    parallel: new GatewayNodeValidator(),
    inclusive: new GatewayNodeValidator(),
    break: new BreakContinueNodeValidator(),
    continue: new BreakContinueNodeValidator(),
    note: new NoOpNodeValidator(),
  }

  getStrategy(nodeType: TbbpmNodeType): NodeValidationStrategy {
    return this.strategies[nodeType]
  }
}
