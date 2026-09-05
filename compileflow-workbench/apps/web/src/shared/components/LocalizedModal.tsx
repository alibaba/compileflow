import { Modal, type ModalProps } from 'antd'
import { useTranslation } from 'react-i18next'

export function LocalizedModal({ closable = true, ...props }: ModalProps) {
  const { t } = useTranslation()
  const localizedClosable =
    closable === false
      ? false
      : {
          'aria-label': t('common.close'),
          ...(typeof closable === 'object' ? closable : {}),
        }

  return <Modal {...props} closable={localizedClosable} />
}
