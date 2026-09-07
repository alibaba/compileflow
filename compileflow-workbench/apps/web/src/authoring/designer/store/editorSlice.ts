import {
  createAsyncThunk,
  createSelector,
  createSlice,
  nanoid,
  PayloadAction,
} from '@reduxjs/toolkit'
import type { StateWithHistory } from 'redux-undo'

import type { StoredProcess } from '../api/processStorageTypes'
import { DEFAULT_BPMN_DEFINITION, DEFAULT_TBBPM_DEFINITION } from '../types'
import type {
  BaseConnection,
  BaseNode,
  BpmnConnection,
  BpmnNode,
  ProcessConnection,
  UnifiedProcessDefinition,
} from '../types/flowDefinition'
import { isNodeAncestor } from '../types/nodeHierarchy'
import type { TbbpmConnection, TbbpmNode } from '../types/tbbpm'
import { isBpmnNode, isTbbpmNode } from '../types/typeGuards'

import { generateCode, generateId } from '@/authoring/designer/identifiers'
import {
  mapDesignerToOperateUpdate,
  mapOperateDefinitionToUnified,
  parseProcessContractTimestamp,
} from '@/authoring/designer/integration/operateProcessBridge'
import { parseBpmnXml } from '@/authoring/designer/serialization/bpmnXmlCodec'
import { generateProcessXml } from '@/authoring/designer/serialization/flowXml'
import { parseTbbpmXml } from '@/authoring/designer/serialization/tbbpmXmlCodec'
import type { ParseWarning } from '@/authoring/designer/serialization/xmlTypes'
import { getProcessByCode, updateProcess as updateOperateProcess } from '@/shared/api/processes'
import type { ProcessModelType, ValidationResult } from '@/shared/contracts'

const IMPORTED_FLOW_NAME_FALLBACK = 'Imported flow'
type ImportXmlPayload = { xml: string; type: ProcessModelType; preferXmlName?: boolean }
type NodeUpdates = Partial<Omit<BaseNode, 'id' | 'type'>>
type ConnectionUpdates = Partial<Omit<BaseConnection, 'id'>>
type ProcessInfoUpdates = Partial<
  Omit<UnifiedProcessDefinition, 'code' | 'connections' | 'id' | 'nodes' | 'type'>
>

interface ParsedProcessXml {
  data: UnifiedProcessDefinition
  warnings: ParseWarning[]
}

async function loadProcessStorage() {
  const { processStorage } = await import('../api/processStorage')
  return processStorage
}

function parseProcessXml(xml: string, type: ProcessModelType): ParsedProcessXml {
  const parseResult = type === 'BPMN' ? parseBpmnXml(xml) : parseTbbpmXml(xml)

  if (!parseResult.success || !parseResult.data) {
    throw new Error(parseResult.error?.message || 'Failed to parse XML')
  }

  return {
    data: parseResult.data,
    warnings: parseResult.warnings ?? [],
  }
}

function resolveImportedProcessName({
  currentProcess,
  parsedProcess,
  preferXmlName,
}: {
  currentProcess: UnifiedProcessDefinition | null
  parsedProcess: UnifiedProcessDefinition
  preferXmlName?: boolean
}) {
  if (preferXmlName) {
    return parsedProcess.name || currentProcess?.name || IMPORTED_FLOW_NAME_FALLBACK
  }
  return currentProcess?.name || parsedProcess.name || IMPORTED_FLOW_NAME_FALLBACK
}

function resolveImportedProcessIdentity({
  currentProcess,
  parsedProcess,
  preferXmlName,
}: {
  currentProcess: UnifiedProcessDefinition | null
  parsedProcess: UnifiedProcessDefinition
  preferXmlName?: boolean
}): Pick<UnifiedProcessDefinition, 'code' | 'id' | 'name' | 'updatedAt'> {
  return {
    id: currentProcess?.id || parsedProcess.id || generateId(),
    code: currentProcess?.code || parsedProcess.code || generateCode(),
    name: resolveImportedProcessName({ currentProcess, parsedProcess, preferXmlName }),
    updatedAt: Date.now(),
  }
}

