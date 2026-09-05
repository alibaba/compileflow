// ThemeContext wraps the app in Ant Design's ConfigProvider so antd components
// switch themes automatically via the dark algorithm instead of manual CSS overrides.
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import type { ReactNode } from 'react'
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { GlobalErrorHandler } from '@/shared/api/errorHandler'

type Theme = 'light' | 'dark'
const THEME_STORAGE_KEY = 'compileflow:theme'

function isTheme(value: string | null): value is Theme {
  return value === 'light' || value === 'dark'
}

function readStoredTheme(): Theme | null {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY)
    return isTheme(value) ? value : null
  } catch {
    return null
  }
}

function writeStoredTheme(theme: Theme): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // The selected theme still applies for the current session.
  }
}

interface ThemeContextType {
  theme: Theme
  setTheme: (theme: Theme) => void
  toggleTheme: () => void
  isTransitioning: boolean
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined)

export const useTheme = () => {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider')
  }
  return context
}

interface ThemeProviderProps {
  children: ReactNode
}

const LIGHT_TOKENS = {
  colorPrimary: '#6b57ff',
  colorSuccess: '#0f9f6e',
  colorWarning: '#fc801d',
  colorError: '#fe2857',
  colorInfo: '#087cfa',
  borderRadius: 8,
  borderRadiusLG: 12,
  borderRadiusSM: 6,
  fontFamily:
    '"Sora", "Noto Sans SC", -apple-system, BlinkMacSystemFont, "Segoe UI", "Helvetica Neue", Arial, sans-serif',
  fontSize: 14,
  colorBgContainer: '#ffffff',
  colorBgLayout: '#f3f5f8',
  colorBgElevated: '#ffffff',
  colorText: 'rgba(11, 18, 32, 0.9)',
  colorTextSecondary: 'rgba(11, 18, 32, 0.62)',
  colorBorder: 'rgba(15, 23, 42, 0.1)',
  colorBorderSecondary: 'rgba(15, 23, 42, 0.06)',
  controlHeight: 34,
  motionDurationMid: '0.2s',
  motionDurationSlow: '0.3s',
  boxShadow: '0 1px 3px rgba(11, 18, 32, 0.05), 0 4px 12px rgba(11, 18, 32, 0.04)',
  boxShadowSecondary: '0 2px 6px rgba(11, 18, 32, 0.05), 0 10px 24px rgba(11, 18, 32, 0.07)',
} as const

const DARK_TOKENS = {
  ...LIGHT_TOKENS,
  colorPrimary: '#9b8cff',
  colorBgContainer: '#171c26',
  colorBgLayout: '#0c0f14',
  colorBgElevated: '#1a2030',
  colorText: 'rgba(241, 245, 249, 0.9)',
  colorTextSecondary: 'rgba(226, 232, 240, 0.62)',
  colorBorder: 'rgba(255, 255, 255, 0.1)',
  colorBorderSecondary: 'rgba(255, 255, 255, 0.06)',
  boxShadow: '0 1px 3px rgba(0, 0, 0, 0.4), 0 4px 12px rgba(0, 0, 0, 0.3)',
  boxShadowSecondary: '0 2px 6px rgba(0, 0, 0, 0.35), 0 12px 28px rgba(0, 0, 0, 0.4)',
} as const

export const ThemeProvider = ({ children }: ThemeProviderProps) => {
  const { i18n } = useTranslation()
  const [theme, setTheme] = useState<Theme>(() => {
    const savedTheme = readStoredTheme()
    if (savedTheme) {
      return savedTheme
    }

    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark'
    }

    return 'light'
  })

  const [isTransitioning, setIsTransitioning] = useState(false)
  const transitionTimer = useRef<number | undefined>(undefined)

  useEffect(
    () => () => {
      if (transitionTimer.current !== undefined) clearTimeout(transitionTimer.current)
    },
    []
  )

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)

    const metaThemeColor = document.querySelector('meta[name="theme-color"]')
    if (metaThemeColor) {
      metaThemeColor.setAttribute('content', theme === 'dark' ? '#0c0f14' : '#fbfcfd')
    }
  }, [theme])

  useEffect(() => {
    const syncTheme = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY && isTheme(event.newValue)) {
        setTheme(event.newValue)
      }
    }
    window.addEventListener('storage', syncTheme)
    const storedTheme = readStoredTheme()
    if (storedTheme) setTheme(storedTheme)
    return () => window.removeEventListener('storage', syncTheme)
  }, [])

  const applyTheme = (nextTheme: Theme) => {
    if (transitionTimer.current !== undefined) clearTimeout(transitionTimer.current)
    setIsTransitioning(true)
    setTheme(nextTheme)
    writeStoredTheme(nextTheme)
    transitionTimer.current = window.setTimeout(() => {
      transitionTimer.current = undefined
      setIsTransitioning(false)
    }, 300)
  }

  const toggleTheme = () => applyTheme(theme === 'light' ? 'dark' : 'light')

  const isDark = theme === 'dark'

  return (
    <ThemeContext.Provider value={{ theme, setTheme: applyTheme, toggleTheme, isTransitioning }}>
      <ConfigProvider
        locale={i18n.resolvedLanguage === 'en' ? undefined : zhCN}
        theme={{
          algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
          token: isDark ? DARK_TOKENS : LIGHT_TOKENS,
          components: {
            Button: {
              primaryShadow: '0 2px 10px color-mix(in srgb, #6b57ff 28%, transparent)',
              defaultShadow: 'none',
              fontWeight: 500,
            },
            Card: {
              paddingLG: 20,
            },
            Menu: {
              itemBorderRadius: 6,
              itemMarginInline: 6,
              activeBarBorderWidth: 0,
            },
            Input: {
              activeShadow: '0 0 0 3px color-mix(in srgb, #6b57ff 12%, transparent)',
            },
            Select: {
              optionSelectedBg: 'color-mix(in srgb, #6b57ff 8%, transparent)',
            },
            Alert: {
              borderRadiusLG: 10,
            },
            Modal: {
              borderRadiusLG: 12,
            },
            Drawer: {
              paddingLG: 20,
            },
            Table: {
              headerBg: 'var(--bg-panel)',
              headerColor: 'var(--text-secondary)',
              rowHoverBg: 'var(--hover-bg)',
              borderColor: 'var(--border-color)',
              cellPaddingBlock: 12,
              cellPaddingInline: 16,
            },
            Tag: {
              defaultBg: 'var(--bg-panel)',
              defaultColor: 'var(--text-secondary)',
            },
            Tabs: {
              inkBarColor: '#6b57ff',
              itemColor: 'var(--text-secondary)',
              itemSelectedColor: 'var(--text-primary)',
              itemHoverColor: 'var(--text-primary)',
            },
          },
        }}
      >
        <AntdApp>
          <GlobalErrorHandler />
          {children}
        </AntdApp>
      </ConfigProvider>
    </ThemeContext.Provider>
  )
}
