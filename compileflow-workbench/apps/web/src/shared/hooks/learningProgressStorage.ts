import { z } from 'zod'

export const LEARNING_PROGRESS_STORAGE_KEY = 'compileflow:learning-progress'

const learningProgressSchema = z
  .object({
    completedExamples: z
      .array(z.string().min(1).max(512))
      .refine((items) => new Set(items).size === items.length),
    totalExamples: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
    lastAccessTime: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
  })
  .strict()

export interface LearningProgress {
  completedExamples: string[]
  totalExamples: number
  lastAccessTime: number
}

export function parseLearningProgress(raw: string | null): LearningProgress | null {
  if (raw === null) return null

  try {
    const result = learningProgressSchema.safeParse(JSON.parse(raw))
    return result.success ? result.data : null
  } catch {
    return null
  }
}

export function readLearningProgress(): LearningProgress | null {
  try {
    const raw = localStorage.getItem(LEARNING_PROGRESS_STORAGE_KEY)
    const progress = parseLearningProgress(raw)
    if (raw !== null && progress === null) {
      localStorage.removeItem(LEARNING_PROGRESS_STORAGE_KEY)
    }
    return progress
  } catch {
    return null
  }
}

export function writeLearningProgress(progress: LearningProgress): void {
  try {
    localStorage.setItem(LEARNING_PROGRESS_STORAGE_KEY, JSON.stringify(progress))
  } catch {
    // Learning progress remains available in memory when browser storage is unavailable.
  }
}
