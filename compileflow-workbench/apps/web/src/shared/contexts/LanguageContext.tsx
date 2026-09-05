import { createContext, ReactNode, useContext, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

export type Language = 'zh' | 'en'

const LANGUAGE_STORAGE_KEY = 'compileflow:language'

function isLanguage(value: string | null): value is Language {
  return value === 'zh' || value === 'en'
}

interface LanguageContextType {
  language: Language
  setLanguage: (lang: Language) => void
  t: (key: string) => string
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined)

export const LanguageProvider = ({ children }: { children: ReactNode }) => {
  const { t, i18n } = useTranslation()

  const currentLanguage: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'zh'

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = currentLanguage === 'en' ? 'en' : 'zh-CN'
    }
  }, [currentLanguage])

  useEffect(() => {
    const syncLanguage = (event: StorageEvent) => {
      if (event.key === LANGUAGE_STORAGE_KEY && isLanguage(event.newValue)) {
        void i18n.changeLanguage(event.newValue)
      }
    }
    window.addEventListener('storage', syncLanguage)
    const storedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY)
    if (isLanguage(storedLanguage)) void i18n.changeLanguage(storedLanguage)
    return () => window.removeEventListener('storage', syncLanguage)
  }, [i18n])

  const setLanguage = (lang: Language) => {
    void i18n.changeLanguage(lang)
  }

  return (
    <LanguageContext.Provider value={{ language: currentLanguage, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  )
}

export const useLanguage = () => {
  const context = useContext(LanguageContext)
  if (!context) {
    throw new Error('useLanguage must be used within LanguageProvider')
  }
  return context
}
