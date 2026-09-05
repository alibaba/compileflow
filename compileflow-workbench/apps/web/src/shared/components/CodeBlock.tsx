import { CheckOutlined, CopyOutlined, DownloadOutlined } from '@ant-design/icons'
import { App, Button, Tag, Tooltip } from 'antd'
import React, { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import oneLight from 'react-syntax-highlighter/dist/esm/styles/prism/one-light'
import vscDarkPlus from 'react-syntax-highlighter/dist/esm/styles/prism/vsc-dark-plus'

import { useTheme } from '../contexts/ThemeContext'

import SyntaxHighlighter from './syntaxHighlighter'

interface CodeBlockProps {
  code: string
  language?: string
  filename?: string
  showLineNumbers?: boolean
  maxHeight?: number | string
}

const LANGUAGE_LABELS: Record<string, string> = {
  xml: 'XML',
  bpmn: 'BPMN',
  json: 'JSON',
  java: 'Java',
  javascript: 'JavaScript',
  typescript: 'TypeScript',
  sql: 'SQL',
  yaml: 'YAML',
}

interface CodeBlockThemeStyles {
  headerBg: string
  headerBorder: string
  filenameColor: string
  actionColor: string
  actionColorCopied: string
  lineNumberColor: string
  tagColor: 'blue' | 'default'
}

function codeBlockThemeStyles(isDark: boolean): CodeBlockThemeStyles {
  return {
    headerBg: isDark ? '#1e1e1e' : '#f3f4f6',
    headerBorder: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)',
    filenameColor: isDark ? '#9cdcfe' : 'var(--primary-600)',
    actionColor: isDark ? 'rgba(255,255,255,0.65)' : 'var(--text-secondary)',
    actionColorCopied: isDark ? '#73d13d' : 'var(--success-main)',
    lineNumberColor: isDark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.25)',
    tagColor: isDark ? 'blue' : 'default',
  }
}

function formatMaxHeight(maxHeight: number | string): string {
  return typeof maxHeight === 'number' ? `${maxHeight}px` : maxHeight
}

const CodeBlock: React.FC<CodeBlockProps> = ({
  code,
  language = 'xml',
  filename,
  showLineNumbers = true,
  maxHeight = '600px',
}) => {
  const { message } = App.useApp()
  const [copied, setCopied] = useState(false)
  const { t } = useTranslation()
  const { theme } = useTheme()
  const copiedTimer = useRef<number | undefined>(undefined)
  const isDark = theme === 'dark'
  const styles = codeBlockThemeStyles(isDark)

  useEffect(
    () => () => {
      if (copiedTimer.current !== undefined) clearTimeout(copiedTimer.current)
    },
    []
  )

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      message.success(t('code.copySuccess'))
      if (copiedTimer.current !== undefined) clearTimeout(copiedTimer.current)
      copiedTimer.current = window.setTimeout(() => {
        copiedTimer.current = undefined
        setCopied(false)
      }, 2000)
    } catch {
      message.error(t('code.copyFailed'))
    }
  }

  const handleDownload = () => {
    const blob = new Blob([code], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename || `code.${language === 'xml' ? 'xml' : 'txt'}`
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
    URL.revokeObjectURL(url)
    message.success(t('code.downloadSuccess'))
  }

  const langLabel = LANGUAGE_LABELS[language.toLowerCase()] || language.toUpperCase()

  return (
    <div
      style={{
        position: 'relative',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
        border: `1px solid ${styles.headerBorder}`,
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: 'var(--spacing-2) var(--spacing-4)',
          background: styles.headerBg,
          borderBottom: `1px solid ${styles.headerBorder}`,
          gap: 'var(--spacing-2)',
        }}
      >
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-2)', minWidth: 0 }}
        >
          <Tag
            color={styles.tagColor}
            style={{
              margin: 0,
              fontFamily: 'var(--font-family-code)',
              fontSize: 11,
              flexShrink: 0,
            }}
          >
            {langLabel}
          </Tag>
          {filename && (
            <span
              style={{
                color: styles.filenameColor,
                fontSize: 'var(--font-size-sm)',
                fontWeight: 'var(--font-weight-medium)',
                fontFamily: 'var(--font-family-code)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {filename}
            </span>
          )}
        </div>

        <div style={{ display: 'flex', gap: 'var(--spacing-1)', flexShrink: 0 }}>
          <Tooltip title={t('code.copy')}>
            <Button
              type="text"
              size="small"
              icon={copied ? <CheckOutlined /> : <CopyOutlined />}
              onClick={() => void handleCopy()}
              style={{
                color: copied ? styles.actionColorCopied : styles.actionColor,
                border: 'none',
              }}
              aria-label={t('code.copy')}
            />
          </Tooltip>
          <Tooltip title={t('code.download')}>
            <Button
              type="text"
              size="small"
              icon={<DownloadOutlined />}
              onClick={handleDownload}
              style={{ color: styles.actionColor, border: 'none' }}
              aria-label={t('code.download')}
            />
          </Tooltip>
        </div>
      </div>

      <SyntaxHighlighter
        language={language}
        style={isDark ? vscDarkPlus : oneLight}
        showLineNumbers={showLineNumbers}
        customStyle={{
          margin: 0,
          borderRadius: 0,
          maxHeight: formatMaxHeight(maxHeight),
          fontSize: 'var(--font-size-sm)',
        }}
        lineNumberStyle={{
          minWidth: '3em',
          paddingRight: '1em',
          color: styles.lineNumberColor,
          userSelect: 'none',
        }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  )
}

export default CodeBlock
