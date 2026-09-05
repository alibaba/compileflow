import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router-dom'

import { store } from './app/store'
import { router } from './routes'
import { sanitizeDiagnosticValue } from './shared/config/buildConfig'
import { i18nReady } from './shared/i18n'
import './global.css'
import './index.css'

async function startApplication(): Promise<void> {
  await i18nReady

  const rootElement = document.getElementById('root')
  if (rootElement === null) {
    throw new Error('Application root element was not found')
  }

  createRoot(rootElement).render(
    <Provider store={store}>
      <RouterProvider router={router} />
    </Provider>
  )
}

function reportStartupFailure(error: unknown): void {
  console.error('CompileFlow Workbench failed to start', sanitizeDiagnosticValue(error))
  const rootElement = document.getElementById('root')
  if (rootElement !== null) {
    rootElement.textContent = 'CompileFlow Workbench failed to start.'
  }
}

void startApplication().catch(reportStartupFailure)
