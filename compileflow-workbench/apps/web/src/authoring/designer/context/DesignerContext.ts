import type { Graph } from '@antv/x6'
import { createContext } from 'react'

export interface DesignerContextValue {
  graphRef: React.MutableRefObject<Graph | null>
}

export const DesignerContext = createContext<DesignerContextValue | null>(null)

DesignerContext.displayName = 'DesignerContext'
