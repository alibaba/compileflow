import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  LEARNING_PROGRESS_STORAGE_KEY,
  readLearningProgress,
  writeLearningProgress,
} from '../learningProgressStorage'
import { useLearningProgress } from '../useLearningProgress'

describe('useLearningProgress', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('preserves sequential updates, duplicate completion and reset semantics', () => {
    const { result } = renderHook(() => useLearningProgress())
    act(() => result.current.setTotalExamples(2))
    act(() => result.current.markAsCompleted('example-one'))
    act(() => result.current.markAsCompleted('example-one'))

    expect(readLearningProgress()?.completedExamples).toEqual(['example-one'])
    expect(readLearningProgress()?.totalExamples).toBe(2)
    expect(result.current.getCompletionRate()).toBe(50)

    act(() => result.current.resetProgress())

    expect(readLearningProgress()?.completedExamples).toEqual([])
    expect(readLearningProgress()?.totalExamples).toBe(2)
    expect(result.current.getCompletionRate()).toBe(0)
  })

  it('retains in-memory progress when native Storage.setItem really throws a quota error', () => {
    // The shared test setup uses a plain-object Storage fake. Use a native realm
    // here so the prototype spy intercepts the same operation as a browser quota error.
    const frame = document.createElement('iframe')
    document.body.append(frame)
    const nativeStorage = frame.contentWindow!.localStorage
    vi.stubGlobal('localStorage', nativeStorage)
    vi.stubGlobal('Storage', nativeStorage.constructor)
    const previous = { completedExamples: [], totalExamples: 2, lastAccessTime: 1 }
    writeLearningProgress(previous)
    const quotaError = new DOMException('Storage quota exceeded', 'QuotaExceededError')
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw quotaError
    })

    try {
      expect(localStorage).toBeInstanceOf(Storage)
      expect(() => localStorage.setItem('quota-probe', 'value')).toThrow(quotaError)
      expect(setItem.mock.results).toEqual([{ type: 'throw', value: quotaError }])
      setItem.mockClear()

      const { result, unmount } = renderHook(() => useLearningProgress())
      act(() => result.current.markAsCompleted('example-one'))

      expect(setItem).toHaveBeenCalledWith(
        LEARNING_PROGRESS_STORAGE_KEY,
        JSON.stringify(result.current.progress)
      )
      expect(setItem.mock.results).toEqual([{ type: 'throw', value: quotaError }])
      expect(result.current.isCompleted('example-one')).toBe(true)
      expect(result.current.getCompletionRate()).toBe(50)
      expect(readLearningProgress()).toEqual(previous)
      unmount()
    } finally {
      setItem.mockRestore()
      vi.unstubAllGlobals()
      frame.remove()
    }
  })
})
