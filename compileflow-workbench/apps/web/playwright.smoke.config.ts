import { defineConfig, devices } from '@playwright/test'

import { workbenchChineseLocale } from './playwright.shared.config'

export default defineConfig({
  testDir: './e2e',
  testMatch: [
    'learn-example.smoke.spec.ts',
    'operate-designer-bridge.spec.ts',
    'settings.smoke.spec.ts',
    'tbbpm-designer.spec.ts',
  ],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'html',
  use: {
    ...workbenchChineseLocale,
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: {
      ...process.env,
      VITE_COMPILEFLOW_OPERATE_MODE: 'mock',
      VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES: 'true',
    },
  },
})
