const DESIGNER_VIEW_MODES = ['visual', 'xml', 'split'] as const

export type DesignerViewMode = (typeof DESIGNER_VIEW_MODES)[number]

export function isDesignerViewMode(value: string): value is DesignerViewMode {
  return DESIGNER_VIEW_MODES.some((mode) => mode === value)
}
