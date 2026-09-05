import React, { createContext, useCallback, useContext, useState } from 'react'

interface SidebarContextType {
  isSidebarVisible: boolean
  isCollapsed: boolean
  sidebarWidth: number
  setSidebarVisible: (visible: boolean) => void
  toggleCollapse: () => void
}

const SidebarContext = createContext<SidebarContextType | undefined>(undefined)

export const SidebarProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [isSidebarVisible, setIsSidebarVisible] = useState(false)

  const toggleCollapse = useCallback(() => {
    setIsCollapsed((prev) => !prev)
  }, [])

  const setSidebarVisible = useCallback((visible: boolean) => {
    setIsSidebarVisible(visible)
  }, [])

  const sidebarWidth = isCollapsed ? 64 : 212

  return (
    <SidebarContext.Provider
      value={{
        isSidebarVisible,
        isCollapsed,
        sidebarWidth,
        setSidebarVisible,
        toggleCollapse,
      }}
    >
      {children}
    </SidebarContext.Provider>
  )
}

export const useSidebar = (): SidebarContextType => {
  const context = useContext(SidebarContext)
  if (!context) {
    throw new Error('useSidebar must be used within SidebarProvider')
  }
  return context
}
