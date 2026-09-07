import { UnorderedListOutlined } from '@ant-design/icons'
import { Anchor } from 'antd'
import React, { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import styles from './TableOfContents.module.css'

interface TableOfContentsProps {
  contentRef: React.RefObject<HTMLElement | null>
}

interface TocItem {
  id: string
  title: string
  level: number
  element: Element
}

const TableOfContents: React.FC<TableOfContentsProps> = ({ contentRef }) => {
  const { t } = useTranslation()
  const [tocItems, setTocItems] = useState<TocItem[]>([])
  const [activeId, setActiveId] = useState<string>('')
  const idPrefix = useId()
  const nextHeadingId = useRef(0)

  useEffect(() => {
    if (!contentRef.current) return

    const content = contentRef.current
    let headingObserver: IntersectionObserver | undefined

    const rebuild = () => {
      headingObserver?.disconnect()
      const activePanel = content.querySelector('[role="tabpanel"][aria-hidden="false"]')
      const headings = activePanel?.querySelectorAll('h1, h2, h3, h4') ?? []
      const items = Array.from(headings, (heading) => {
        const id = heading.id || `${idPrefix}-heading-${nextHeadingId.current++}`
        if (!heading.id) heading.id = id
        return {
          id,
          title: heading.textContent || '',
          level: Number(heading.tagName.substring(1)),
          element: heading,
        }
      })
      setTocItems(items)
      setActiveId((current) => (items.some((item) => item.id === current) ? current : ''))

      headingObserver = new IntersectionObserver(
        (entries) => {
          const visibleHeading = entries.find((entry) => entry.isIntersecting)
          if (visibleHeading) setActiveId(visibleHeading.target.id)
        },
        { rootMargin: '-80px 0px -80% 0px', threshold: 0 }
      )
      headings.forEach((heading) => headingObserver?.observe(heading))
    }

    rebuild()
    const contentObserver = new MutationObserver(rebuild)
    contentObserver.observe(content, {
      attributeFilter: ['aria-hidden', 'class'],
      attributes: true,
      childList: true,
      characterData: true,
      subtree: true,
    })

    return () => {
      contentObserver.disconnect()
      headingObserver?.disconnect()
    }
  }, [contentRef, idPrefix])

  if (tocItems.length === 0) {
    return null
  }

  const anchorItems = tocItems.map((item) => ({
    key: item.id,
    href: `#${item.id}`,
    title: (
      <span className={item.level === 2 ? styles.linkLevel2 : styles.linkLevel3}>{item.title}</span>
    ),
  }))

  return (
    <nav className={styles.panel} aria-label={t('toc.title')}>
      <h2 className={styles.title}>
        <UnorderedListOutlined />
        {t('toc.title')}
      </h2>
      <Anchor
        affix={false}
        targetOffset={80}
        getCurrentAnchor={() => `#${activeId}`}
        items={anchorItems}
        onClick={(e, link) => {
          e.preventDefault()
          const element = tocItems.find((item) => `#${item.id}` === link.href)?.element
          if (
            element &&
            contentRef.current?.contains(element) &&
            element.closest('[role="tabpanel"]')?.getAttribute('aria-hidden') === 'false'
          ) {
            element.scrollIntoView({
              behavior: 'smooth',
              block: 'start',
            })
          }
        }}
      />
    </nav>
  )
}

export default TableOfContents
