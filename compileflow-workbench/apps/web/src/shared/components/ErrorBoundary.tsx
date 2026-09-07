import { HomeOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button, Result } from 'antd'
import { Component, ErrorInfo, ReactNode } from 'react'

import { handleComponentError } from '@/shared/api/errorHandler'
import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import i18n from '@/shared/i18n'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    }
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    // 更新state以便下次渲染时显示备用UI
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ error, errorInfo })
    handleComponentError(error, errorInfo)
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    })
  }

  handleGoHome = () => {
    window.location.href = '/'
  }

  render() {
    const { hasError, error, errorInfo } = this.state
    const { children } = this.props

    if (hasError) {
      // 默认错误UI
      return (
        <div
          style={{
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '24px',
            background: 'var(--bg-secondary)',
          }}
        >
          <Result
            status="error"
            title={i18n.t('errorBoundary.title')}
            subTitle={i18n.t('errorBoundary.description')}
            extra={[
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={this.handleReset}
                key="retry"
              >
                {i18n.t('errorBoundary.retry')}
              </Button>,
              <Button icon={<HomeOutlined />} onClick={this.handleGoHome} key="home">
                {i18n.t('errorBoundary.home')}
              </Button>,
            ]}
          >
            {APP_BUILD_CONFIG.buildMode === 'development' && error && (
              <div
                style={{
                  textAlign: 'left',
                  padding: '16px',
                  background: 'var(--bg-primary)',
                  border: '1px solid var(--border-color)',
                  borderRadius: '8px',
                  marginTop: '24px',
                  maxWidth: '800px',
                }}
              >
                <details>
                  <summary
                    style={{
                      cursor: 'pointer',
                      fontWeight: 600,
                      marginBottom: '12px',
                      color: 'var(--error-main)',
                    }}
                  >
                    {i18n.t('errorBoundary.details')}
                  </summary>
                  <div
                    style={{
                      fontFamily: 'monospace',
                      fontSize: '12px',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}
                  >
                    <strong>{i18n.t('errorBoundary.errorLabel')}</strong> {error.toString()}
                    {errorInfo && (
                      <>
                        <br />
                        <br />
                        <strong>{i18n.t('errorBoundary.componentStackLabel')}</strong>
                        {errorInfo.componentStack}
                      </>
                    )}
                  </div>
                </details>
              </div>
            )}
          </Result>
        </div>
      )
    }

    return children
  }
}

export default ErrorBoundary
