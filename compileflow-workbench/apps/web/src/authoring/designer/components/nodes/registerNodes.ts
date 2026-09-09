import { register } from '@antv/x6-react-shape'

import { CONNECTION_PORTS } from '../../types/graphTypes'

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
  TBBPM_NODE_SIZES,
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
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  end: {
    shape: 'tbbpm-end',
    component: EndNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  autoTask: {
    shape: 'tbbpm-auto-task',
    component: AutoTaskNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  waitTask: {
    shape: 'tbbpm-wait-task',
    component: WaitTaskNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  waitEventTask: {
    shape: 'tbbpm-wait-event-task',
    component: WaitEventTaskNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  timerTask: {
    shape: 'tbbpm-timer-task',
    component: TimerTaskNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  scriptTask: {
    shape: 'tbbpm-script-task',
    component: ScriptTaskNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  exclusive: {
    shape: 'tbbpm-exclusive',
    component: ExclusiveNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  parallel: {
    shape: 'tbbpm-parallel',
    component: ParallelNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  inclusive: {
    shape: 'tbbpm-inclusive',
    component: InclusiveNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  subBpm: {
    shape: 'tbbpm-sub-bpm',
    component: SubBpmNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  bpmCall: {
    shape: 'tbbpm-bpm-call',
    component: BpmCallNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  while: {
    shape: 'tbbpm-while',
    component: WhileNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  foreach: {
    shape: 'tbbpm-foreach',
    component: ForEachNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  continue: {
    shape: 'tbbpm-continue',
    component: ContinueNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  break: {
    shape: 'tbbpm-break',
    component: BreakNode,
    ports: CONNECTION_PORTS.map((id) => ({ id, group: id })),
  },
  note: {
    shape: 'tbbpm-note',
    component: NoteNode,
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
    const size = TBBPM_NODE_SIZES[nodeType as TbbpmNodeType]
    register({
      shape: config.shape,
      width: size.width,
      height: size.height,
      component: config.component,
      effect: ['data'],
      ports: {
        groups: {
          top: {
            position: { name: 'top', args: { dy: 6 } },
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
            position: { name: 'bottom', args: { dy: -6 } },
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
            position: { name: 'left', args: { dx: 6 } },
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
            position: { name: 'right', args: { dx: -6 } },
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
  logger.info(`Registered ${TBBPM_NODE_TYPES.length} TBBPM node types to X6`)
}

export function getNodeConfig(nodeType: string) {
  return isTbbpmNodeType(nodeType)
    ? { ...NODE_CONFIGS[nodeType], ...TBBPM_NODE_SIZES[nodeType] }
    : undefined
}

const SHAPE_TO_NODE_TYPE: ReadonlyMap<string, TbbpmNodeType> = new Map(
  TBBPM_NODE_TYPES.map((type) => [NODE_CONFIGS[type].shape, type])
)

export function getNodeTypeByShape(shape: string): TbbpmNodeType | undefined {
  return SHAPE_TO_NODE_TYPE.get(shape)
}
