import { Spin } from 'antd'
import { useTranslation } from 'react-i18next'

import './LoadingFeedback.css'

export function SuspenseFallback({ text }: { text?: string }) {
  const { t } = useTranslation()
  return (
    <div className="suspense-fallback">
      <Spin description={text ?? t('designer.loading.suspense')} />
    </div>
  )
}
