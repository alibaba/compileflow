import { defineConfig, devices } from '@playwright/test'

import { workbenchChineseLocale } from './playwright.shared.config'

const externalApi = process.env.COMPILEFLOW_E2E_SERVER_URL
const externalBrowser = process.env.COMPILEFLOW_E2E_BROWSER_URL
const managed = !externalApi && !externalBrowser
const browserUrl = externalBrowser ?? (managed ? 'http://127.0.0.1:4173' : undefined)

export default defineConfig({
  testDir: './e2e/integration',
  timeout: 30_000,
  workers: 1,
  retries: 0,
  reporter: process.env.CI
    ? [['github'], ['junit', { outputFile: 'test-results/workbench-integration-junit.xml' }]]
    : 'list',
  use: {
    ...workbenchChineseLocale,
    baseURL: browserUrl ?? 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: managed
    ? [
        {
          command: 'bash ../../scripts/start-integration-server.sh',
          url: 'http://127.0.0.1:8080/actuator/health',
          reuseExistingServer: false,
          timeout: 120_000,
        },
        {
          command: 'node ../../scripts/start-integration-edge.mjs',
          url: 'http://127.0.0.1:4173/health',
          reuseExistingServer: false,
          timeout: 120_000,
        },
      ]
    : undefined,
})
