import { Collapse } from 'antd'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { buildHelpCollapseItems } from './helpDocumentationSections'

import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

import './DesignerSurfaces.css'
import './HelpDocumentation.css'

interface HelpDocumentationProps {
  open: boolean
  onClose: () => void
}

function HelpDocumentation({ open, onClose }: HelpDocumentationProps) {
  const { t } = useTranslation()
  const items = useMemo(() => buildHelpCollapseItems(t), [t])
  useEscapeToClose(open, onClose)

  return (
    <Modal
      title={t('designer.help.title')}
      open={open}
      onCancel={onClose}
      footer={null}
      width={800}
      className="designer-surface-modal"
      destroyOnHidden
      style={{ top: 20 }}
    >
      <div className="help-documentation-content">
        <Collapse
          defaultActiveKey={['quick-start']}
          expandIconPosition="end"
          size="large"
          items={items}
        />
      </div>
    </Modal>
  )
}

export default HelpDocumentation
