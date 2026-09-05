import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AUTO_SAVE_DEBOUNCE_MS, useAutoSave } from '../useAutoSave'

import type { AppDispatch } from '@/app/store'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { error: vi.fn() } }) },
}))

describe('useAutoSave', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('marks pending while debounce timer is active', () => {
    const dispatch = vi.fn(() => ({
      unwrap: vi.fn().mockResolvedValue({ id: 'flow-1' }),
    })) as unknown as AppDispatch

    const { result } = renderHook(() =>
      useAutoSave({
        isModified: true,
        isSaving: false,
        canSave: true,
        dispatch,
      })
    )

    expect(result.current.autoSavePending).toBe(true)
  })

  it('dispatches saveProcess after debounce elapses', async () => {
    const dispatch = vi.fn(() => ({
      unwrap: vi.fn().mockResolvedValue({ id: 'flow-1' }),
    })) as unknown as AppDispatch

    renderHook(() =>
      useAutoSave({
        isModified: true,
        isSaving: false,
        canSave: true,
        dispatch,
      })
    )

    await act(async () => {
      vi.advanceTimersByTime(AUTO_SAVE_DEBOUNCE_MS)
    })

    expect(dispatch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(dispatch).mock.calls[0][0]).toEqual(expect.any(Function))
  })

  it('does not schedule when there are no unsaved changes', async () => {
    const dispatch = vi.fn() as unknown as AppDispatch

    renderHook(() =>
      useAutoSave({
        isModified: false,
        isSaving: false,
        canSave: true,
        dispatch,
      })
    )

    await act(async () => {
      vi.advanceTimersByTime(AUTO_SAVE_DEBOUNCE_MS)
    })

    expect(dispatch).not.toHaveBeenCalled()
  })

  it('does not schedule while the draft cannot be serialized', async () => {
    const dispatch = vi.fn() as unknown as AppDispatch

    const { result } = renderHook(() =>
      useAutoSave({
        isModified: true,
        isSaving: false,
        canSave: false,
        dispatch,
      })
    )

    await act(async () => {
      vi.advanceTimersByTime(AUTO_SAVE_DEBOUNCE_MS)
    })

    expect(result.current.autoSavePending).toBe(false)
    expect(dispatch).not.toHaveBeenCalled()
  })
})
