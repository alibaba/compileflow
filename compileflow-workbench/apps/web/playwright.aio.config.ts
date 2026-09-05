import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { defineConfig, devices } from '@playwright/test'

import { workbenchChineseLocale } from './playwright.shared.config'

const WEB_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const REPO_ROOT = path.resolve(WEB_ROOT, '..')
const JAVA_HOME = process.env.COMPILEFLOW_E2E_JAVA_HOME?.trim()
const JAVA_COMMAND = JAVA_HOME
  ? `JAVA_HOME="${JAVA_HOME}" PATH="${JAVA_HOME}/bin:$PATH" java`
  : 'java'
const JAR = path.join(
  REPO_ROOT,
  'compileflow-workbench-server/target/compileflow-workbench-all-in-one-2.0.0-SNAPSHOT.jar'
)
const DATABASE_URL =
  process.env.COMPILEFLOW_E2E_DATABASE_URL ?? 'jdbc:postgresql://localhost:5432/compileflow'
const DATABASE_USERNAME = process.env.COMPILEFLOW_E2E_DATABASE_USERNAME ?? 'compileflow'
const DATABASE_PASSWORD =
  process.env.COMPILEFLOW_E2E_DATABASE_PASSWORD ?? 'compileflow_test_password'

/**
 * All-in-one JAR: embedded static UI + API on :8083 (dev profile, auth DISABLED).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: ['journey-aio.spec.ts'],
  timeout: 120_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report/aio' }]],
  outputDir: 'test-results/journey-aio-output',
  use: {
    ...workbenchChineseLocale,
    baseURL: 'http://127.0.0.1:8083',
    trace: 'retain-on-failure',
    screenshot: 'off',
    video: 'retain-on-failure',
    actionTimeout: 20_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `${JAVA_COMMAND} -jar "${JAR}" ` + '--spring.profiles.active=dev --server.port=8083',
    url: 'http://127.0.0.1:8083/actuator/health',
    reuseExistingServer: false,
    timeout: 180_000,
    cwd: path.join(WEB_ROOT, 'apps/web'),
    env: {
      ...process.env,
      SPRING_DATASOURCE_URL: DATABASE_URL,
      SPRING_DATASOURCE_USERNAME: DATABASE_USERNAME,
      SPRING_DATASOURCE_PASSWORD: DATABASE_PASSWORD,
    },
  },
})
