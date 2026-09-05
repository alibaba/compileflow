import type { ParseResult } from './xmlTypes'

export function validateXmlInput(xml: string): ParseResult<string> {
  if (!xml || typeof xml !== 'string') {
    return {
      success: false,
      error: { code: 'INVALID_INPUT', message: 'XML input must be a non-empty string' },
    }
  }

  const MAX_XML_SIZE = 10 * 1024 * 1024
  const xmlSize = new TextEncoder().encode(xml).byteLength
  if (xmlSize > MAX_XML_SIZE) {
    return {
      success: false,
      error: {
        code: 'XML_TOO_LARGE',
        message: `XML size ${xmlSize} bytes exceeds limit ${MAX_XML_SIZE} bytes`,
      },
    }
  }

  for (const char of xml) {
    const codePoint = char.codePointAt(0)
    if (codePoint === undefined || !isXml10Character(codePoint)) {
      return {
        success: false,
        error: {
          code: 'INVALID_XML_CHARACTER',
          message: `XML contains an invalid XML 1.0 character at code point U+${codePoint?.toString(16).toUpperCase() ?? 'UNKNOWN'}`,
        },
      }
    }
  }

  // Reject DOCTYPE unconditionally (XXE prevention).
  if (/<!DOCTYPE/i.test(xml)) {
    return {
      success: false,
      error: {
        code: 'DOCTYPE_FORBIDDEN',
        message: 'DOCTYPE declarations are not allowed in flow XML',
      },
    }
  }

  // Reject any entity declaration — even a single <!ENTITY can be the seed of an
  // exponential-expansion attack when combined with nested references.
  if (/<!ENTITY/i.test(xml)) {
    return {
      success: false,
      error: {
        code: 'ENTITY_FORBIDDEN',
        message: 'ENTITY declarations are not allowed in flow XML',
      },
    }
  }

  return { success: true, data: xml }
}

function isXml10Character(codePoint: number): boolean {
  return (
    codePoint === 0x09 ||
    codePoint === 0x0a ||
    codePoint === 0x0d ||
    (codePoint >= 0x20 && codePoint <= 0xd7ff) ||
    (codePoint >= 0xe000 && codePoint <= 0xfffd) ||
    (codePoint >= 0x10000 && codePoint <= 0x10ffff)
  )
}
