import { z } from 'zod'

import { MAX_LOOP_ITERATIONS } from '../types/loopLimits'

// ==================== 基础Schema ====================

const PositionSchema = z.object({
  x: z.number().finite(),
  y: z.number().finite(),
})

const SizeSchema = z.object({
  width: z.number().positive(),
  height: z.number().positive(),
})

const MetadataSchema = z
  .object({
    editable: z.boolean().optional(),
    deletable: z.boolean().optional(),
    style: z.record(z.unknown()).optional(),
    uiState: z.record(z.unknown()).optional(),
  })
  .optional()

// ==================== TBBPM节点Schema ====================

const BaseNodeShape = {
  id: z.string().min(1),
  parentId: z.string().min(1).optional(),
  name: z.string().optional(),
  position: PositionSchema,
  size: SizeSchema.optional(),
  metadata: MetadataSchema,
}

const ActionExecutionSchema = z.enum(['replayable', 'effect'])
const InvocationPolicySchema = z
  .object({
    timeout: z.string().optional(),
    attemptTimeout: z.string().optional(),
    maxAttempts: z.number().optional(),
    initialBackoff: z.string().optional(),
    backoffMultiplier: z.number().optional(),
    maxBackoff: z.string().optional(),
    jitter: z.enum(['none', 'full']).optional(),
    retryOn: z.string().optional(),
    onFailure: z.string().optional(),
  })
  .strict()

const ExactNonBlankStringSchema = z
  .string()
  .min(1)
  .refine((value) => value === value.trim())
const NonBlankStringSchema = z.string().refine((value) => value.trim().length > 0)

const VariableMappingSchema = z.discriminatedUnion('direction', [
  z
    .object({
      direction: z.literal('input'),
      target: ExactNonBlankStringSchema,
      dataType: ExactNonBlankStringSchema.optional(),
      source: NonBlankStringSchema.optional(),
      defaultValue: z.string().optional(),
    })
    .strict(),
  z
    .object({
      direction: z.literal('output'),
      source: ExactNonBlankStringSchema.optional(),
      target: ExactNonBlankStringSchema,
      dataType: ExactNonBlankStringSchema.optional(),
    })
    .strict(),
])

const ActionCommonShape = {
  execution: ActionExecutionSchema.optional(),
  mappings: z.array(VariableMappingSchema).optional(),
  invocationPolicy: InvocationPolicySchema.optional(),
}

const ActionDefinitionSchema = z.discriminatedUnion('actionType', [
  z
    .object({
      actionType: z.literal('java'),
      ...ActionCommonShape,
      className: z.string().optional(),
      method: z.string().optional(),
    })
    .strict(),
  z
    .object({
      actionType: z.literal('spring-bean'),
      ...ActionCommonShape,
      bean: z.string().optional(),
      className: z.string().optional(),
      method: z.string().optional(),
    })
    .strict(),
  z
    .object({
      actionType: z.literal('script'),
      ...ActionCommonShape,
      language: z.string().optional(),
      source: z.string().optional(),
    })
    .strict(),
])

const StartNodeSchema = z.object({
  type: z.literal('start'),
  ...BaseNodeShape,
  properties: z.object({}).strict(),
})

const EndNodeSchema = z.object({
  type: z.literal('end'),
  ...BaseNodeShape,
  properties: z.object({}).strict(),
})

const AutoTaskNodeSchema = z.object({
  type: z.literal('autoTask'),
  ...BaseNodeShape,
  properties: z
    .object({
      action: ActionDefinitionSchema.optional(),
    })
    .strict(),
})

const ScriptTaskNodeSchema = z.object({
  type: z.literal('scriptTask'),
  ...BaseNodeShape,
  properties: z
    .object({
      action: ActionDefinitionSchema.optional(),
    })
    .strict(),
})

const ExclusiveNodeSchema = z.object({
  type: z.literal('exclusive'),
  ...BaseNodeShape,
  properties: z.object({}).strict(),
})

