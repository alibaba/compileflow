import { toExpressionVariables } from '../expressionVariables'

describe('toExpressionVariables', () => {
  it('preserves the canonical flow variable type', () => {
    expect(
      toExpressionVariables([
        {
          name: 'amount',
          type: 'java.math.BigDecimal',
          inOutType: 'inner',
          description: 'Order total',
        },
      ])
    ).toEqual([
      {
        name: 'amount',
        type: 'java.math.BigDecimal',
        description: 'Order total',
      },
    ])
  })

  it('omits unnamed variables and supplies a display type for blank types', () => {
    expect(
      toExpressionVariables([
        { name: ' ', type: 'java.lang.String', inOutType: 'inner' },
        { name: 'payload', type: ' ', inOutType: 'inner' },
      ])
    ).toEqual([{ name: 'payload', type: 'java.lang.Object', description: undefined }])
  })
})
