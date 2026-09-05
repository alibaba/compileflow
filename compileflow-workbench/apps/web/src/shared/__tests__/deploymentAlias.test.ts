import { describe, expect, it } from 'vitest'

import { DEPLOYMENT_ALIAS_PRESETS, getDeploymentAliasPreset } from '../constants'

describe('deployment alias truth source', () => {
  it('keeps deployment alias values aligned with operate runtime payloads', () => {
    expect(DEPLOYMENT_ALIAS_PRESETS.map(({ value }) => value)).toEqual([
      'dev',
      'staging',
      'production',
    ])
  })

  it('resolves alias metadata from shared constants', () => {
    expect(getDeploymentAliasPreset('staging')).toMatchObject({
      value: 'staging',
      labelKey: 'deployment.alias.staging',
    })
  })

  it('does not resolve unsupported alias values', () => {
    expect(getDeploymentAliasPreset('unknown')).toBeUndefined()
  })
})
