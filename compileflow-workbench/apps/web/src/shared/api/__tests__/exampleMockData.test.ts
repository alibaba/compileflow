import { describe, expect, it } from 'vitest'

import { listMockExamples } from '../exampleMockData'

import { parseBpmnXml } from '@/authoring/designer/serialization/bpmnXmlCodec'
import { parseTbbpmXml } from '@/authoring/designer/serialization/tbbpmXmlCodec'
import { validateDesignerProcess } from '@/authoring/designer/validation/designerProcessValidation'
import { LEARN_CATEGORIES } from '@/shared/constants'

describe('example catalog', () => {
  it('uses stable category identifiers understood by filters and routes', () => {
    const supportedCategories = new Set(Object.values(LEARN_CATEGORIES))

    for (const example of listMockExamples()) {
      expect(supportedCategories.has(example.category), example.id).toBe(true)
    }
  })

  it('contains protocol-valid, designer-valid executable models', () => {
    for (const example of listMockExamples()) {
      const parsed =
        example.modelType === 'BPMN'
          ? parseBpmnXml(example.code)
          : parseTbbpmXml(example.code, { validate: true, schemaValidate: true })

      expect(parsed.success, `${example.id}: ${parsed.error?.message}`).toBe(true)
      expect(parsed.data?.code, example.id).toBe(example.id)

      const validation = validateDesignerProcess(parsed.data!)
      expect(
        validation.issues.filter((issue) => issue.level === 'error'),
        example.id
      ).toEqual([])
    }
  })
})
