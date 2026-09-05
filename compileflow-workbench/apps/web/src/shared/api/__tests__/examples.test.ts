import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAllExamples, getExample } from '../examples'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  findMockExample: vi.fn(),
  listMockExamples: vi.fn(),
}))

vi.mock('@/shared/api/client', () => ({
  default: {
    get: mocks.apiGet,
  },
}))

vi.mock('@/shared/config/buildConfig', () => ({
  APP_BUILD_CONFIG: {
    useBuiltInExamples: false,
  },
}))

vi.mock('../exampleMockData', () => ({
  findMockExample: mocks.findMockExample,
  listMockExamples: mocks.listMockExamples,
}))

describe('real example catalog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses the server catalog as the sole authority', async () => {
    const examples = [{ id: 'server-example' }]
    mocks.apiGet.mockResolvedValue(examples)

    await expect(getAllExamples()).resolves.toBe(examples)

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/examples')
    expect(mocks.listMockExamples).not.toHaveBeenCalled()
  })

  it('propagates server failures without falling back to mock data', async () => {
    const failure = new Error('catalog unavailable')
    mocks.apiGet.mockRejectedValue(failure)

    await expect(getExample('server-example')).rejects.toBe(failure)

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/examples/server-example')
    expect(mocks.findMockExample).not.toHaveBeenCalled()
  })
})
