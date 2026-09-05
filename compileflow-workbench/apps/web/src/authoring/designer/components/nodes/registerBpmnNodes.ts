import { register } from '@antv/x6-react-shape'

import { CallActivityNode } from './CallActivityNode'
import { EndEventNode } from './EndEventNode'
import { ExclusiveGatewayNode } from './ExclusiveGatewayNode'
import { InclusiveGatewayNode } from './InclusiveGatewayNode'
import { ParallelGatewayNode } from './ParallelGatewayNode'
import { ReceiveTaskNode } from './ReceiveTaskNode'
import { ScriptTaskNode } from './ScriptTaskNode'
import { ServiceTaskNode } from './ServiceTaskNode'
import { StartEventNode } from './StartEventNode'
import { SubProcessNode } from './SubProcessNode'

import {
  BPMN_NODE_TYPES,
  isBpmnNodeType,
  type BpmnNodeType,
} from '@/authoring/designer/types/bpmnNodeTypes'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('RegisterBpmnNodes')

const BPMN_NODE_CONFIGS = {
  // ==================== Events ====================
  'bpmn:StartEvent': {
    shape: 'bpmn-start-event',
    component: StartEventNode,
    width: 36,
    height: 36,
    ports: [
      // StartEvent只有出口
      { id: 'out', group: 'out' },
    ],
  },
  'bpmn:EndEvent': {
    shape: 'bpmn-end-event',
    component: EndEventNode,
    width: 36,
    height: 36,
    ports: [
      // EndEvent只有入口
      { id: 'in', group: 'in' },
    ],
  },

  // ==================== Tasks ====================
  'bpmn:ServiceTask': {
    shape: 'bpmn-service-task',
    component: ServiceTaskNode,
    width: 100,
    height: 80,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  'bpmn:ScriptTask': {
    shape: 'bpmn-script-task',
    component: ScriptTaskNode,
    width: 100,
    height: 80,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  'bpmn:ReceiveTask': {
    shape: 'bpmn-receive-task',
    component: ReceiveTaskNode,
    width: 100,
    height: 80,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },

  // ==================== Gateways ====================
  'bpmn:ExclusiveGateway': {
    shape: 'bpmn-exclusive-gateway',
    component: ExclusiveGatewayNode,
    width: 50,
    height: 50,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  'bpmn:ParallelGateway': {
    shape: 'bpmn-parallel-gateway',
    component: ParallelGatewayNode,
    width: 50,
    height: 50,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  'bpmn:InclusiveGateway': {
    shape: 'bpmn-inclusive-gateway',
    component: InclusiveGatewayNode,
    width: 50,
    height: 50,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },

  // ==================== Subprocesses ====================
  'bpmn:CallActivity': {
    shape: 'bpmn-call-activity',
    component: CallActivityNode,
    width: 140,
    height: 100,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
  'bpmn:SubProcess': {
    shape: 'bpmn-sub-process',
    component: SubProcessNode,
    width: 320,
    height: 220,
    ports: [
      { id: 'top', group: 'top' },
      { id: 'bottom', group: 'bottom' },
      { id: 'left', group: 'left' },
      { id: 'right', group: 'right' },
    ],
  },
} as const

let isRegistered = false

export function registerBpmnNodes() {
  if (isRegistered) {
    logger.debug('BPMN nodes already registered, skipping')
    return
  }

  logger.info('Registering BPMN nodes...')

  Object.entries(BPMN_NODE_CONFIGS).forEach(([, config]) => {
    try {
      register({
        shape: config.shape,
        width: config.width,
        height: config.height,
        component: config.component,
        ports: {
          groups: {
            top: {
              position: 'top',
              attrs: {
                circle: {
                  r: 4,
                  magnet: true,
                  stroke: '#31d0c6',
                  strokeWidth: 2,
                  fill: '#fff',
                },
              },
            },
            bottom: {
              position: 'bottom',
              attrs: {
                circle: {
                  r: 4,
                  magnet: true,
                  stroke: '#31d0c6',
                  strokeWidth: 2,
                  fill: '#fff',
                },
              },
            },
            left: {
              position: 'left',
              attrs: {
                circle: {
                  r: 4,
                  magnet: true,
                  stroke: '#31d0c6',
                  strokeWidth: 2,
                  fill: '#fff',
                },
              },
            },
            right: {
              position: 'right',
              attrs: {
                circle: {
                  r: 4,
                  magnet: true,
                  stroke: '#31d0c6',
                  strokeWidth: 2,
                  fill: '#fff',
                },
              },
            },
            in: {
              position: 'left',
              attrs: {
                circle: {
                  r: 4,
                  magnet: true,
                  stroke: '#31d0c6',
                  strokeWidth: 2,
                  fill: '#fff',
                },
              },
            },
            out: {
              position: 'right',
              attrs: {
                circle: {
                  r: 4,
                  magnet: true,
                  stroke: '#31d0c6',
                  strokeWidth: 2,
                  fill: '#fff',
                },
              },
            },
          },
          items: config.ports,
        },
      })

      logger.debug(`Registered BPMN node: ${config.shape}`)
    } catch (error) {
      logger.error(`Failed to register ${config.shape}:`, toError(error))
    }
  })

  isRegistered = true
  logger.info('All BPMN nodes registered successfully')
}

export function getBpmnNodeConfig(nodeType: string) {
  return isBpmnNodeType(nodeType) ? BPMN_NODE_CONFIGS[nodeType] : undefined
}

const SHAPE_TO_BPMN_TYPE: ReadonlyMap<string, BpmnNodeType> = new Map(
  BPMN_NODE_TYPES.map((type) => [BPMN_NODE_CONFIGS[type].shape, type])
)

export function getBpmnTypeByShape(shape: string): BpmnNodeType | undefined {
  return SHAPE_TO_BPMN_TYPE.get(shape)
}
