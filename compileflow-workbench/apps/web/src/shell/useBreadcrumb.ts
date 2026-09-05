import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

import { useAppDispatch } from '@/app/hooks'
import { setBreadcrumbs } from '@/app/store'
import { getBreadcrumbsForPath } from '@/shared/navigation/breadcrumbs'

export function useBreadcrumb() {
  const location = useLocation()
  const dispatch = useAppDispatch()

  useEffect(() => {
    dispatch(setBreadcrumbs(getBreadcrumbsForPath(location.pathname)))
  }, [dispatch, location.pathname])
}
