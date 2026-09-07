import { defineConfig, devices } from '@playwright/test'

import { workbenchChineseLocale } from './playwright.shared.config'

export default defineConfig({
  testDir: './e2e',
  testIgnore: [
    'integration/**',
    'journey-aio.spec.ts',
    'journey-real.spec.ts',
    'journey-secured.spec.ts',
  ],

  /* 最大失败数 */
  maxFailures: 5,

  /* 测试超时 */
  timeout: 30 * 1000,
  expect: {
    timeout: 5000,
  },

  /* 并行worker */
  fullyParallel: true,
  workers: process.env.CI ? 2 : undefined,

  /* 失败重试 */
  retries: process.env.CI ? 2 : 0,

  /* Reporter */
  reporter: 'html',

  /* 全局配置 */
  use: {
    ...workbenchChineseLocale,
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  /* 浏览器配置 */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  /* 开发服务器 */
  webServer: [
    {
      command: 'pnpm --dir ../dev-gateway dev',
      url: 'http://127.0.0.1:3001/api/status',
      reuseExistingServer: false,
      timeout: 120 * 1000,
      env: {
        ...process.env,
        COMPILEFLOW_DEV_GATEWAY_PORT: '3001',
      },
    },
    {
      command: 'pnpm dev --strictPort',
      url: 'http://localhost:5173',
      reuseExistingServer: false,
      timeout: 120 * 1000,
      env: {
        ...process.env,
        COMPILEFLOW_DEV_GATEWAY_PORT: '3001',
        VITE_COMPILEFLOW_OPERATE_MODE: 'mock',
        VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES: 'true',
      },
    },
  ],
})
