import type { APIRequestContext } from '@playwright/test'
import { expect } from '@playwright/test'

import { apiHeaders, serverUrl } from './integrationRuntime'

export interface JsonResponse {
  ok(): boolean

  status(): number

  text(): Promise<string>
}

export async function expectJson<T>(response: JsonResponse): Promise<T> {
  const body = await response.text()
  expect(response.ok(), `HTTP ${response.status()}: ${body}`).toBeTruthy()
  return JSON.parse(body) as T
}

export async function postJson<T>(
  request: APIRequestContext,
  path: string,
  data: unknown,
  headers: Record<string, string> = {}
): Promise<T> {
  return expectJson<T>(
    await request.post(`${serverUrl}${path}`, {
      data,
      headers: { ...apiHeaders(), ...headers },
    })
  )
}
