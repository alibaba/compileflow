import { HomeOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button, Result } from 'antd'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { ROUTES } from '@/shared/constants'
import { usePageTitle } from '@/shared/hooks/usePageTitle'

const ServerErrorPage: React.FC = () => {
  usePageTitle('pageTitle.serverError')
  const navigate = useNavigate()
  const { t } = useTranslation()

  const handleReload = () => {
    window.location.reload()
  }

  return (
    <div
      style={{
        flex: 1,
        minHeight: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg-secondary)',
      }}
    >
      <Result
        status="500"
        title={<h1 style={{ margin: 0, fontSize: 'inherit' }}>500</h1>}
        subTitle={t('serverError.message')}
        extra={[
          <Button type="primary" icon={<ReloadOutlined />} onClick={handleReload} key="reload">
            {t('serverError.reload')}
          </Button>,
          <Button icon={<HomeOutlined />} onClick={() => navigate(ROUTES.LEARN)} key="home">
            {t('serverError.home')}
          </Button>,
        ]}
      />
    </div>
  )
}

export default ServerErrorPage
