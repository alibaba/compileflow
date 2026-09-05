import { BugOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button, Result } from 'antd'
import { Component, type ErrorInfo, type ReactNode } from 'react'

import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import i18n from '@/shared/i18n'
import { createLogger } from '@/shared/logging/logger'
import './DesignerErrorBoundary.css'

const logger = createLogger('ErrorBoundary')

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
}

export class DesignerErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    }
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return {
      hasError: true,
      error,
    }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // 记录错误信息
    logger.error('Error caught', error, { componentStack: errorInfo.componentStack ?? '' })

    // 更新状态
    this.setState({
      error,
      errorInfo,
    })

    // 在开发环境显示详细信息
    if (APP_BUILD_CONFIG.buildMode === 'development') {
      logger.error('Error details', error)
    }
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    })
  }

  handleReload = () => {
    window.location.reload()
  }

  render() {
    const { hasError, error, errorInfo } = this.state
    const { children } = this.props

    if (hasError && error) {
      // 默认错误UI
      return (
        <div className="error-boundary-container">
          <Result
            status="error"
            title={i18n.t('designer.errorBoundary.title')}
            subTitle={
              <div>
                <p>{error.message || i18n.t('designer.errorBoundary.unknown')}</p>
                {APP_BUILD_CONFIG.buildMode === 'development' && errorInfo && (
                  <details className="error-boundary-details">
                    <summary className="error-boundary-summary">
                      <BugOutlined /> {i18n.t('designer.errorBoundary.devInfo')}
                    </summary>
                    <pre className="error-boundary-stack">{error.stack}</pre>
                    <pre className="error-boundary-component-stack">{errorInfo.componentStack}</pre>
                  </details>
                )}
              </div>
            }
            extra={[
              <Button
                key="reset"
                type="primary"
                onClick={this.handleReset}
                icon={<ReloadOutlined />}
              >
                {i18n.t('designer.errorBoundary.retry')}
              </Button>,
              <Button key="reload" onClick={this.handleReload}>
                {i18n.t('designer.errorBoundary.reload')}
              </Button>,
            ]}
          />
        </div>
      )
    }

    return children
  }
}
