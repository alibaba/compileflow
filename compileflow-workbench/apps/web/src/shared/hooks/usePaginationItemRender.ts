import type { PaginationProps } from 'antd'
import { cloneElement, isValidElement, useCallback } from 'react'
import { useTranslation } from 'react-i18next'

type ItemRender = NonNullable<PaginationProps['itemRender']>

export function usePaginationItemRender(): ItemRender {
  const { t } = useTranslation()

  return useCallback<ItemRender>(
    (_page, type, element) => {
      if (
        (type !== 'prev' && type !== 'next') ||
        !isValidElement<{ 'aria-label'?: string }>(element)
      ) {
        return element
      }

      return cloneElement(element, {
        'aria-label': t(type === 'prev' ? 'common.previousPage' : 'common.nextPage'),
      })
    },
    [t]
  )
}
