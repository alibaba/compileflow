import { GlobalOutlined, QuestionCircleOutlined, SettingOutlined } from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { Button, Dropdown } from 'antd'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { ROUTES } from '@/shared/constants'
import { useLanguage } from '@/shared/contexts/LanguageContext'

const DOCUMENTATION_URL = 'https://github.com/alibaba/compileflow/tree/master/docs'

export function AppMenu() {
  const { t } = useTranslation()
  const { language, setLanguage } = useLanguage()

  const items: MenuProps['items'] = [
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: <Link to={ROUTES.SETTINGS}>{t('appMenu.settings')}</Link>,
    },
    {
      key: 'documentation',
      icon: <QuestionCircleOutlined />,
      label: (
        <a href={DOCUMENTATION_URL} target="_blank" rel="noopener noreferrer">
          {t('appMenu.documentation')}
        </a>
      ),
    },
    { type: 'divider' },
    {
      key: 'language',
      icon: <GlobalOutlined />,
      label: t('appMenu.language'),
      children: [
        {
          key: 'lang-zh',
          label: `中文${language === 'zh' ? ' ✓' : ''}`,
        },
        {
          key: 'lang-en',
          label: `English${language === 'en' ? ' ✓' : ''}`,
        },
      ],
    },
  ]

  const handleClick: MenuProps['onClick'] = ({ key }) => {
    switch (key) {
      case 'lang-zh':
        setLanguage('zh')
        break
      case 'lang-en':
        setLanguage('en')
        break
      default:
        break
    }
  }

  return (
    <Dropdown menu={{ items, onClick: handleClick }} placement="bottomRight" trigger={['click']}>
      <Button type="text" icon={<SettingOutlined />} aria-label={t('appMenu.label')} />
    </Dropdown>
  )
}
