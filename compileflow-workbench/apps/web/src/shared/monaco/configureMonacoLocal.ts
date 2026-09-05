import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api.js'
import 'monaco-editor/esm/vs/basic-languages/java/java.contribution.js'
import 'monaco-editor/esm/vs/basic-languages/xml/xml.contribution.js'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'

/**
 * Bundle Monaco from node_modules instead of the default jsDelivr CDN.
 * Workbench CSP only allows script-src 'self', so CDN loader.js stays stuck on
 * "Loading…" forever without this configuration.
 */
export function configureMonacoLocal(): void {
  const globalScope = globalThis as typeof globalThis & {
    MonacoEnvironment?: {
      getWorker: (_: unknown, label: string) => Worker
    }
  }

  // Java and XML language services use the editor worker in this bundle.
  globalScope.MonacoEnvironment = {
    getWorker() {
      return new editorWorker()
    },
  }

  loader.config({ monaco })
  // Keep the classic global for diagnostics / Playwright helpers.
  ;(globalThis as typeof globalThis & { monaco?: typeof monaco }).monaco = monaco
}
