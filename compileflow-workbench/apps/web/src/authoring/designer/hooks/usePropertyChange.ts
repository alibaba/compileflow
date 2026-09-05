import { useCallback, useEffect, useRef } from 'react'

import type { TbbpmNode } from '../types/tbbpm'

export function usePropertyChange(
  nodeId: string,
  onUpdate: (nodeId: string, updates: Partial<TbbpmNode['properties']>) => void
) {
  // Keep the latest node id and update callback available to stable handlers.
  const nodeIdRef = useRef(nodeId)
  const onUpdateRef = useRef(onUpdate)

  useEffect(() => {
    nodeIdRef.current = nodeId
  }, [nodeId])

  useEffect(() => {
    onUpdateRef.current = onUpdate
  }, [onUpdate])

  const handleChange = useCallback((field: string, value: unknown) => {
    onUpdateRef.current(nodeIdRef.current, { [field]: value })
  }, [])

  const handleBatchChange = useCallback((updates: Record<string, unknown>) => {
    onUpdateRef.current(nodeIdRef.current, updates)
  }, [])

  const handleSelectChange = useCallback(
    (field: string) => (value: unknown) => {
      handleChange(field, value)
    },
    [handleChange]
  )

  const handleInputChange = useCallback(
    (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      handleChange(field, e.target.value)
    },
    [handleChange]
  )

  const handleNumberChange = useCallback(
    (field: string, defaultValue?: number) => (value: number | null) => {
      handleChange(field, value ?? defaultValue)
    },
    [handleChange]
  )

  const handleBooleanChange = useCallback(
    (field: string) => (checked: boolean) => {
      handleChange(field, checked)
    },
    [handleChange]
  )

  return {
    handleChange,
    handleBatchChange,
    handleSelectChange,
    handleInputChange,
    handleNumberChange,
    handleBooleanChange,
  }
}
