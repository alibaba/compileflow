import type { Reducer } from '@reduxjs/toolkit'
import { configureStore, createSlice, PayloadAction } from '@reduxjs/toolkit'
import type { StateWithHistory } from 'redux-undo'
import undoable, { ActionCreators, ActionTypes } from 'redux-undo'

import editorReducer, {
  type EditorState,
  addConnection,
  addGraph,
  addNode,
  deleteConnection,
  deleteGraph,
  deleteNode,
  moveNode,
  replaceImportedProcess,
  replaceContainerChildren,
  setBpmnDefaultConnection,
  updateConnection,
  updateProcessInfo,
  updateNode,
} from '@/authoring/designer/store/editorSlice'
import uiReducer from '@/authoring/designer/store/uiSlice'
import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import { LIMITS } from '@/shared/constants'
import type { BreadcrumbItem } from '@/shared/navigation/breadcrumbs'

interface NavigationState {
  breadcrumbs: BreadcrumbItem[]
}

const navigationSlice = createSlice({
  name: 'navigation',
  initialState: {
    breadcrumbs: [],
  } as NavigationState,
  reducers: {
    setBreadcrumbs: (state, action: PayloadAction<BreadcrumbItem[]>) => {
      state.breadcrumbs = action.payload
    },
  },
})

const editorHistory = undoable(editorReducer, {
  limit: LIMITS.MAX_UNDO_HISTORY,
  filter: (action) => {
    // Process metadata edits belong to the same undo history as graph edits.
    // stack, consistent with node and connection mutations.
    const undoableActions = new Set<string>([
      addGraph.type,
      addNode.type,
      updateNode.type,
      replaceContainerChildren.type,
      setBpmnDefaultConnection.type,
      updateProcessInfo.type,
      replaceImportedProcess.type,
      moveNode.type,
      deleteNode.type,
      deleteGraph.type,
      addConnection.type,
      updateConnection.type,
      deleteConnection.type,
    ])
    return undoableActions.has(action.type)
  },
  groupBy: (action) => {
    if (action.type === 'editor/moveNode') {
      const moveAction = action as PayloadAction<
        { id: string; x: number; y: number },
        string,
        { historyGroup?: string }
      >
      return moveAction.meta.historyGroup ?? null
    }
    return null
  },
}) satisfies Reducer<StateWithHistory<import('@/authoring/designer/store/editorSlice').EditorState>>

const historyActions = new Set<string>([
  ActionTypes.UNDO,
  ActionTypes.REDO,
  ActionTypes.JUMP,
  ActionTypes.JUMP_TO_PAST,
  ActionTypes.JUMP_TO_FUTURE,
])
const editorWithHistory: Reducer<StateWithHistory<EditorState>> = (state, action) => {
  const next = editorHistory(state, action)
  if (!state || !historyActions.has(action.type) || next.present === state.present) return next
  // History owns content, never persistence revisions or active request identities.
  if (next.present.documentRequestId !== state.present.documentRequestId) return state
  const present = {
    ...state.present,
    currentProcess: next.present.currentProcess
      ? { ...next.present.currentProcess, updatedAt: state.present.currentProcess?.updatedAt }
      : null,
    changeToken: next.present.changeToken,
    warnings: next.present.warnings,
    validationResult: null,
    isModified: next.present.changeToken !== state.present.savedChangeToken,
  }
  return { ...next, present, _latestUnfiltered: present }
}

export const store = configureStore({
  reducer: {
    navigation: navigationSlice.reducer,
    editor: editorWithHistory,
    ui: uiReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredPaths: [
          'editor.present.validationResult',
          'editor.past', // Ignore the entire past array (all history entries)
          'editor.future', // Ignore the entire future array (all redo entries)
        ],
      },
    }),
})

export const { setBreadcrumbs } = navigationSlice.actions

export { ActionCreators as UndoActionCreators }

declare global {
  interface Window {
    __COMPILEFLOW_DEBUG__?: unknown
  }
}

if (
  typeof window !== 'undefined' &&
  APP_BUILD_CONFIG.buildMode === 'development' &&
  APP_BUILD_CONFIG.enableDebug
) {
  window.__COMPILEFLOW_DEBUG__ = {
    store,
    getState: () => store.getState(),
    dispatch: (action: Parameters<typeof store.dispatch>[0]) => store.dispatch(action),
  }
}

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
