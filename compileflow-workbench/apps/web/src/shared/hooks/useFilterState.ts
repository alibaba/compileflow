import { useCallback, useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

type FilterValue = string | number | undefined

export type FilterParsers<T extends { [K in keyof T]: FilterValue }> = Partial<{
  [K in keyof T]: (raw: string) => T[K]
}>

export type FilterUpdater<T extends { [K in keyof T]: FilterValue }> = <K extends keyof T>(
  key: K,
  value: T[K]
) => void

export type FilterBatchUpdater<T extends { [K in keyof T]: FilterValue }> = (
  values: Partial<T>
) => void

/**
 * Parse a URL search param using an explicit field parser or an unambiguous default.
 *
 * - Numeric defaults always parse as numbers.
 * - String defaults (including `''`) always remain strings.
 * - `undefined` defaults require a parser because they carry no runtime type information.
 */
function parseFilterValue<T extends FilterValue>(
  key: PropertyKey,
  raw: string,
  defaultVal: T,
  parser?: (raw: string) => T
): T {
  if (parser) return parser(raw)
  if (typeof defaultVal === 'number') {
    const parsed = Number(raw)
    return (Number.isFinite(parsed) ? parsed : defaultVal) as T
  }
  if (typeof defaultVal === 'string') {
    return raw as T
  }
  throw new Error(`Filter "${String(key)}" with an undefined default requires a parser`)
}

export function useFilterState<T extends { [K in keyof T]: FilterValue }>(
  defaults: T,
  parsers?: FilterParsers<T>
): [T, FilterUpdater<T>, () => void, FilterBatchUpdater<T>] {
  const [searchParams, setSearchParams] = useSearchParams()

  const filterState = useMemo((): T => {
    const state = { ...defaults }
    for (const key of Object.keys(defaults) as Array<keyof T>) {
      const raw = searchParams.get(String(key))
      if (raw !== null) {
        state[key] = parseFilterValue(key, raw, defaults[key], parsers?.[key])
      }
    }
    return state
  }, [defaults, parsers, searchParams])

  useEffect(() => {
    const normalized = new URLSearchParams(searchParams)
    let changed = false
    for (const key of Object.keys(defaults) as Array<keyof T>) {
      const param = String(key)
      const raw = searchParams.get(param)
      if (raw === null) continue
      const value = filterState[key]
      const canonical =
        value === defaults[key] || value === undefined || value === '' ? null : String(value)
      if (canonical === raw) continue
      changed = true
      if (canonical === null) normalized.delete(param)
      else normalized.set(param, canonical)
    }
    if (changed) setSearchParams(normalized, { replace: true })
  }, [defaults, filterState, searchParams, setSearchParams])

  const updateFilter = useCallback(
    <K extends keyof T>(key: K, value: T[K]) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          const defaultVal = defaults[key]
          const isDefault = value === defaultVal || value === undefined || value === ''
          if (isDefault) {
            next.delete(String(key))
          } else {
            next.set(String(key), String(value))
          }
          return next
        },
        { replace: true }
      )
    },
    [setSearchParams, defaults]
  )

  const clearAll = useCallback(() => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const key of Object.keys(defaults)) next.delete(key)
        return next
      },
      { replace: true }
    )
  }, [defaults, setSearchParams])

  const updateFilters = useCallback(
    (values: Partial<T>) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const key of Object.keys(values) as Array<keyof T>) {
            const value = values[key]
            const defaultVal = defaults[key]
            const isDefault = value === defaultVal || value === undefined || value === ''
            if (isDefault) next.delete(String(key))
            else next.set(String(key), String(value))
          }
          return next
        },
        { replace: true }
      )
    },
    [defaults, setSearchParams]
  )

  return [filterState, updateFilter, clearAll, updateFilters]
}