const ParallelNodeSchema = z.object({
  type: z.literal('parallel'),
  ...BaseNodeShape,
  properties: z.object({}).strict(),
})

const InclusiveNodeSchema = z.object({
  type: z.literal('inclusive'),
  ...BaseNodeShape,
  properties: z.object({}).strict(),
})

const SubBpmNodeSchema = z.object({
  type: z.literal('subBpm'),
  ...BaseNodeShape,
  properties: z.object({}).strict(),
})

const BpmCallNodeSchema = z.object({
  type: z.literal('bpmCall'),
  ...BaseNodeShape,
  properties: z
    .object({
      code: z.string().optional(),
      classpath: z.string().optional(),
      version: z.string().optional(),
      callMappings: z.array(VariableMappingSchema).optional(),
    })
    .strict(),
})

const WhileNodeSchema = z.object({
  type: z.literal('while'),
  ...BaseNodeShape,
  properties: z
    .object({
      condition: z.string().optional(),
      index: z.string().optional(),
      maxIterations: z.number().int().positive().max(MAX_LOOP_ITERATIONS).optional(),
    })
    .strict(),
})

const ForEachNodeSchema = z.object({
  type: z.literal('foreach'),
  ...BaseNodeShape,
  properties: z
    .object({
      execution: z.enum(['sequential', 'parallel']).optional(),
      collection: z.string().optional(),
      item: z.string().optional(),
      index: z.string().optional(),
      itemType: z.string().optional(),
      output: z.object({ target: z.string(), source: z.string() }).strict().optional(),
    })
    .strict(),
})

const WaitTaskNodeSchema = z.object({
  type: z.literal('waitTask'),
  ...BaseNodeShape,
  properties: z.object({ timeout: z.string().optional() }).strict(),
})

const WaitEventTaskNodeSchema = z.object({
  type: z.literal('waitEventTask'),
  ...BaseNodeShape,
  properties: z
    .object({
      event: z.string().optional(),
      timeout: z.string().optional(),
    })
    .strict(),
})

const TimerTaskNodeSchema = z.object({
  type: z.literal('timerTask'),
  ...BaseNodeShape,
  properties: z
    .object({
      duration: z.string().optional(),
      durationExpression: z.string().optional(),
      wakeAtExpression: z.string().optional(),
    })
    .strict(),
})

const ContinueNodeSchema = z.object({
  type: z.literal('continue'),
  ...BaseNodeShape,
  properties: z
    .object({
      condition: z.string().optional(),
    })
    .strict(),
})

const BreakNodeSchema = z.object({
  type: z.literal('break'),
  ...BaseNodeShape,
  properties: z
    .object({
      condition: z.string().optional(),
    })
    .strict(),
})

const NoteNodeSchema = z.object({
  type: z.literal('note'),
  ...BaseNodeShape,
  properties: z
    .object({
      comment: z.string().optional(),
    })
    .strict(),
})

// ==================== Discriminated Union ====================

const TbbpmNodeSchema = z.discriminatedUnion('type', [
  StartNodeSchema,
  EndNodeSchema,
  AutoTaskNodeSchema,
  ScriptTaskNodeSchema,
  ExclusiveNodeSchema,
  ParallelNodeSchema,
  InclusiveNodeSchema,
  SubBpmNodeSchema,
  BpmCallNodeSchema,
  WhileNodeSchema,
  ForEachNodeSchema,
  WaitTaskNodeSchema,
  WaitEventTaskNodeSchema,
  TimerTaskNodeSchema,
  ContinueNodeSchema,
  BreakNodeSchema,
  NoteNodeSchema,
])

// ==================== 类型导出 ====================

export type TbbpmNodeInput = z.input<typeof TbbpmNodeSchema>
export type TbbpmNodeOutput = z.output<typeof TbbpmNodeSchema>

// ==================== 验证函数 ====================

export function safeParseTbbpmNode(
  data: unknown
): z.SafeParseReturnType<TbbpmNodeInput, TbbpmNodeOutput> {
  return TbbpmNodeSchema.safeParse(data)
}
