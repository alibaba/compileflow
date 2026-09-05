import { register } from '@antv/x6-react-shape'

import { AutoTaskNode } from './AutoTaskNode'
import { EndNode } from './EndNode'
import { ExclusiveNode } from './ExclusiveNode'
import {
  BreakNode,
  ContinueNode,
  InclusiveNode,
  ForEachNode,
  WhileNode,
  NoteNode,
  ParallelNode,
  SubBpmNode,
  TimerTaskNode,
  BpmCallNode,
  WaitEventTaskNode,
} from './OtherNodes'
import { ScriptTaskNode } from './ScriptTaskNode'
import { StartNode } from './StartNode'
import { WaitTaskNode } from './WaitTaskNode'

import {
  isTbbpmNodeType,
  TBBPM_NODE_TYPES,
  type TbbpmNodeType,
} from '@/authoring/designer/types/tbbpm'
import { createLogger } from '@/shared/logging/logger'
import { getNodeColor } from '@/shared/styles/design-tokens'

const logger = createLogger('RegisterNodes')

const NODE_CONFIGS = {
  start: {
    shape: 'tbbpm-start',
    component: StartNode,
    width: 80,
    height: 80,
    ports: [{ id: 'bottom', group: 'bottom' }],
  },
  end: {
    shape: 'tbbpm-end',
    component: EndNode,
    width: 80,
    height: 80,
    ports: [{ id: 'top', group: 'top' }],
  },
  autoTask: {
    shape: 'tbbpm-auto-task',
    component: AutoTaskNode,
    width: 200,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  waitTask: {
    shape: 'tbbpm-wait-task',
    component: WaitTaskNode,
    width: 200,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  waitEventTask: {
    shape: 'tbbpm-wait-event-task',
    component: WaitEventTaskNode,
    width: 200,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  timerTask: {
    shape: 'tbbpm-timer-task',
    component: TimerTaskNode,
    width: 200,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  scriptTask: {
    shape: 'tbbpm-script-task',
    component: ScriptTaskNode,
    width: 200,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  exclusive: {
    shape: 'tbbpm-exclusive',
    component: ExclusiveNode,
    width: 100,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  parallel: {
    shape: 'tbbpm-parallel',
    component: ParallelNode,
    width: 100,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  inclusive: {
    shape: 'tbbpm-inclusive',
    component: InclusiveNode,
    width: 100,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  subBpm: {
    shape: 'tbbpm-sub-bpm',
    component: SubBpmNode,
    width: 220,
    height: 120,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  bpmCall: {
    shape: 'tbbpm-bpm-call',
    component: BpmCallNode,
    width: 220,
    height: 120,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  while: {
    shape: 'tbbpm-while',
    component: WhileNode,
    width: 220,
    height: 120,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  foreach: {
    shape: 'tbbpm-foreach',
    component: ForEachNode,
    width: 220,
    height: 120,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
    ],
  },
  continue: {
    shape: 'tbbpm-continue',
    component: ContinueNode,
    width: 120,
    height: 60,
    ports: [{ id: 'top', group: 'top' }],
  },
  break: {
    shape: 'tbbpm-break',
    component: BreakNode,
    width: 120,
    height: 60,
    ports: [{ id: 'top', group: 'top' }],
  },
  note: {
    shape: 'tbbpm-note',
    component: NoteNode,
    width: 180,
    height: 120,
    ports: [],
  },
} as const

let isRegistered = false

export function registerTbbpmNodes() {
  if (isRegistered) {
    logger.debug('Already registered, skipping')
    return
  }

  Object.entries(NODE_CONFIGS).forEach(([nodeType, config]) => {
    register({
      shape: config.shape,
      width: config.width,
      height: config.height,
      component: config.component,
      effect: ['data'],
      ports: {
        groups: {
          top: {
            position: 'top',
            attrs: {
              circle: {
                r: 6,
                magnet: true,
                stroke: getNodeColor(nodeType),
                strokeWidth: 2,
                fill: '#fff',
              },
            },
          },
          bottom: {
            position: 'bottom',
            attrs: {
              circle: {
                r: 6,
                magnet: true,
                stroke: getNodeColor(nodeType),
                strokeWidth: 2,
                fill: '#fff',
              },
            },
          },
          left: {
            position: 'left',
            attrs: {
              circle: {
                r: 6,
                magnet: true,
                stroke: getNodeColor(nodeType),
                strokeWidth: 2,
                fill: '#fff',
              },
            },
          },
          right: {
            position: 'right',
            attrs: {
              circle: {
                r: 6,
                magnet: true,
                stroke: getNodeColor(nodeType),
                strokeWidth: 2,
                fill: '#fff',
              },
            },
          },
        },
        items: config.ports,
      },
    })
  })

  isRegistered = true
  logger.info('Registered 14 TBBPM node types to X6')
}

export function getNodeConfig(nodeType: string) {
  return isTbbpmNodeType(nodeType) ? NODE_CONFIGS[nodeType] : undefined
}

const SHAPE_TO_NODE_TYPE: ReadonlyMap<string, TbbpmNodeType> = new Map(
  TBBPM_NODE_TYPES.map((type) => [NODE_CONFIGS[type].shape, type])
)

export function getNodeTypeByShape(shape: string): TbbpmNodeType | undefined {
  return SHAPE_TO_NODE_TYPE.get(shape)
}
