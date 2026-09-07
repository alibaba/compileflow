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
    const isChinese = navigator.language.toLowerCase().startsWith('zh')
    rootElement.textContent = isChinese
      ? 'CompileFlow Workbench 启动失败，请刷新页面后重试。'
      : 'CompileFlow Workbench failed to start. Refresh the page to try again.'
  }
}

void startApplication().catch(reportStartupFailure)
