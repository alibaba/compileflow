import { App } from 'antd'
import type { ReactNode } from 'react'

// Wraps children in Ant Design's <App> so App.useApp() (message, modal,
// notification) returns functional instances during tests.
export function renderWithApp(children: ReactNode): ReactNode {
  return <App>{children}</App>
}