function inheritedImportedProcessMetadata(
  currentProcess: UnifiedProcessDefinition | null
): Partial<Pick<UnifiedProcessDefinition, 'category' | 'createdAt' | 'tags'>> {
  if (!currentProcess) return {}

  return {
    createdAt: currentProcess.createdAt,
    category: currentProcess.category,
    tags: currentProcess.tags,
  }
}

function mergeImportedProcess<Definition extends UnifiedProcessDefinition>({
  currentProcess,
  parsedProcess,
  preferXmlName,
}: {
  currentProcess: UnifiedProcessDefinition | null
  parsedProcess: Definition
  preferXmlName?: boolean
}): Definition {
  return {
    ...parsedProcess,
    ...inheritedImportedProcessMetadata(currentProcess),
    ...resolveImportedProcessIdentity({
      currentProcess,
      parsedProcess,
      preferXmlName,
    }),
  }
}

export interface EditorState {
  currentProcess: UnifiedProcessDefinition | null
  isModified: boolean
  /** Unique identity of the latest user-visible content mutation. */
  changeToken: string | null
  savedChangeToken: string | null
  isLoading: boolean
  isSaving: boolean
  error: string | null
  warnings: ParseWarning[]
  validationResult: ValidationResult | null
  lastSavedTime: number | null
  /** When set, save persists to the operate flow API instead of IndexedDB. */
  operateBinding: { processCode: string; revision: number } | null
  /** Latest content transition allowed to replace or clear the editor document. */
  activeContentRequestId: string | null
  /** Document identity shared by its edit history, including same-ID reloads. */
  documentRequestId: string | null
  /** Correlates cancellation failures, which have no thunk rejection payload. */
  activeSaveRequestId: string | null
}

interface EditorRootState {
  editor: StateWithHistory<EditorState>
}

// currentProcess starts as null; the UnifiedDesigner skeleton guard (if (!currentProcess)) requires this.
// All reducers that mutate currentProcess already have null-guards.
const initialState: EditorState = {
  currentProcess: null,
  isModified: false,
  changeToken: null,
  savedChangeToken: null,
  isLoading: false,
  isSaving: false,
  error: null,
  warnings: [],
  validationResult: null,
  lastSavedTime: null,
  operateBinding: null,
  activeContentRequestId: null,
  documentRequestId: null,
  activeSaveRequestId: null,
}

interface ChangeTokenMeta {
  changeToken: string
}

type ChangeAction<T> = PayloadAction<T, string, ChangeTokenMeta>

function prepareChange<T>(payload: T): { payload: T; meta: ChangeTokenMeta } {
  return { payload, meta: { changeToken: nanoid() } }
}

function markChanged(state: EditorState, token: string): void {
  state.changeToken = token
  state.isModified = true
}

function clearRemovedBpmnDefaultConnections(
  process: UnifiedProcessDefinition,
  removedConnectionIds: ReadonlySet<string>
): void {
  if (process.type !== 'BPMN' || removedConnectionIds.size === 0) return
  process.nodes.forEach((node) => {
    if (node.properties.default && removedConnectionIds.has(node.properties.default)) {
      delete node.properties.default
    }
  })
}

function resetDocumentTracking(state: EditorState, requestId: string): void {
  state.documentRequestId = requestId
  state.activeSaveRequestId = null
  state.isSaving = false
  state.isModified = false
  state.changeToken = null
  state.savedChangeToken = null
}

