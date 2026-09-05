import { createSlice, PayloadAction } from '@reduxjs/toolkit'

import type { DesignerViewMode } from '../types'
import type { ProcessConnection } from '../types/flowDefinition'

export interface UiState {
  selectedNodeId: string | null
  selectedEdge: ProcessConnection | null

  rightPanelTab: 'properties' | 'edge' | 'validation' | 'debug' | 'none'
  leftPanelCollapsed: boolean
  rightPanelCollapsed: boolean

  showShortcuts: boolean
  showHelpDocs: boolean
  showSearch: boolean
  showGridlines: boolean

  contextMenu: {
    visible: boolean
    position: { x: number; y: number } | null
    type: 'canvas' | 'node' | 'edge'
    targetId?: string
  }

  viewMode: DesignerViewMode
}

interface UiRootState {
  ui: UiState
}

const initialState: UiState = {
  selectedNodeId: null,
  selectedEdge: null,
  rightPanelTab: 'none',
  leftPanelCollapsed: false,
  rightPanelCollapsed: false,
  showShortcuts: false,
  showHelpDocs: false,
  showSearch: false,
  showGridlines: true,
  contextMenu: {
    visible: false,
    position: null,
    type: 'canvas',
  },
  viewMode: 'visual',
}

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    selectNode(state, action: PayloadAction<string | null>) {
      state.selectedNodeId = action.payload
      state.selectedEdge = null
      if (action.payload) {
        state.rightPanelTab = 'properties'
      } else if (state.rightPanelTab === 'properties' || state.rightPanelTab === 'edge') {
        state.rightPanelTab = 'none'
      }
    },

    selectEdge(state, action: PayloadAction<ProcessConnection | null>) {
      state.selectedEdge = action.payload
      state.selectedNodeId = null
      if (action.payload) {
        state.rightPanelTab = 'edge'
      } else if (state.rightPanelTab === 'edge' || state.rightPanelTab === 'properties') {
        state.rightPanelTab = 'none'
      }
    },

    togglePanel(state, action: PayloadAction<'showShortcuts' | 'showHelpDocs' | 'showSearch'>) {
      state[action.payload] = !state[action.payload]
    },

    toggleLeftPanel(state) {
      state.leftPanelCollapsed = !state.leftPanelCollapsed
    },

    toggleRightPanel(state) {
      state.rightPanelCollapsed = !state.rightPanelCollapsed
    },

    setSidePanelsCollapsed(state, action: PayloadAction<{ left: boolean; right: boolean }>) {
      state.leftPanelCollapsed = action.payload.left
      state.rightPanelCollapsed = action.payload.right
    },

    setRightPanelTab(
      state,
      action: PayloadAction<'properties' | 'edge' | 'validation' | 'debug' | 'none'>
    ) {
      state.rightPanelTab = action.payload
    },

    openRightPanelTab(
      state,
      action: PayloadAction<{
        tab: 'validation' | 'debug'
        collapseLeft: boolean
      }>
    ) {
      state.rightPanelTab = action.payload.tab
      state.rightPanelCollapsed = false
      if (action.payload.collapseLeft) {
        state.leftPanelCollapsed = true
      }
    },

    showContextMenu(
      state,
      action: PayloadAction<{
        position: { x: number; y: number }
        type: 'canvas' | 'node' | 'edge'
        targetId?: string
      }>
    ) {
      state.contextMenu = {
        visible: true,
        position: action.payload.position,
        type: action.payload.type,
        targetId: action.payload.targetId,
      }
    },

    hideContextMenu(state) {
      state.contextMenu = {
        visible: false,
        position: null,
        type: 'canvas',
      }
    },

    toggleGridlines(state) {
      state.showGridlines = !state.showGridlines
    },

    switchView(state, action: PayloadAction<DesignerViewMode>) {
      state.viewMode = action.payload
    },
  },
})

export const {
  selectNode,
  selectEdge,
  togglePanel,
  setRightPanelTab,
  openRightPanelTab,
  showContextMenu,
  hideContextMenu,
  toggleGridlines,
  switchView,
  toggleLeftPanel,
  toggleRightPanel,
  setSidePanelsCollapsed,
} = uiSlice.actions

export default uiSlice.reducer

export const selectSelectedNodeId = (state: UiRootState) => state.ui.selectedNodeId
export const selectSelectedEdge = (state: UiRootState) => state.ui.selectedEdge
export const selectRightPanelTab = (state: UiRootState) => state.ui.rightPanelTab
export const selectShowShortcuts = (state: UiRootState) => state.ui.showShortcuts
export const selectShowHelpDocs = (state: UiRootState) => state.ui.showHelpDocs
export const selectShowSearch = (state: UiRootState) => state.ui.showSearch
export const selectShowGridlines = (state: UiRootState) => state.ui.showGridlines
export const selectContextMenu = (state: UiRootState) => state.ui.contextMenu
export const selectViewMode = (state: UiRootState) => state.ui.viewMode
export const selectLeftPanelCollapsed = (state: UiRootState) => state.ui.leftPanelCollapsed
export const selectRightPanelCollapsed = (state: UiRootState) => state.ui.rightPanelCollapsed
