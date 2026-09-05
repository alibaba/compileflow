import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Sets the browser document title.  Appends " | CompileFlow Workbench"
 * automatically so every page has a consistent brand suffix.
 */
export function usePageTitle(titleKey: string, subject?: string | null) {
  const { t } = useTranslation()
  useEffect(() => {
    const pageTitle = t(titleKey)
    document.title = subject
      ? `${subject} — ${pageTitle} | CompileFlow Workbench`
      : `${pageTitle} | CompileFlow Workbench`
    return () => {
      document.title = 'CompileFlow Workbench — Reliable Java Processes for the AI Era'
    }
  }, [subject, t, titleKey])
}