export const createProcess = createAsyncThunk(
  'editor/createProcess',
  async (
    {
      type,
      name,
      definition: initialDefinition,
    }: {
      type: ProcessModelType
      name?: string
      definition?: string
    },
    { signal }
  ) => {
    const id = generateId()
    const code = generateCode()
    const definition =
      initialDefinition ?? (type === 'BPMN' ? DEFAULT_BPMN_DEFINITION : DEFAULT_TBBPM_DEFINITION)
    const parsedProcess = parseProcessXml(definition, type).data

    const baseName = name || `New ${type} Process`
    const processStorage = await loadProcessStorage()
    signal.throwIfAborted()
    const availableName = await processStorage.suggestAvailableProcessName(baseName, type)
    signal.throwIfAborted()
    const now = Date.now()

    const unified: UnifiedProcessDefinition = {
      ...parsedProcess,
      id,
      code,
      name: availableName,
      createdAt: now,
      updatedAt: now,
    }
    const flow: StoredProcess = {
      id,
      code,
      name: availableName,
      type,
      definition: generateProcessXml(unified),
      createdAt: now,
      updatedAt: now,
    }

    await processStorage.saveProcess(flow)
    signal.throwIfAborted()

    return unified
  }
)

// Clear the undo/redo stack after loadProcess so Ctrl+Z does not replay history from a previous flow.
export const loadProcess = createAsyncThunk(
  'editor/loadProcess',
  async (id: string, { signal }) => {
    const processStorage = await loadProcessStorage()
    signal.throwIfAborted()
    const flow = await processStorage.loadProcess(id)
    signal.throwIfAborted()
    if (!flow) {
      throw new Error(`Process not found: ${id}`)
    }

    const parseResult =
      flow.type === 'BPMN'
        ? parseBpmnXml(flow.definition || '')
        : parseTbbpmXml(flow.definition || '')

    if (!parseResult.success || !parseResult.data) {
      throw new Error(parseResult.error?.message || 'Failed to parse flow definition')
    }

    const unified: UnifiedProcessDefinition = {
      ...parseResult.data,
      id: flow.id,
      code: flow.code,
      name: flow.name,
      description: flow.description,
      createdAt: flow.createdAt,
      updatedAt: flow.updatedAt,
      category: flow.category,
      tags: flow.tags,
    }

    return {
      flow: unified,
      warnings: parseResult.warnings ?? [],
    }
  }
)

export const loadOperateProcess = createAsyncThunk(
  'editor/loadOperateProcess',
  async (processCode: string, { signal }) => {
    const remote = await getProcessByCode(processCode, signal)
    const { flow, warnings } = mapOperateDefinitionToUnified(remote)

    return {
      flow,
      operateProcessCode: remote.code,
      revision: remote.revision,
      warnings,
    }
  }
)

// The thunk condition prevents a second save while one is already in progress.
export const saveProcess = createAsyncThunk<
  {
    id: string
    updatedAt: number
    operateRevision: number | null
    savedChangeToken: string | null
    documentRequestId: string | null
  },
  { createSnapshot?: boolean } | undefined,
  { rejectValue: { message: string; documentRequestId: string | null } }
>(
  'editor/saveProcess',
  async (options, { getState, rejectWithValue }) => {
    const state = getState() as EditorRootState
    const editorState = getEditorState(state)
    const currentProcess = editorState.currentProcess
    const savedChangeToken = editorState.changeToken
    const documentRequestId = editorState.documentRequestId

    try {
      if (!currentProcess) {
        throw new Error('No flow to save')
      }

      const xml = generateProcessXml(currentProcess)

      if (editorState.operateBinding) {
        const updated = await updateOperateProcess(
          editorState.operateBinding.processCode,
          mapDesignerToOperateUpdate(currentProcess, xml, editorState.operateBinding.revision)
        )
        return {
          id: currentProcess.id,
          updatedAt: parseProcessContractTimestamp(updated.updatedAt, 'updatedAt'),
          operateRevision: updated.revision,
          savedChangeToken,
          documentRequestId,
        }
      }

      const processStorage = await loadProcessStorage()

      // Generate the timestamp once and thread it through to avoid millisecond-level skew
      // between the stored record and the Redux state.
      const savedAt = Date.now()
      const flow: StoredProcess = {
        id: currentProcess.id,
        code: currentProcess.code,
        name: currentProcess.name,
        type: currentProcess.type,
        definition: xml,
        description: currentProcess.description,
        createdAt: currentProcess.createdAt || savedAt,
        updatedAt: savedAt,
        category: currentProcess.category,
        tags: currentProcess.tags,
      }

      const snapshot = options?.createSnapshot
        ? {
            id: generateId(),
            createdAt: savedAt,
          }
        : undefined
      const savedProcess = await processStorage.saveProcess(flow, snapshot)

      return {
        id: currentProcess.id,
        updatedAt: savedProcess.updatedAt,
        operateRevision: null,
        savedChangeToken,
        documentRequestId,
      }
    } catch (error) {
      return rejectWithValue({
        message: error instanceof Error ? error.message : String(error),
        documentRequestId,
      })
    }
  },
  {
    condition: (_, { getState }) => {
      // Skip if a save is already in-flight; the ongoing thunk will settle first.
      return !getEditorState(getState() as EditorRootState).isSaving
    },
  }
)

