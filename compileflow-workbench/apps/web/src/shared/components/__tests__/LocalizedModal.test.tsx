import { render, screen } from '@testing-library/react'

import { LocalizedModal } from '../LocalizedModal'

describe('LocalizedModal', () => {
  it('localizes the close button accessible name', () => {
    render(<LocalizedModal open title="测试弹窗" onCancel={() => undefined} />)

    expect(screen.getByRole('button', { name: '关闭' })).toBeInTheDocument()
  })
})
