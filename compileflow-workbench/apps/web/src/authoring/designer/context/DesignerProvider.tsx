import type { Graph } from '@antv/x6'
import React, { useContext, useMemo, useRef } from 'react'

import type { DesignerContextValue } from './DesignerContext'
import { DesignerContext } from './DesignerContext'

interface DesignerProviderProps {
  children: React.ReactNode
  graphRef?: React.MutableRefObject<Graph | null>
}

export function DesignerProvider({ children, graphRef: externalGraphRef }: DesignerProviderProps) {
  const internalGraphRef = useRef<Graph | null>(null)
  const graphRef = externalGraphRef ?? internalGraphRef
  const contextValue = useMemo<DesignerContextValue>(() => ({ graphRef }), [graphRef])

  return <DesignerContext.Provider value={contextValue}>{children}</DesignerContext.Provider>
}

export function useDesignerContext() {
  const context = useContext(DesignerContext)

  if (!context) {
    throw new Error('useDesignerContext must be used within DesignerProvider')
  }

  return context
}
