import AppShell from '@shell/AppShell'

import { getAllExamples } from '@/shared/api/examples'
import ErrorBoundary from '@/shared/components/ErrorBoundary'
import { LanguageProvider } from '@/shared/contexts/LanguageContext'
import { SidebarProvider } from '@/shared/contexts/SidebarContext'
import { ThemeProvider } from '@/shared/contexts/ThemeContext'
import type { Example } from '@/shared/contracts'
import type { SearchableProcess } from '@/shell/components/GlobalSearch'

function loadExamples(): Promise<Example[]> {
  return getAllExamples()
}

async function loadWorkspaceProcesses(): Promise<SearchableProcess[]> {
  const { processStorage } = await import('@/authoring/designer/api/processStorage')
  return processStorage.listProcesses()
}

function App() {
  return (
    <ErrorBoundary>
      <LanguageProvider>
        <ThemeProvider>
          <SidebarProvider>
            <AppShell loadExamples={loadExamples} loadProcesses={loadWorkspaceProcesses} />
          </SidebarProvider>
        </ThemeProvider>
      </LanguageProvider>
    </ErrorBoundary>
  )
}

export default App
