import {
  BulbOutlined,
  LinkOutlined,
  NodeIndexOutlined,
  QuestionCircleOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import type { CollapseProps } from 'antd'
import { Divider, Space, Typography } from 'antd'
import type { TFunction } from 'i18next'
import type { ReactNode } from 'react'

const { Title, Paragraph, Text, Link } = Typography

interface HelpSection {
  key: string
  icon: ReactNode
  titleKey: string
  children: ReactNode
}

interface LinkItem {
  href: string
  labelKey: string
  descKey: string
}

function SectionLabel({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <Space>
      {icon}
      <span className="help-section-title">{title}</span>
    </Space>
  )
}

function BulletList({ items, ordered = false }: { items: string[]; ordered?: boolean }) {
  const ListTag = ordered ? 'ol' : 'ul'

  return (
    <ListTag style={{ paddingLeft: 20 }}>
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ListTag>
  )
}

function HelpBlock({ children, title }: { children: ReactNode; title: string }) {
  return (
    <div>
      <Title level={5}>{title}</Title>
      {children}
    </div>
  )
}

function QuickStartContent({ t }: { t: TFunction }) {
  return (
    <Space vertical size="middle" style={{ width: '100%' }}>
      <HelpBlock title={t('designer.help.quickStart.step1.title')}>
        <Paragraph>
          <BulletList
            ordered
            items={[
              t('designer.help.quickStart.step1.li1'),
              t('designer.help.quickStart.step1.li2'),
              t('designer.help.quickStart.step1.li3'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.quickStart.step2.title')}>
        <Paragraph>
          <BulletList
            items={[
              t('designer.help.quickStart.step2.li1'),
              t('designer.help.quickStart.step2.li2'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.quickStart.step3.title')}>
        <Paragraph>
          <BulletList
            items={[
              t('designer.help.quickStart.step3.li1'),
              t('designer.help.quickStart.step3.li2'),
              t('designer.help.quickStart.step3.li3'),
              t('designer.help.quickStart.step3.li4'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.quickStart.step4.title')}>
        <Paragraph>
          <BulletList
            items={[
              t('designer.help.quickStart.step4.li1'),
              t('designer.help.quickStart.step4.li2'),
              t('designer.help.quickStart.step4.li3'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.quickStart.step5.title')}>
        <Paragraph>
          <BulletList
            items={[
              t('designer.help.quickStart.step5.li1'),
              t('designer.help.quickStart.step5.li2'),
              t('designer.help.quickStart.step5.li3'),
            ]}
          />
        </Paragraph>
      </HelpBlock>
    </Space>
  )
}

function NodeTypeGroup({ items, title }: { items: string[]; title: string }) {
  return (
    <div>
      <Title level={5}>{title}</Title>
      <BulletList items={items} />
    </div>
  )
}

function NodeTypesContent({ t }: { t: TFunction }) {
  const groups = [
    {
      title: t('designer.help.nodeTypes.flowControl'),
      items: [t('designer.help.nodeTypes.start'), t('designer.help.nodeTypes.end')],
    },
    {
      title: t('designer.help.nodeTypes.tasks'),
      items: [
        t('designer.help.nodeTypes.autoTask'),
        t('designer.help.nodeTypes.waitTask'),
        t('designer.help.nodeTypes.waitEvent'),
        t('designer.help.nodeTypes.timerTask'),
        t('designer.help.nodeTypes.scriptTask'),
      ],
    },
    {
      title: t('designer.help.nodeTypes.gateways'),
      items: [
        t('designer.help.nodeTypes.exclusive'),
        t('designer.help.nodeTypes.parallel'),
        t('designer.help.nodeTypes.inclusive'),
      ],
    },
    {
      title: t('designer.help.nodeTypes.subprocess'),
      items: [
        t('designer.help.nodeTypes.subBpm'),
        t('designer.help.nodeTypes.bpmCall'),
        t('designer.help.nodeTypes.while'),
        t('designer.help.nodeTypes.foreach'),
      ],
    },
    {
      title: t('designer.help.nodeTypes.loopControl'),
      items: [t('designer.help.nodeTypes.continue'), t('designer.help.nodeTypes.break')],
    },
    {
      title: t('designer.help.nodeTypes.other'),
      items: [t('designer.help.nodeTypes.note')],
    },
  ]

  return (
    <Space vertical size="small" style={{ width: '100%' }}>
      {groups.map((group, index) => (
        <div key={group.title}>
          {index > 0 && <Divider style={{ margin: '12px 0' }} />}
          <NodeTypeGroup items={group.items} title={group.title} />
        </div>
      ))}
    </Space>
  )
}

function FaqContent({ t }: { t: TFunction }) {
  return (
    <Space vertical size="middle" style={{ width: '100%' }}>
      <HelpBlock title={t('designer.help.faq.branch.q')}>
        <Paragraph>
          <Text>{t('designer.help.faq.branch.a')}</Text>
          <br />
          <Text type="secondary">{t('designer.help.faq.branch.example')}</Text>
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.faq.panel.q')}>
        <Paragraph>
          <Text>{t('designer.help.faq.panel.a')}</Text>
          <BulletList
            ordered
            items={[
              t('designer.help.faq.panel.li1'),
              t('designer.help.faq.panel.li2'),
              t('designer.help.faq.panel.li3'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.faq.variables.q')}>
        <Paragraph>
          <Text>{t('designer.help.faq.variables.a')}</Text>
          <br />
          <Text type="secondary">{t('designer.help.faq.variables.example')}</Text>
          <br />
          <Text type="secondary">{t('designer.help.faq.variables.tip')}</Text>
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.faq.validation.q')}>
        <Paragraph>
          <Text>{t('designer.help.faq.validation.a')}</Text>
          <BulletList
            items={[
              t('designer.help.faq.validation.li1'),
              t('designer.help.faq.validation.li2'),
              t('designer.help.faq.validation.li3'),
              t('designer.help.faq.validation.li4'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.faq.debug.q')}>
        <Paragraph>
          <Text>{t('designer.help.faq.debug.a')}</Text>
          <BulletList
            ordered
            items={[
              t('designer.help.faq.debug.li1'),
              t('designer.help.faq.debug.li2'),
              t('designer.help.faq.debug.li3'),
              t('designer.help.faq.debug.li4'),
              t('designer.help.faq.debug.li5'),
            ]}
          />
        </Paragraph>
      </HelpBlock>

      <HelpBlock title={t('designer.help.faq.rubberband.q')}>
        <Paragraph>
          <Text>{t('designer.help.faq.rubberband.a')}</Text>
          <br />
          <Text type="secondary">{t('designer.help.faq.rubberband.tip')}</Text>
        </Paragraph>
      </HelpBlock>
    </Space>
  )
}

function BestPracticesContent({ t }: { t: TFunction }) {
  const groups = [
    {
      title: t('designer.help.practices.naming.title'),
      items: [
        t('designer.help.practices.naming.li1'),
        t('designer.help.practices.naming.li2'),
        t('designer.help.practices.naming.li3'),
      ],
    },
    {
      title: t('designer.help.practices.design.title'),
      items: [
        t('designer.help.practices.design.li1'),
        t('designer.help.practices.design.li2'),
        t('designer.help.practices.design.li3'),
        t('designer.help.practices.design.li4'),
      ],
    },
    {
      title: t('designer.help.practices.performance.title'),
      items: [
        t('designer.help.practices.performance.li1'),
        t('designer.help.practices.performance.li2'),
        t('designer.help.practices.performance.li3'),
        t('designer.help.practices.performance.li4'),
      ],
    },
    {
      title: t('designer.help.practices.security.title'),
      items: [
        t('designer.help.practices.security.li1'),
        t('designer.help.practices.security.li2'),
        t('designer.help.practices.security.li3'),
        t('designer.help.practices.security.li4'),
      ],
    },
  ]

  return (
    <Space vertical size="middle" style={{ width: '100%' }}>
      {groups.map((group) => (
        <NodeTypeGroup key={group.title} items={group.items} title={group.title} />
      ))}
    </Space>
  )
}

function DocumentationLinksContent({ t }: { t: TFunction }) {
  const links: LinkItem[] = [
    {
      href: 'https://github.com/alibaba/compileflow',
      labelKey: 'designer.help.links.github',
      descKey: 'designer.help.links.githubDesc',
    },
    {
      href: 'https://github.com/alibaba/compileflow/blob/master/docs/zh/README.md',
      labelKey: 'designer.help.links.docsZh',
      descKey: 'designer.help.links.docsZhDesc',
    },
    {
      href: 'https://github.com/alibaba/compileflow/blob/master/docs/zh/node-support.md',
      labelKey: 'designer.help.links.nodeSupport',
      descKey: 'designer.help.links.nodeSupportDesc',
    },
    {
      href: 'https://github.com/alibaba/compileflow/issues',
      labelKey: 'designer.help.links.issues',
      descKey: 'designer.help.links.issuesDesc',
    },
  ]

  return (
    <Space vertical size="small" style={{ width: '100%' }}>
      {links.map((item) => (
        <div key={item.href}>
          <Link href={item.href} target="_blank" rel="noopener noreferrer">
            {t(item.labelKey)}
          </Link>
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            {t(item.descKey)}
          </Text>
        </div>
      ))}
    </Space>
  )
}

function buildSections(t: TFunction): HelpSection[] {
  return [
    {
      key: 'quick-start',
      icon: <RocketOutlined style={{ color: 'var(--color-primary)' }} />,
      titleKey: 'designer.help.section.quickStart',
      children: <QuickStartContent t={t} />,
    },
    {
      key: 'node-types',
      icon: <NodeIndexOutlined style={{ color: 'var(--success-main)' }} />,
      titleKey: 'designer.help.section.nodeTypes',
      children: <NodeTypesContent t={t} />,
    },
    {
      key: 'faq',
      icon: <QuestionCircleOutlined style={{ color: 'var(--warning-main)' }} />,
      titleKey: 'designer.help.section.faq',
      children: <FaqContent t={t} />,
    },
    {
      key: 'best-practices',
      icon: <BulbOutlined style={{ color: 'var(--warning-main)' }} />,
      titleKey: 'designer.help.section.bestPractices',
      children: <BestPracticesContent t={t} />,
    },
    {
      key: 'links',
      icon: <LinkOutlined style={{ color: 'var(--node-subprocess-main)' }} />,
      titleKey: 'designer.help.section.links',
      children: <DocumentationLinksContent t={t} />,
    },
  ]
}

export function buildHelpCollapseItems(t: TFunction): CollapseProps['items'] {
  return buildSections(t).map((section) => ({
    key: section.key,
    label: <SectionLabel icon={section.icon} title={t(section.titleKey)} />,
    children: section.children,
  }))
}
