// ==================== Learn Domain ====================

import type { RefinedServerSchema } from './serverSchema'

type ExampleCategory = 'basics' | 'business' | 'advanced'

export type Example = RefinedServerSchema<
  'ExampleResponse',
  {
    category: ExampleCategory
  }
>
