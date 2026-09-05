import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { defineConfig, devices } from '@playwright/test'

import { workbenchChineseLocale } from './playwright.shared.config'

const API_KEY = 'compileflow-e2e-test-api-key-32charsxx'
const WEB_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const REPO_ROOT = path.resolve(WEB_ROOT, '..')
const JAVA_HOME = process.env.COMPILEFLOW_E2E_JAVA_HOME?.trim()
const JAVA_COMMAND = JAVA_HOME
  ? `JAVA_HOME="${JAVA_HOME}" PATH="${JAVA_HOME}/bin:$PATH" java`
  : 'java'
const JAR = path.join(
  REPO_ROOT,
  'compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar'
)
const DATABASE_URL =
  process.env.COMPILEFLOW_E2E_DATABASE_URL ?? 'jdbc:postgresql://localhost:5432/compileflow'
const DATABASE_USERNAME = process.env.COMPILEFLOW_E2E_DATABASE_USERNAME ?? 'compileflow'
const DATABASE_PASSWORD =
  process.env.COMPILEFLOW_E2E_DATABASE_PASSWORD ?? 'compileflow_test_password'

/**
 * Secured edge: Vite :5175 → trusted edge :4174 → API_KEY server :8082.
 * Proves browser never holds the key while Operate/Learn still work.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: ['journey-secured.spec.ts'],
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report/secured' }]],
  outputDir: 'test-results/journey-secured-output',
  use: {
    ...workbenchChineseLocale,
    baseURL: 'http://127.0.0.1:5175',
    trace: 'retain-on-failure',
    screenshot: 'off',
    video: 'retain-on-failure',
    actionTimeout: 20_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `${JAVA_COMMAND} -jar "${JAR}" ` + '--spring.profiles.active=dev --server.port=8082',
      url: 'http://127.0.0.1:8082/actuator/health',
      reuseExistingServer: false,
      timeout: 180_000,
      cwd: path.join(WEB_ROOT, 'apps/web'),
      env: {
        ...process.env,
        SPRING_DATASOURCE_URL: DATABASE_URL,
        SPRING_DATASOURCE_USERNAME: DATABASE_USERNAME,
        SPRING_DATASOURCE_PASSWORD: DATABASE_PASSWORD,
        COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE: 'API_KEY',
        COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY: API_KEY,
        COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL:
          'workbench-secured-e2e',
      },
    },
    {
      command: 'node ../../scripts/start-secured-edge.mjs',
      url: 'http://127.0.0.1:4174/health',
      reuseExistingServer: false,
      timeout: 60_000,
      cwd: path.join(WEB_ROOT, 'apps/web'),
      env: {
        ...process.env,
        COMPILEFLOW_E2E_SERVER_API_KEY: API_KEY,
        COMPILEFLOW_SECURED_EDGE_PORT: '4174',
        COMPILEFLOW_SECURED_UPSTREAM: 'http://127.0.0.1:8082',
      },
    },
    {
      command:
        'pnpm --filter @compileflow/workbench-web exec vite --host 127.0.0.1 --port 5175 --clearScreen false',
      url: 'http://127.0.0.1:5175',
      reuseExistingServer: false,
      timeout: 120_000,
      cwd: WEB_ROOT,
      env: {
        ...process.env,
        VITE_COMPILEFLOW_OPERATE_MODE: 'real',
        VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES: 'false',
        COMPILEFLOW_API_PROXY_TARGET: 'http://127.0.0.1:4174',
      },
    },
  ],
})
