import type { ActionDefinition, VariableMapping } from './action'
import type { BaseConnection, BaseNode } from './graphTypes'

export const TBBPM_NODE_TYPES = [
  'start',
  'end',
  'autoTask',
  'exclusive',
  'parallel',
  'inclusive',
  'subBpm',
  'bpmCall',
  'waitTask',
  'waitEventTask',
  'timerTask',
  'scriptTask',
  'while',
  'foreach',
  'break',
  'continue',
  'note',
] as const

export type TbbpmNodeType = (typeof TBBPM_NODE_TYPES)[number]

export const TBBPM_NODE_SIZES: Record<
  TbbpmNodeType,
  Readonly<{ width: number; height: number }>
> = {
  start: { width: 80, height: 80 },
  end: { width: 80, height: 80 },
  autoTask: { width: 200, height: 100 },
  waitTask: { width: 200, height: 100 },
  waitEventTask: { width: 200, height: 100 },
  timerTask: { width: 200, height: 100 },
  scriptTask: { width: 200, height: 100 },
  exclusive: { width: 100, height: 100 },
  parallel: { width: 100, height: 100 },
  inclusive: { width: 100, height: 100 },
  subBpm: { width: 220, height: 120 },
  bpmCall: { width: 220, height: 120 },
  while: { width: 220, height: 120 },
  foreach: { width: 220, height: 120 },
  continue: { width: 120, height: 60 },
  break: { width: 120, height: 60 },
  note: { width: 180, height: 120 },
}

const TBBPM_NODE_TYPE_SET: ReadonlySet<string> = new Set(TBBPM_NODE_TYPES)

export function isTbbpmNodeType(value: string): value is TbbpmNodeType {
  return TBBPM_NODE_TYPE_SET.has(value)
}

const TBBPM_REGULAR_FLOW_NODE_TYPES = [
  'autoTask',
  'exclusive',
  'parallel',
  'inclusive',
  'subBpm',
  'bpmCall',
  'waitTask',
  'waitEventTask',
  'timerTask',
  'scriptTask',
  'while',
  'foreach',
] as const satisfies readonly TbbpmNodeType[]

export const TBBPM_ROOT_NODE_TYPES: ReadonlySet<TbbpmNodeType> = new Set([
  'start',
  'end',
  'note',
  ...TBBPM_REGULAR_FLOW_NODE_TYPES,
])

export const TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES: ReadonlySet<TbbpmNodeType> = new Set([
  'start',
  'end',
  'break',
  'continue',
  'note',
  ...TBBPM_REGULAR_FLOW_NODE_TYPES,
])

export function getTbbpmChildNodeTypes(
  containerType: TbbpmNodeType
): ReadonlySet<TbbpmNodeType> | undefined {
  if (containerType === 'while' || containerType === 'foreach' || containerType === 'subBpm') {
    return TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES
  }
  return undefined
}

type TbbpmLoopExecution = 'sequential' | 'parallel'

export interface TbbpmNode extends BaseNode {
  type: TbbpmNodeType
  properties: {
    action?: ActionDefinition
    event?: string
    timeout?: string
    duration?: string
    durationExpression?: string
    wakeAtExpression?: string
    code?: string
    classpath?: string
    version?: string
    callMappings?: VariableMapping[]
    collection?: string
    item?: string
    index?: string
    itemType?: string
    condition?: string
    maxIterations?: number
    execution?: TbbpmLoopExecution
    output?: { target: string; source: string }
    comment?: string
  }
}

export interface TbbpmConnection extends BaseConnection {
  to?: string
  from?: string
  condition?: string
  name?: string
  /** Transition path hint written by the engine (e.g. "10,10,80,80"). */
  g?: string
}

export interface TbbpmVar {
  name: string
  inOutType: 'param' | 'return' | 'inner'
  dataType: string
  description?: string
  defaultValue?: string
}
