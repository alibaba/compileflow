import { Form, Input, Select } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import type { NodePropertyTabProps } from '../../types/propertyTabs'

import { PropertiesTabLayout } from './PropertiesTabLayout'

type ScheduleProperty = 'duration' | 'durationExpression' | 'wakeAtExpression'

const SCHEDULE_PROPERTIES: readonly ScheduleProperty[] = [
  'duration',
  'durationExpression',
  'wakeAtExpression',
]

export function TimerTaskPropertiesTab({ node, onUpdate }: NodePropertyTabProps) {
  const { t } = useTranslation()
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)
  const scheduleProperty =
    SCHEDULE_PROPERTIES.find((property) => node.properties[property] !== undefined) ?? 'duration'

  const selectSchedule = (selected: ScheduleProperty) => {
    onUpdate(node.id, {
      duration: selected === 'duration' ? '' : undefined,
      durationExpression: selected === 'durationExpression' ? '' : undefined,
      wakeAtExpression: selected === 'wakeAtExpression' ? '' : undefined,
    })
  }

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.timerTask.title')}
      description={t('designer.props.node.timerTask.desc')}
    >
      <Form.Item label={t('designer.props.node.timerTask.scheduleType')} required>
        <Select
          value={scheduleProperty}
          onChange={selectSchedule}
          aria-label={t('designer.props.node.timerTask.scheduleType')}
        >
          {SCHEDULE_PROPERTIES.map((property) => (
            <Select.Option key={property} value={property}>
              {t(`designer.props.node.timerTask.${property}`)}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item
        label={t(`designer.props.node.timerTask.${scheduleProperty}`)}
        required
        help={t(`designer.props.node.timerTask.${scheduleProperty}Help`)}
      >
        <Input
          value={node.properties[scheduleProperty] || ''}
          onChange={handleInputChange(scheduleProperty)}
          aria-label={t(`designer.props.node.timerTask.${scheduleProperty}`)}
        />
      </Form.Item>
    </PropertiesTabLayout>
  )
}
