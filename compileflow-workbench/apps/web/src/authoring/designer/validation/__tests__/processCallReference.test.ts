import { describe, expect, test } from 'vitest'

import { processCallReferenceIssue } from '../processCallReference'

describe('processCallReferenceIssue', () => {
  test.each(['child.bpm', 'flows/child.bpm', 'flows/child process.bpm'])(
    'accepts canonical Process classpath location %s',
    (classpath) => {
      expect(processCallReferenceIssue({ code: 'child', classpath })).toBeUndefined()
    }
  )

  test.each([
    './child.bpm',
    '../shared/child.bpm',
    '/flows/child.bpm',
    'classpath:/flows/child.bpm',
    'file:/workspace/flows/child.bpm',
    'flows//child.bpm',
    'flows/./child.bpm',
    'flows/*/child.bpm',
    'child\\child.bpm',
    ' child.bpm',
  ])('rejects a non-canonical Process classpath location: %s', (classpath) => {
    expect(processCallReferenceIssue({ code: 'child', classpath })).toBe('invalidReference')
  })

  test('requires exactly one classpath or version target', () => {
    expect(processCallReferenceIssue({ code: 'child' })).toBe('targetRequired')
    expect(
      processCallReferenceIssue({ code: 'child', classpath: 'flows/child.bpm', version: 'v1' })
    ).toBe('targetConflict')
    expect(processCallReferenceIssue({ code: 'child', version: 'v1' })).toBeUndefined()
  })
})