export const deleteProcess = createAsyncThunk('editor/deleteProcess', async (id: string) => {
  const processStorage = await loadProcessStorage()
  await processStorage.deleteProcess(id)
  return id
})

export const importXml = createAsyncThunk(
  'editor/importXml',
  async ({ xml, type, preferXmlName }: ImportXmlPayload, { getState }) => {
    const parseResult = parseProcessXml(xml, type)
    const state = getState() as EditorRootState
    const currentProcess = getEditorState(state).currentProcess

    return {
      flow: mergeImportedProcess({
        currentProcess,
        parsedProcess: parseResult.data,
        preferXmlName,
      }),
      warnings: parseResult.warnings,
    }
  }
)

const editorSlice = createSlice({
  name: 'editor',
  initialState,
  reducers: {
    addGraph: {
      reducer(
        state,
        action: ChangeAction<{
          nodes: BaseNode[]
          connections: BaseConnection[]
          messages: NonNullable<UnifiedProcessDefinition['messages']>
        }>
      ) {
        if (!state.currentProcess || action.payload.nodes.length === 0) return
        const { nodes, connections, messages } = action.payload
        if (state.currentProcess.type === 'BPMN' && nodes.every(isBpmnNode)) {
          state.currentProcess.nodes.push(...nodes)
          if (messages.length) {
            state.currentProcess.messages = [...(state.currentProcess.messages ?? []), ...messages]
          }
        } else if (state.currentProcess.type === 'TBBPM' && nodes.every(isTbbpmNode)) {
          state.currentProcess.nodes.push(...nodes)
        } else {
          throw new Error(`Cannot add graph to a ${state.currentProcess.type} process`)
        }
        state.currentProcess.connections.push(...connections)
        markChanged(state, action.meta.changeToken)
      },
      prepare: prepareChange,
    },
    addNode: {
      reducer(state, action: ChangeAction<TbbpmNode | BpmnNode>) {
        if (!state.currentProcess) return
        if (state.currentProcess.type === 'BPMN' && isBpmnNode(action.payload)) {
          state.currentProcess.nodes.push(action.payload)
        } else if (state.currentProcess.type === 'TBBPM' && isTbbpmNode(action.payload)) {
          state.currentProcess.nodes.push(action.payload)
        } else {
          throw new Error(
            `Cannot add ${action.payload.type} to a ${state.currentProcess.type} process`
          )
        }
        markChanged(state, action.meta.changeToken)
      },
      prepare: prepareChange,
    },

    updateNode: {
      reducer(state, action: ChangeAction<{ id: string; updates: NodeUpdates }>) {
        if (!state.currentProcess) return
        const { id, updates } = action.payload
        const node = state.currentProcess.nodes.find((candidate) => candidate.id === id)
        if (node) {
          Object.assign(node, updates)
          markChanged(state, action.meta.changeToken)
        }
      },
      prepare: prepareChange,
    },

    replaceContainerChildren: {
      reducer(state, action: ChangeAction<{ parentId: string; childIds: string[] }>) {
        if (!state.currentProcess) return
        const { parentId, childIds } = action.payload
        const nodesById = new Map(state.currentProcess.nodes.map((node) => [node.id, node]))
        const parent = nodesById.get(parentId)
        if (
          !parent ||
          childIds.some(
            (childId) => childId === parentId || isNodeAncestor(childId, parent, nodesById)
          )
        ) {
          return
        }
        const selected = new Set(childIds)
        state.currentProcess.nodes.forEach((node) => {
          if (selected.has(node.id)) {
            node.parentId = parentId
          } else if (node.parentId === parentId) {
            node.parentId = parent.parentId
          }
        })
        markChanged(state, action.meta.changeToken)
      },
      prepare: prepareChange,
    },

    moveNode: {
      reducer(state, action: ChangeAction<{ id: string; x: number; y: number }>) {
        if (!state.currentProcess) return
        const { id, x, y } = action.payload
        const node = state.currentProcess.nodes.find((n) => n.id === id)
        if (node) {
          const dx = x - node.position.x
          const dy = y - node.position.y
          node.position = { x, y }
          if (dx !== 0 || dy !== 0) {
            const movedIds = new Set([id])
            let foundDescendant = true
            while (foundDescendant) {
              foundDescendant = false
              state.currentProcess.nodes.forEach((candidate) => {
                if (
                  candidate.parentId &&
                  movedIds.has(candidate.parentId) &&
                  !movedIds.has(candidate.id)
                ) {
                  movedIds.add(candidate.id)
                  candidate.position = {
                    x: candidate.position.x + dx,
                    y: candidate.position.y + dy,
                  }
                  foundDescendant = true
                }
              })
            }
          }
          markChanged(state, action.meta.changeToken)
        }
      },
      prepare: prepareChange,
    },

    deleteNode: {
      reducer(state, action: ChangeAction<string>) {
        if (!state.currentProcess) return
        const nodeId = action.payload
        const deletedIds = new Set([nodeId])
        let foundDescendant = true
        while (foundDescendant) {
          foundDescendant = false
          state.currentProcess.nodes.forEach((node) => {
            if (node.parentId && deletedIds.has(node.parentId) && !deletedIds.has(node.id)) {
              deletedIds.add(node.id)
              foundDescendant = true
            }
          })
        }
        if (state.currentProcess.type === 'BPMN') {
          const removedConnectionIds = new Set(
            state.currentProcess.connections
              .filter(
                (connection) =>
                  deletedIds.has(connection.sourceId) || deletedIds.has(connection.targetId)
              )
              .map((connection) => connection.id)
          )
          state.currentProcess.nodes = state.currentProcess.nodes.filter(
            (node) => !deletedIds.has(node.id)
          )
          state.currentProcess.connections = state.currentProcess.connections.filter(
            (connection) =>
              !deletedIds.has(connection.sourceId) && !deletedIds.has(connection.targetId)
          )
          clearRemovedBpmnDefaultConnections(state.currentProcess, removedConnectionIds)
        } else {
          state.currentProcess.nodes = state.currentProcess.nodes.filter(
            (node) => !deletedIds.has(node.id)
          )
          state.currentProcess.connections = state.currentProcess.connections.filter(
            (connection) =>
              !deletedIds.has(connection.sourceId) && !deletedIds.has(connection.targetId)
          )
        }
        markChanged(state, action.meta.changeToken)
      },
      prepare: prepareChange,
    },

    addConnection: {
      reducer(state, action: ChangeAction<ProcessConnection>) {
        if (!state.currentProcess) return
        state.currentProcess.connections.push(action.payload)
        markChanged(state, action.meta.changeToken)
      },
      prepare: prepareChange,
    },

    updateConnection: {
      reducer(state, action: ChangeAction<{ id: string; updates: ConnectionUpdates }>) {
        if (!state.currentProcess) return
        const { id, updates } = action.payload
        const connection = state.currentProcess.connections.find((candidate) => candidate.id === id)
        if (connection) {
          Object.assign(connection, updates)
          markChanged(state, action.meta.changeToken)
        }
      },
      prepare: prepareChange,
    },

    deleteConnection: {
      reducer(state, action: ChangeAction<string>) {
        if (!state.currentProcess) return
        state.currentProcess.connections = state.currentProcess.connections.filter(
          (c) => c.id !== action.payload
        )
        clearRemovedBpmnDefaultConnections(state.currentProcess, new Set([action.payload]))
        markChanged(state, action.meta.changeToken)
      },
      prepare: prepareChange,
    },

    updateProcessInfo: {
      reducer(state, action: ChangeAction<ProcessInfoUpdates>) {
        if (state.currentProcess) {
          // Use Object.assign for precise Immer tracking instead of spread-replace.
          // A spread creates a new currentProcess object on every call — even renaming one
          // field invalidates all selectCurrentProcess-derived selectors. Object.assign
          // lets Immer track only the touched keys, so unchanged selectors stay stable.
          Object.assign(state.currentProcess, action.payload)
          markChanged(state, action.meta.changeToken)
        }
      },
      prepare: prepareChange,
    },

    clearWarnings(state) {
      state.warnings = []
    },
  },
  extraReducers: (builder) => {
    builder.addCase(createProcess.pending, (state, action) => {
      state.isLoading = true
      state.error = null
      state.activeContentRequestId = action.meta.requestId
    })
    builder.addCase(createProcess.fulfilled, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.activeContentRequestId = null
      state.isLoading = false
      state.currentProcess = action.payload
      resetDocumentTracking(state, action.meta.requestId)
      state.warnings = []
      state.operateBinding = null
    })
    builder.addCase(createProcess.rejected, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.activeContentRequestId = null
      state.isLoading = false
      state.error = action.error.message || 'Failed to create flow'
    })

    builder.addCase(loadProcess.pending, (state, action) => {
      state.isLoading = true
      state.error = null
      state.activeContentRequestId = action.meta.requestId
    })
    builder.addCase(loadProcess.fulfilled, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.isLoading = false
      state.activeContentRequestId = null
      state.currentProcess = action.payload.flow
      state.warnings = action.payload.warnings
      state.operateBinding = null
      resetDocumentTracking(state, action.meta.requestId)
    })
    builder.addCase(loadProcess.rejected, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.isLoading = false
      state.activeContentRequestId = null
      state.error = action.error.message || 'Failed to load flow'
    })

    builder.addCase(saveProcess.pending, (state, action) => {
      state.isSaving = true
      state.activeSaveRequestId = action.meta.requestId
      state.error = null
    })
    builder.addCase(saveProcess.fulfilled, (state, action) => {
      if (state.documentRequestId !== action.payload.documentRequestId) return
      state.isSaving = false
      state.activeSaveRequestId = null
      // Only clear isModified when the saved flow is still the active flow.
      if (state.currentProcess && state.currentProcess.id === action.payload.id) {
        state.currentProcess.updatedAt = action.payload.updatedAt
        if (state.operateBinding && action.payload.operateRevision !== null) {
          state.operateBinding.revision = action.payload.operateRevision
        }
        state.isModified = state.changeToken !== action.payload.savedChangeToken
        state.savedChangeToken = action.payload.savedChangeToken
        state.lastSavedTime = Date.now()
      }
    })
    builder.addCase(saveProcess.rejected, (state, action) => {
      if (action.payload) {
        if (state.documentRequestId !== action.payload.documentRequestId) return
      } else if (state.activeSaveRequestId !== action.meta.requestId) {
        return
      }
      state.isSaving = false
      state.activeSaveRequestId = null
      state.error = action.payload?.message || action.error.message || 'Failed to save flow'
    })

    builder.addCase(deleteProcess.pending, (state, action) => {
      // Show loading state while delete is in progress; prevents double-delete race.
      state.isLoading = true
      state.error = null
      state.activeContentRequestId = action.meta.requestId
    })
    builder.addCase(deleteProcess.fulfilled, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.activeContentRequestId = null
      state.isLoading = false
      state.currentProcess = null
      resetDocumentTracking(state, action.meta.requestId)
      state.operateBinding = null
    })
    // Surface deleteProcess rejection in Redux state so the UI can show an error.
    builder.addCase(deleteProcess.rejected, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.activeContentRequestId = null
      state.isLoading = false
      state.error = action.error.message || 'Failed to delete flow'
    })

    builder.addCase(loadOperateProcess.pending, (state, action) => {
      state.isLoading = true
      state.error = null
      state.activeContentRequestId = action.meta.requestId
    })
    builder.addCase(loadOperateProcess.fulfilled, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.isLoading = false
      state.activeContentRequestId = null
      state.currentProcess = action.payload.flow
      state.warnings = action.payload.warnings
      state.operateBinding = {
        processCode: action.payload.operateProcessCode,
        revision: action.payload.revision,
      }
      resetDocumentTracking(state, action.meta.requestId)
    })
    builder.addCase(loadOperateProcess.rejected, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.isLoading = false
      state.activeContentRequestId = null
      state.error = action.error.message || 'Failed to load operate flow'
    })

    builder.addCase(importXml.pending, (state, action) => {
      state.isLoading = true
      state.error = null
      state.activeContentRequestId = action.meta.requestId
    })
    builder.addCase(importXml.fulfilled, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.activeContentRequestId = null
      state.isLoading = false
      state.currentProcess = action.payload.flow
      state.warnings = action.payload.warnings
      state.isModified = true
      state.changeToken = action.meta.requestId
    })
    builder.addCase(importXml.rejected, (state, action) => {
      if (state.activeContentRequestId !== action.meta.requestId) return
      state.activeContentRequestId = null
      state.isLoading = false
      state.error = action.error.message || 'Failed to import XML'
    })
  },
})

