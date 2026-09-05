import { HomeOutlined } from '@ant-design/icons'
import { Button, Result } from 'antd'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { ROUTES } from '@/shared/constants'
import { usePageTitle } from '@/shared/hooks/usePageTitle'

const NotFoundPage: React.FC = () => {
  usePageTitle('pageTitle.notFound')
  const navigate = useNavigate()
  const { t } = useTranslation()

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
        status="404"
        title={<h1 style={{ margin: 0, fontSize: 'inherit' }}>404</h1>}
        subTitle={t('notFound.message')}
        extra={
          <Button type="primary" icon={<HomeOutlined />} onClick={() => navigate(ROUTES.LEARN)}>
            {t('notFound.home')}
          </Button>
        }
      />
    </div>
  )
}

export default NotFoundPage
