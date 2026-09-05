import { readdirSync, readFileSync } from 'node:fs'
import { dirname, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url))
const DIST_DIR = resolve(SCRIPT_DIR, '../apps/web/dist')
const MANIFEST_PATH = join(DIST_DIR, '.vite/manifest.json')

const BUDGETS = Object.freeze({
  initialGzip: 450 * 1024,
  entryAsyncGzip: 350 * 1024,
  applicationJavaScriptGzip: 2300 * 1024,
  workerJavaScriptGzip: 128 * 1024,
  totalCssGzip: 66 * 1024,
})

function readManifest() {
  try {
    return JSON.parse(readFileSync(MANIFEST_PATH, 'utf8'))
  } catch (error) {
    throw new Error(`Cannot read Vite manifest at ${MANIFEST_PATH}`, { cause: error })
  }
}

function listFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? listFiles(path) : [path]
  })
}

function collectStaticAssets(manifest, key, assets, chunkKeys = new Set()) {
  if (chunkKeys.has(key)) {
    return
  }
  chunkKeys.add(key)

  const chunk = manifest[key]
  if (!chunk) {
    throw new Error(`Vite manifest references missing chunk: ${key}`)
  }

  assets.add(chunk.file)
  for (const cssFile of chunk.css ?? []) {
    assets.add(cssFile)
  }
  for (const importedKey of chunk.imports ?? []) {
    collectStaticAssets(manifest, importedKey, assets, chunkKeys)
  }
}

function gzipSize(relativePath) {
  const content = readFileSync(join(DIST_DIR, relativePath))
  return gzipSync(content, { level: 9 }).length
}

function totalGzipSize(relativePaths) {
  return [...relativePaths].reduce((total, path) => total + gzipSize(path), 0)
}

function formatKib(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`
}

function assertWithinBudget(label, actual, budget, failures) {
  console.log(`${label}: ${formatKib(actual)} / ${formatKib(budget)}`)
  if (actual > budget) {
    failures.push(`${label} exceeds its budget by ${formatKib(actual - budget)}`)
  }
}

const manifest = readManifest()
const entries = Object.entries(manifest).filter(([, chunk]) => chunk.isEntry)
if (entries.length !== 1) {
  throw new Error(`Expected one web entry in the Vite manifest, found ${entries.length}`)
}

const initialAssets = new Set()
const initialChunkKeys = new Set()
collectStaticAssets(manifest, entries[0][0], initialAssets, initialChunkKeys)

const entryAsyncKeys = new Set(
  [...initialChunkKeys].flatMap((key) => manifest[key].dynamicImports ?? [])
)
const entryAsyncMeasurements = [...entryAsyncKeys]
  .map((key) => {
    const assets = new Set()
    collectStaticAssets(manifest, key, assets)
    for (const initialAsset of initialAssets) {
      assets.delete(initialAsset)
    }
    return {
      key,
      size: totalGzipSize(assets),
    }
  })
  .sort((left, right) => right.size - left.size)

const outputFiles = listFiles(DIST_DIR)
const javascriptFiles = outputFiles.filter((path) => extname(path) === '.js')
const cssFiles = outputFiles.filter((path) => extname(path) === '.css')
const relativeJavaScriptFiles = javascriptFiles.map((path) => path.slice(DIST_DIR.length + 1))
const relativeCssFiles = cssFiles.map((path) => path.slice(DIST_DIR.length + 1))
const workerJavaScriptFiles = relativeJavaScriptFiles.filter((path) =>
  /(^|\/)[^/]+\.worker-[^/]+\.js$/.test(path)
)
const applicationJavaScriptFiles = relativeJavaScriptFiles.filter(
  (path) => !workerJavaScriptFiles.includes(path)
)

const initialGzip = totalGzipSize(initialAssets)
const largestEntryAsync = entryAsyncMeasurements[0] ?? { key: 'none', size: 0 }
const applicationJavaScriptGzip = totalGzipSize(applicationJavaScriptFiles)
const workerJavaScriptGzip = totalGzipSize(workerJavaScriptFiles)
const totalCssGzip = totalGzipSize(relativeCssFiles)
const failures = []

assertWithinBudget('Initial static assets (gzip)', initialGzip, BUDGETS.initialGzip, failures)
assertWithinBudget(
  `Largest entry-level async increment (gzip, ${largestEntryAsync.key})`,
  largestEntryAsync.size,
  BUDGETS.entryAsyncGzip,
  failures
)
assertWithinBudget(
  'Application JavaScript assets (gzip)',
  applicationJavaScriptGzip,
  BUDGETS.applicationJavaScriptGzip,
  failures
)
assertWithinBudget(
  'Monaco worker JavaScript assets (gzip)',
  workerJavaScriptGzip,
  BUDGETS.workerJavaScriptGzip,
  failures
)
assertWithinBudget('All CSS assets (gzip)', totalCssGzip, BUDGETS.totalCssGzip, failures)

if (failures.length > 0) {
  throw new Error(`Web bundle budget failed:\n- ${failures.join('\n- ')}`)
}