export const {
  addGraph,
  addNode,
  updateNode,
  replaceContainerChildren,
  moveNode,
  deleteNode,
  addConnection,
  updateConnection,
  deleteConnection,
  updateProcessInfo,
  clearWarnings,
} = editorSlice.actions

export default editorSlice.reducer

const getEditorState = (state: EditorRootState): EditorState => state.editor.present

export const selectCurrentProcess = (state: EditorRootState) => getEditorState(state).currentProcess

// createSelector stabilises empty-state fallbacks ([] and {}) to prevent unnecessary re-renders.
export const selectNodes = createSelector([selectCurrentProcess], (flow) => flow?.nodes ?? [])
export const selectBpmnNodes = createSelector([selectCurrentProcess], (flow): BpmnNode[] =>
  flow?.type === 'BPMN' ? flow.nodes : []
)
export const selectBpmnConnections = createSelector(
  [selectCurrentProcess],
  (flow): BpmnConnection[] => (flow?.type === 'BPMN' ? flow.connections : [])
)
export const selectTbbpmNodes = createSelector([selectCurrentProcess], (flow): TbbpmNode[] =>
  flow?.type === 'TBBPM' ? flow.nodes : []
)
export const selectTbbpmConnections = createSelector(
  [selectCurrentProcess],
  (flow): TbbpmConnection[] => (flow?.type === 'TBBPM' ? flow.connections : [])
)
export const selectIsModified = (state: EditorRootState) => getEditorState(state).isModified
export const selectIsSaving = (state: EditorRootState) => getEditorState(state).isSaving
export const selectWarnings = (state: EditorRootState) => getEditorState(state).warnings
export const selectOperateBinding = (state: EditorRootState) => getEditorState(state).operateBinding

export const selectCanUndo = (state: EditorRootState) =>
  state.editor.past && state.editor.past.length > 0
export const selectCanRedo = (state: EditorRootState) =>
  state.editor.future && state.editor.future.length > 0
