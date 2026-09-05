import { beforeEach, describe, expect, it } from 'vitest'

import {
  LEARNING_PROGRESS_STORAGE_KEY,
  parseLearningProgress,
  readLearningProgress,
  writeLearningProgress,
} from '../learningProgressStorage'

describe('learningProgressStorage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('accepts the complete stored progress contract', () => {
    expect(
      parseLearningProgress(
        JSON.stringify({
          completedExamples: ['hello-world'],
          totalExamples: 3,
          lastAccessTime: 123,
        })
      )
    ).toEqual({
      completedExamples: ['hello-world'],
      totalExamples: 3,
      lastAccessTime: 123,
    })
  })

  it.each([
    '{',
    JSON.stringify({ completedExamples: ['example'], totalExamples: -1, lastAccessTime: 1 }),
    JSON.stringify({
      completedExamples: ['example', 'example'],
      totalExamples: 2,
      lastAccessTime: 1,
    }),
    JSON.stringify({
      completedExamples: [],
      totalExamples: 0,
      lastAccessTime: 1,
      unexpected: true,
    }),
  ])('rejects malformed or non-canonical records', (raw) => {
    expect(parseLearningProgress(raw)).toBeNull()
  })

  it('removes invalid records at the storage boundary', () => {
    localStorage.setItem(LEARNING_PROGRESS_STORAGE_KEY, '{"totalExamples":"invalid"}')

    expect(readLearningProgress()).toBeNull()
    expect(localStorage.getItem(LEARNING_PROGRESS_STORAGE_KEY)).toBeNull()
  })

  it('round-trips valid progress', () => {
    const progress = {
      completedExamples: ['example'],
      totalExamples: 2,
      lastAccessTime: 456,
    }

    writeLearningProgress(progress)

    expect(readLearningProgress()).toEqual(progress)
  })
})
