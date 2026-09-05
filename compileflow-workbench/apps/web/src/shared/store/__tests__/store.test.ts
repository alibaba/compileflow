import { setBreadcrumbs, store } from '@/app/store'

describe('Redux Store', () => {
  describe('navigation slice', () => {
    it('should set breadcrumbs', () => {
      const breadcrumbs = [
        { labelKey: 'nav.learn', path: '/learn' },
        { labelKey: 'nav.learn.examples', path: '/learn/examples' },
      ]

      store.dispatch(setBreadcrumbs(breadcrumbs))

      const state = store.getState()
      expect(state.navigation.breadcrumbs).toEqual(breadcrumbs)
    })
  })
})
