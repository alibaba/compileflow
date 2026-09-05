import { Typography } from 'antd'
import type { Components } from 'react-markdown'
import ReactMarkdown from 'react-markdown'
import vs from 'react-syntax-highlighter/dist/esm/styles/prism/vs'
import vscDarkPlus from 'react-syntax-highlighter/dist/esm/styles/prism/vsc-dark-plus'
import remarkGfm from 'remark-gfm'

import { useTheme } from '../contexts/ThemeContext'

import SyntaxHighlighter from './syntaxHighlighter'

const { Title, Paragraph } = Typography

interface MarkdownRendererProps {
  content: string
}

type SyntaxTheme = typeof vscDarkPlus

interface MarkdownCodeProps extends React.ComponentPropsWithoutRef<'code'> {
  node?: unknown
  inline?: boolean
}

const titleSpacing = {
  h1: { marginTop: 'var(--spacing-6)', marginBottom: 'var(--spacing-4)' },
  h2: { marginTop: 'var(--spacing-6)', marginBottom: 'var(--spacing-4)' },
  h3: { marginTop: 'var(--spacing-5)', marginBottom: 'var(--spacing-3)' },
  h4: { marginTop: 'var(--spacing-4)', marginBottom: 'var(--spacing-3)' },
} as const

const listStyle: React.CSSProperties = {
  paddingLeft: 'var(--spacing-6)',
  marginBottom: 'var(--spacing-4)',
  lineHeight: 'var(--line-height-relaxed)',
}

const blockquoteStyle: React.CSSProperties = {
  borderLeft: '4px solid var(--primary-500)',
  marginLeft: 0,
  marginRight: 0,
  background: 'var(--primary-bg)',
  borderRadius: '0 var(--radius-lg) var(--radius-lg) 0',
  padding: 'var(--spacing-3) var(--spacing-4)',
}

const tableStyle: React.CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
  border: '1px solid var(--border-color)',
}

const tableCellStyle: React.CSSProperties = {
  padding: 'var(--spacing-3) var(--spacing-4)',
  borderBottom: '1px solid var(--border-color)',
  color: 'var(--text-primary)',
}

const MarkdownCode: React.FC<MarkdownCodeProps & { codeHighlightStyle: SyntaxTheme }> = ({
  inline,
  className,
  children,
  codeHighlightStyle,
  ...props
}) => {
  const match = /language-(\w+)/.exec(className || '')
  const language = match ? match[1] : ''

  if (inline) {
    return (
      <code
        style={{
          background: 'var(--code-bg)',
          padding: '2px 6px',
          borderRadius: 'var(--radius-sm)',
          fontSize: '0.9em',
          fontFamily: 'var(--font-family-code)',
          color: 'var(--error-main)',
        }}
        {...props}
      >
        {children}
      </code>
    )
  }

  return (
    <SyntaxHighlighter
      language={language}
      style={codeHighlightStyle}
      customStyle={{
        borderRadius: 'var(--radius-lg)',
        fontSize: 'var(--font-size-base)',
        margin: 'var(--spacing-4) 0',
      }}
    >
      {String(children).replace(/\n$/, '')}
    </SyntaxHighlighter>
  )
}

const createMarkdownComponents = (codeHighlightStyle: SyntaxTheme): Components => ({
  h1: ({ children }) => (
    <Title level={2} style={titleSpacing.h1}>
      {children}
    </Title>
  ),
  h2: ({ children }) => (
    <Title level={3} style={titleSpacing.h2}>
      {children}
    </Title>
  ),
  h3: ({ children }) => (
    <Title level={4} style={titleSpacing.h3}>
      {children}
    </Title>
  ),
  h4: ({ children }) => (
    <Title level={5} style={titleSpacing.h4}>
      {children}
    </Title>
  ),
  p: ({ children }) => (
    <Paragraph
      style={{ fontSize: 'var(--font-size-base)', lineHeight: 'var(--line-height-relaxed)' }}
    >
      {children}
    </Paragraph>
  ),
  code: (props) => <MarkdownCode {...props} codeHighlightStyle={codeHighlightStyle} />,
  ul: ({ children }) => <ul style={listStyle}>{children}</ul>,
  ol: ({ children }) => <ol style={listStyle}>{children}</ol>,
  li: ({ children }) => <li style={{ marginBottom: 'var(--spacing-2)' }}>{children}</li>,
  blockquote: ({ children }) => <blockquote style={blockquoteStyle}>{children}</blockquote>,
  table: ({ children }) => (
    <div style={{ overflow: 'auto', marginBottom: 'var(--spacing-4)' }}>
      <table style={tableStyle}>{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead style={{ background: 'var(--bg-secondary)' }}>{children}</thead>,
  th: ({ children }) => (
    <th
      style={{
        ...tableCellStyle,
        borderBottom: '2px solid var(--border-color)',
        fontWeight: 'var(--font-weight-semibold)' as React.CSSProperties['fontWeight'],
      }}
    >
      {children}
    </th>
  ),
  td: ({ children }) => <td style={tableCellStyle}>{children}</td>,
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="md-link">
      {children}
    </a>
  ),
  hr: () => (
    <hr
      style={{
        border: 'none',
        borderTop: '1px solid var(--border-color)',
        margin: 'var(--spacing-6) 0',
      }}
    />
  ),
})

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content }) => {
  const { theme } = useTheme()
  const codeHighlightStyle = theme === 'dark' ? vscDarkPlus : vs

  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={createMarkdownComponents(codeHighlightStyle)}
    >
      {content}
    </ReactMarkdown>
  )
}

export default MarkdownRenderer
