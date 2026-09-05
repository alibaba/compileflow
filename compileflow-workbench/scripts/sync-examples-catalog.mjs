#!/usr/bin/env node
/**
 * Sync Learn mock examples into compileflow-workbench-server classpath catalog.
 *
 * Usage:
 *   pnpm sync:examples-catalog          # write JSON
 *   pnpm sync:examples-catalog --check  # fail if catalog is stale (CI gate)
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const WB_ROOT = join(__dirname, '..')
const CATALOG_PATH = join(
  WB_ROOT,
  '../compileflow-workbench-server/src/main/resources/learn/examples-catalog.json'
)
const MOCK_DATA_MODULE = join(WB_ROOT, 'apps/web/src/shared/api/exampleMockData.ts')

function normalizeJson(data) {
  return `${JSON.stringify(data, null, 2)}\n`
}

async function loadExamples() {
  const moduleUrl = pathToFileURL(MOCK_DATA_MODULE).href
  const mod = await import(moduleUrl)
  return mod.listMockExamples()
}

const checkOnly = process.argv.includes('--check')
const examples = await loadExamples()
const nextContent = normalizeJson(examples)

if (checkOnly) {
  let currentContent
  try {
    currentContent = readFileSync(CATALOG_PATH, 'utf8')
  } catch {
    console.error(`Missing catalog at ${CATALOG_PATH}. Run: pnpm sync:examples-catalog`)
    process.exit(1)
  }
  if (currentContent !== nextContent) {
    console.error(
      'examples-catalog.json is out of sync with apps/web/src/shared/api/exampleMockData.ts.\n' +
        'Run: pnpm sync:examples-catalog'
    )
    process.exit(1)
  }
  console.log(`Examples catalog in sync (${examples.length} entries)`)
} else {
  mkdirSync(dirname(CATALOG_PATH), { recursive: true })
  writeFileSync(CATALOG_PATH, nextContent)
  console.log(`Wrote ${CATALOG_PATH} (${examples.length} entries)`)
}
