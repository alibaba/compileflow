import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import MarkdownRenderer from '../MarkdownRenderer'

vi.mock('@/shared/contexts/ThemeContext', () => ({ useTheme: () => ({ theme: 'light' }) }))

describe('MarkdownRenderer code semantics', () => {
  it('does not render raw HTML or executable links and isolates external tabs', () => {
    const { container } = render(
      <MarkdownRenderer
        content={[
          '<script>alert(1)</script>',
          '<img src=x onerror=alert(1)>',
          '[unsafe](javascript:alert%281%29)',
          '[data](data:text/html;base64,PHNjcmlwdD4=)',
          '[external](https://example.com)',
        ].join('\n\n')}
      />
    )
    expect(container.querySelector('script, img[onerror]')).toBeNull()
    expect(screen.getByText('unsafe')).not.toHaveAttribute(
      'href',
      expect.stringMatching(/^javascript:/)
    )
    expect(screen.getByText('data')).not.toHaveAttribute('href', expect.stringMatching(/^data:/))
    expect(screen.getByRole('link', { name: 'external' })).toHaveAttribute(
      'rel',
      'noopener noreferrer'
    )
  })
  it('keeps inline code inside the surrounding prose without a preformatted block', () => {
    const { container } = render(<MarkdownRenderer content={'Run `compile()` now.'} />)
    expect(screen.getByText('compile()', { selector: 'code' })).toBeInTheDocument()
    expect(container.querySelector('pre')).toBeNull()
  })

  it.each(['java', ''])('renders %s fenced code with one preformatted container', (language) => {
    const { container } = render(
      <MarkdownRenderer content={`\`\`\`${language}\ncompile();\n\`\`\``} />
    )
    expect(container.querySelectorAll('pre')).toHaveLength(1)
    expect(container.querySelector('pre')).toHaveTextContent('compile();')
    expect(container.querySelector('pre pre')).toBeNull()
  })
})
