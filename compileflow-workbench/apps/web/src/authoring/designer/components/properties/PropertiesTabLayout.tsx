import { Alert, type AlertProps, Form } from 'antd'
import React, { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import './PropertiesTabLayout.css'

export interface PropertiesTabLayoutProps {
  title: string
  description: string
  alertType?: AlertProps['type']
  icon?: ReactNode
  children: ReactNode
  example?: {
    title?: string
    content: ReactNode
  }
  style?: React.CSSProperties
}

export const PropertiesTabLayout = React.memo(function PropertiesTabLayout({
  title,
  description,
  alertType = 'info',
  icon,
  children,
  example,
  style,
}: PropertiesTabLayoutProps) {
  const { t } = useTranslation()

  return (
    <div className="properties-tab-layout" style={style}>
      <Form layout="vertical">
        <Alert title={title} description={description} type={alertType} showIcon icon={icon} />

        {children}

        {example && (
          <Alert
            className="properties-tab-example"
            title={example.title || t('designer.props.exampleTitle')}
            description={example.content}
            type="success"
            showIcon
          />
        )}
      </Form>
    </div>
  )
})

PropertiesTabLayout.displayName = 'PropertiesTabLayout'
