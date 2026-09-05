import { describe, expect, it } from 'vitest'

import uiReducer from '../uiSlice'
import { openRightPanelTab, setSidePanelsCollapsed, togglePanel } from '../uiSlice'

describe('uiSlice', () => {
  it('toggles supported panel flags', () => {
    let state = uiReducer(undefined, togglePanel('showSearch'))
    expect(state.showSearch).toBe(true)

    state = uiReducer(state, togglePanel('showHelpDocs'))
    expect(state.showHelpDocs).toBe(true)

    state = uiReducer(state, togglePanel('showShortcuts'))
    expect(state.showShortcuts).toBe(true)
  })

  it('sets both side-panel states atomically for narrow layouts', () => {
    const state = uiReducer(undefined, setSidePanelsCollapsed({ left: true, right: false }))

    expect(state.leftPanelCollapsed).toBe(true)
    expect(state.rightPanelCollapsed).toBe(false)
  })

  it('opens a requested right-panel tab and only hides the palette on narrow layouts', () => {
    const collapsed = uiReducer(undefined, setSidePanelsCollapsed({ left: false, right: true }))
    const desktop = uiReducer(collapsed, openRightPanelTab({ tab: 'debug', collapseLeft: false }))

    expect(desktop.rightPanelTab).toBe('debug')
    expect(desktop.rightPanelCollapsed).toBe(false)
    expect(desktop.leftPanelCollapsed).toBe(false)

    const mobile = uiReducer(desktop, openRightPanelTab({ tab: 'validation', collapseLeft: true }))
    expect(mobile.rightPanelTab).toBe('validation')
    expect(mobile.rightPanelCollapsed).toBe(false)
    expect(mobile.leftPanelCollapsed).toBe(true)
  })
})
