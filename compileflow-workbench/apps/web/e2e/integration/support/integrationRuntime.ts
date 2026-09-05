const externalApiUrl = process.env.COMPILEFLOW_E2E_SERVER_URL
const externalBrowserUrl = process.env.COMPILEFLOW_E2E_BROWSER_URL
export const managedIntegration = !externalApiUrl && !externalBrowserUrl
const directServer = managedIntegration || Boolean(externalApiUrl)
export const serverUrl = (externalApiUrl ?? externalBrowserUrl ?? 'http://127.0.0.1:8080').replace(
  /\/+$/,
  ''
)
export const browserUrl = (
  externalBrowserUrl ?? (managedIntegration ? 'http://127.0.0.1:4173' : '')
).replace(/\/+$/, '')

const apiKey = process.env.COMPILEFLOW_E2E_SERVER_API_KEY ?? ''

export function apiHeaders(): Record<string, string> {
  return directServer && apiKey ? { 'X-API-Key': apiKey } : {}
}

export function hasDirectApiKey(): boolean {
  return directServer && apiKey.length > 0
}
