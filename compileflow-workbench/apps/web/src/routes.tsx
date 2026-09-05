import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'

import App from './App'

import { ROUTES } from '@/shared/constants'

const HomePage = lazy(() => import('@/learn/pages/HomePage'))
const ExampleList = lazy(() => import('@/learn/pages/ExampleList'))
const ExampleDetail = lazy(() => import('@/learn/pages/ExampleDetail'))
const BuildWorkspace = lazy(() => import('@/authoring/pages/Workspace'))
const UnifiedDesigner = lazy(() => import('@/authoring/pages/UnifiedDesigner'))
const OperateHome = lazy(() => import('@/operate/pages/OperateHome'))
const ProcessManagement = lazy(() => import('@/operate/pages/ProcessManagement'))
const DeploymentManagement = lazy(() => import('@/operate/pages/DeploymentManagement'))
const DeploymentWizard = lazy(() => import('@/operate/pages/DeploymentWizard'))
const Monitoring = lazy(() => import('@/operate/pages/Monitoring'))
const Logs = lazy(() => import('@/operate/pages/Logs'))
const DeploymentDetail = lazy(() => import('@/operate/pages/DeploymentDetail'))
const Settings = lazy(() => import('@/settings/pages/Settings'))

const NotFoundPage = lazy(() => import('@/shared/pages/NotFoundPage'))
const ServerErrorPage = lazy(() => import('@/shared/pages/ServerErrorPage'))

export const router = createBrowserRouter([
  {
    element: <App />,
    children: [
      { path: ROUTES.HOME, element: <Navigate to={ROUTES.LEARN} replace /> },
      { path: ROUTES.LEARN, element: <HomePage /> },
      { path: ROUTES.LEARN_EXAMPLES, element: <ExampleList /> },
      { path: ROUTES.LEARN_EXAMPLE_DETAIL, element: <ExampleDetail /> },
      { path: ROUTES.BUILD, element: <BuildWorkspace /> },
      { path: ROUTES.BUILD_DESIGNER, element: <UnifiedDesigner /> },
      { path: ROUTES.OPERATE, element: <OperateHome /> },
      { path: ROUTES.OPERATE_PROCESSES, element: <ProcessManagement /> },
      { path: ROUTES.OPERATE_DEPLOYMENTS, element: <DeploymentManagement /> },
      { path: ROUTES.OPERATE_DEPLOYMENT_DETAIL, element: <DeploymentDetail /> },
      { path: ROUTES.OPERATE_DEPLOY_WIZARD, element: <DeploymentWizard /> },
      { path: ROUTES.OPERATE_MONITORING, element: <Monitoring /> },
      { path: ROUTES.OPERATE_LOGS, element: <Logs /> },
      { path: ROUTES.SETTINGS, element: <Settings /> },
      { path: ROUTES.SERVER_ERROR, element: <ServerErrorPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
