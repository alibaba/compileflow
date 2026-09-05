import { App } from 'antd'
import { lazy } from 'react'
import { useTranslation } from 'react-i18next'

import { importXml } from '../store/editorSlice'

import { BaseDesignerCore, type BaseDesignerProps, withDesignerWrapper } from './BaseDesigner'
import NodePalette from './NodePalette'
import TbbpmCanvas from './TbbpmCanvas'

import { useAppDispatch } from '@/app/hooks'
import { toError } from '@/shared/errors'
import { DEFAULT_TBBPM_WITH_NODES_XML } from '@/shared/processes/tbbpmTemplates'

import '../theme/design-tokens.css'
import './TbbpmDesigner.css'

const TbbpmPropertiesPanel = lazy(() => import('./TbbpmPropertiesPanel'))

function TbbpmDesignerInner(props: BaseDesignerProps) {
  const { message } = App.useApp()
  const dispatch = useAppDispatch()
  const { t } = useTranslation()

  const handleLoadExample = async () => {
    try {
      await dispatch(importXml({ xml: DEFAULT_TBBPM_WITH_NODES_XML, type: 'TBBPM' })).unwrap()
      message.success(t('designer.toolbar.loadExampleSuccess'))
    } catch (err) {
      message.error(
        t('designer.toolbar.loadExampleFailed', {
          message: toError(err, t('designer.actions.xmlParseFailed')).message,
        })
      )
    }
  }

  return (
    <BaseDesignerCore
      {...props}
      layoutClassName="tbbpm-designer"
      renderPalette={(graph) => <NodePalette graph={graph} />}
      renderCanvas={(onGraphReady) => <TbbpmCanvas onGraphReady={onGraphReady} />}
      renderPropertiesPanel={() => <TbbpmPropertiesPanel />}
      onLoadExample={() => void handleLoadExample()}
    />
  )
}

export default withDesignerWrapper(TbbpmDesignerInner)
