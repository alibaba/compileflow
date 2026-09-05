import { renderHook } from '@testing-library/react'
import { ReactNode } from 'react'
import { Provider } from 'react-redux'
import { describe, expect, it } from 'vitest'

import { DesignerProvider, useDesignerContext } from '../../context'

import { store } from '@/app/store'

const createWrapper = () => {
  return ({ children }: { children: ReactNode }) => (
    <Provider store={store}>
      <DesignerProvider>{children}</DesignerProvider>
    </Provider>
  )
}

describe('DesignerContext', () => {
  describe('Context Provider', () => {
    it('should provide graphRef', () => {
      const { result } = renderHook(() => useDesignerContext(), {
        wrapper: createWrapper(),
      })

      expect(result.current).toBeDefined()
      expect(result.current.graphRef).toBeDefined()
      expect(result.current.graphRef.current).toBeNull()
    })

    it('should throw when used outside Provider', () => {
      const { result } = renderHook(() => {
        try {
          return useDesignerContext()
        } catch (e) {
          return e
        }
      })
      expect(result.current).toBeInstanceOf(Error)
    })
  })
})
