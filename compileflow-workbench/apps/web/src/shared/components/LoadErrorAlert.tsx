import { Alert, Button } from 'antd'
import { useTranslation } from 'react-i18next'

export function LoadErrorAlert({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <Alert
      type="error"
      showIcon
      title={t('error.loadFailed')}
      action={<Button onClick={onRetry}>{t('common.retry')}</Button>}
    />
  )
}
