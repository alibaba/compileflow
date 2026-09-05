import apiClient from '@/shared/api/client'
import { findMockExample, listMockExamples } from '@/shared/api/exampleMockData'
import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import type { Example } from '@/shared/contracts'

export const getAllExamples = (): Promise<Example[]> => {
  if (APP_BUILD_CONFIG.useBuiltInExamples) {
    return Promise.resolve(listMockExamples())
  }
  return apiClient.get('/api/examples')
}

export const getExample = (id: string): Promise<Example> => {
  if (APP_BUILD_CONFIG.useBuiltInExamples) {
    const example = findMockExample(id)
    return example
      ? Promise.resolve(example)
      : Promise.reject(new Error(`Example not found: ${id}`))
  }
  return apiClient.get(`/api/examples/${id}`)
}
