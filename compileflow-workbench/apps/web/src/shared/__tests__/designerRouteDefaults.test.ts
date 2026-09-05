import {
  buildDeploymentDetailPath,
  buildDesignerRoute,
  createLearnExampleDetailPath,
  isRouteWithin,
  normalizeDesignerRouteProcessType,
} from '@/shared/constants'
import {
  createNewDesignerEntry,
  parseDesignerEntryDescriptor,
} from '@/shared/services/designerNavigation'

describe('designer route type defaults', () => {
  it('defaults missing type to tbbpm (product primary format)', () => {
    expect(normalizeDesignerRouteProcessType(undefined)).toBe('tbbpm')
    expect(normalizeDesignerRouteProcessType(null)).toBe('tbbpm')
    expect(normalizeDesignerRouteProcessType('')).toBe('tbbpm')
  })

  it('preserves explicit bpmn / tbbpm (case-insensitive)', () => {
    expect(normalizeDesignerRouteProcessType('BPMN')).toBe('bpmn')
    expect(normalizeDesignerRouteProcessType('tbbpm')).toBe('tbbpm')
    expect(normalizeDesignerRouteProcessType(' TBBPM ')).toBe('tbbpm')
  })

  it('rejects an explicit unsupported process type', () => {
    expect(() => normalizeDesignerRouteProcessType('unknown')).toThrow(
      'Unsupported designer process type: unknown'
    )
  })

  it('bare /build/designer entry without model type opens TBBPM new process', () => {
    const entry = parseDesignerEntryDescriptor(new URLSearchParams())
    expect(entry.source).toBe('new')
    expect(entry.modelType).toBe('tbbpm')
  })

  it('createNewDesignerEntry without options is TBBPM', () => {
    expect(createNewDesignerEntry().modelType).toBe('tbbpm')
  })

  it('buildDesignerRoute without model type emits modelType=tbbpm', () => {
    expect(buildDesignerRoute()).toContain('modelType=tbbpm')
  })

  it('encodes resource ids as one path segment', () => {
    expect(createLearnExampleDetailPath('folder/example #1')).toBe(
      '/learn/examples/folder%2Fexample%20%231'
    )
    expect(buildDeploymentDetailPath('deploy/production #1')).toBe(
      '/operate/deployments/deploy%2Fproduction%20%231'
    )
  })

  it('matches complete route segments instead of lookalike prefixes', () => {
    expect(isRouteWithin('/learn/examples', '/learn')).toBe(true)
    expect(isRouteWithin('/learn-anything', '/learn')).toBe(false)
  })
})
