export interface ParseResult<T> {
  success: boolean
  data?: T
  error?: ParseError
  warnings?: ParseWarning[]
}

interface ParseError {
  code: string
  message: string
  line?: number
  column?: number
  details?: unknown
}

export interface ParseWarning {
  code: string
  message: string
  location?: string
}

export interface ParseOptions {
  strict?: boolean
  validate?: boolean
  /** Enables schema diagnostics without rejecting otherwise parseable XML. */
  schemaValidate?: boolean
  preserveComments?: boolean
  preserveWhitespace?: boolean
  namespaces?: Record<string, string>
}

export interface GenerateOptions {
  format?: boolean
  indent?: string
  includeDeclaration?: boolean
  encoding?: string
  compress?: boolean
}
