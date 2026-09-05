import { describe, expect, it } from 'vitest'

import { validateXmlInput } from '../xmlInputValidation'

describe('validateXmlInput', () => {
  it('preserves valid XML including supplementary Unicode characters', () => {
    const xml = '<definitions name="flow-\u{1F680}" />'

    expect(validateXmlInput(xml)).toEqual({
      success: true,
      data: xml,
    })
  })

  it('rejects declarations that can introduce external or expanded entities', () => {
    const xml =
      '<!DOCTYPE definitions [<!ENTITY secret SYSTEM "file:///etc/passwd">]><definitions />'

    expect(validateXmlInput(xml)).toMatchObject({
      success: false,
      error: { code: 'DOCTYPE_FORBIDDEN' },
    })
  })

  it('rejects invalid XML 1.0 characters instead of silently changing the definition', () => {
    expect(validateXmlInput('<definitions>\u0000</definitions>')).toMatchObject({
      success: false,
      error: { code: 'INVALID_XML_CHARACTER' },
    })
  })

  it('enforces the payload limit in UTF-8 bytes', () => {
    const xml = `<!--${'\u4E2D'.repeat(3_495_253)}-->`

    expect(xml.length).toBeLessThan(10 * 1024 * 1024)
    expect(validateXmlInput(xml)).toMatchObject({
      success: false,
      error: { code: 'XML_TOO_LARGE' },
    })
  })
})
