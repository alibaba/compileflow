import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import type { FormInstance } from 'antd'
import { App, Button, Form, Input, Popconfirm, Select, Space, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { selectCurrentProcess, updateProcessInfo } from '../store/editorSlice'
import type { ProcessVariable } from '../types/flowDefinition'
import { isJavaIdentifier, isReservedJavaIdentifier } from '../types/javaIdentifiers'

import { DefaultValueEditor } from './properties/DefaultValueEditor'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

import './DesignerSurfaces.css'
import './ProcessVariablesDialog.css'

interface ProcessVariablesDialogProps {
  open: boolean
  onClose: () => void
}

interface VariableTableRow extends ProcessVariable {
  _index: number
  key: number
}

interface VariableTableProps {
  rows: VariableTableRow[]
  onDelete: (index: number) => void
  onEdit: (variable: ProcessVariable, index: number) => void
}

interface VariableEditModalProps {
  editingIndex: number
  form: FormInstance<ProcessVariable>
  open: boolean
  onCancel: () => void
  onSave: () => void
}

const DIRECTION_VALUES = [
  'param',
  'return',
  'inner',
] as const satisfies readonly ProcessVariable['inOutType'][]

const decodeVariableTip = (tip: string) => tip.replace(/&lt;/g, '<').replace(/&gt;/g, '>')

function useDirectionLabel() {
  const { t } = useTranslation()

  return useCallback(
    (value: string) => {
      const labels: Record<string, string> = {
        param: t('designer.variableManager.dir.param'),
        return: t('designer.variableManager.dir.return'),
        inner: t('designer.variableManager.dir.inner'),
      }
      return labels[value] || value
    },
    [t]
  )
}

function VariableTable({ rows, onDelete, onEdit }: VariableTableProps) {
  const { t } = useTranslation()
  const directionLabel = useDirectionLabel()
  const columns = useMemo<ColumnsType<VariableTableRow>>(
    () => [
      {
        title: t('designer.variableManager.col.name'),
        dataIndex: 'name',
        key: 'name',
        width: 120,
      },
      {
        title: t('designer.variableManager.col.dataType'),
        dataIndex: 'type',
        key: 'type',
        ellipsis: true,
      },
      {
        title: t('designer.variableManager.col.direction'),
        dataIndex: 'inOutType',
        key: 'inOutType',
        width: 80,
        render: (value: string) => directionLabel(value),
      },
      {
        title: t('designer.variableManager.col.defaultValue'),
        dataIndex: 'defaultValue',
        key: 'defaultValue',
        ellipsis: true,
        render: (value: string | undefined) =>
          value === undefined ? '-' : value === '' ? '""' : value,
      },
      {
        title: t('designer.variableManager.col.description'),
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
      },
      {
        title: t('designer.variableManager.col.actions'),
        key: 'actions',
        width: 100,
        render: (_, record) => (
          <Space>
            <Button type="link" size="small" onClick={() => onEdit(record, record._index)}>
              {t('common.edit')}
            </Button>
            <Popconfirm
              title={t('designer.variableManager.deleteConfirm')}
              onConfirm={() => onDelete(record._index)}
            >
              <Button
                type="link"
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={t('common.delete')}
              />
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [directionLabel, onDelete, onEdit, t]
  )

  return (
    <Table
      columns={columns}
      dataSource={rows}
      size="small"
      pagination={false}
      locale={{ emptyText: t('designer.variableManager.empty') }}
    />
  )
}

function VariableEditModal({ editingIndex, form, open, onCancel, onSave }: VariableEditModalProps) {
  const { t } = useTranslation()
  const defaultValue = Form.useWatch('defaultValue', form)
  useEscapeToClose(open, onCancel)

  return (
    <Modal
      title={
        editingIndex >= 0 ? t('designer.variableManager.edit') : t('designer.variableManager.add')
      }
      open={open}
      onOk={onSave}
      onCancel={onCancel}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
      className="designer-surface-modal"
      destroyOnHidden
    >
      <Form form={form} layout="vertical" size="small">
        <Form.Item
          label={t('designer.variableManager.field.name')}
          name="name"
          rules={[
            { required: true, message: t('designer.variableManager.field.nameRequired') },
            {
              validator: async (_, value: unknown) => {
                const name = typeof value === 'string' ? value.trim() : ''
                if (!name) return
                if (!isJavaIdentifier(name)) {
                  throw new Error(t('designer.variableManager.field.nameInvalid'))
                }
                if (isReservedJavaIdentifier(name)) {
                  throw new Error(t('designer.variableManager.field.nameReserved'))
                }
              },
            },
          ]}
        >
          <Input
            aria-label={t('designer.variableManager.field.name')}
            placeholder={t('designer.variableManager.field.namePlaceholder')}
          />
        </Form.Item>
        <Form.Item
          label={t('designer.variableManager.field.dataType')}
          name="type"
          rules={[
            { required: true, message: t('designer.variableManager.field.dataTypeRequired') },
          ]}
        >
          <Input
            aria-label={t('designer.variableManager.field.dataType')}
            placeholder={t('designer.variableManager.field.dataTypePlaceholder')}
          />
        </Form.Item>
        <Form.Item
          label={t('designer.variableManager.field.direction')}
          name="inOutType"
          rules={[{ required: true }]}
        >
          <Select aria-label={t('designer.variableManager.field.direction')}>
            {DIRECTION_VALUES.map((value) => (
              <Select.Option key={value} value={value}>
                {value} ({t(`designer.variableManager.dir.${value}`)})
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
        <Form.Item label={t('designer.variableManager.field.description')} name="description">
          <Input
            aria-label={t('designer.variableManager.field.description')}
            placeholder={t('designer.variableManager.field.descriptionPlaceholder')}
          />
        </Form.Item>
        <Form.Item name="defaultValue" hidden>
          <Input aria-label={t('designer.variableManager.field.defaultValue')} />
        </Form.Item>
        <Form.Item label={t('designer.variableManager.field.defaultValue')}>
          <DefaultValueEditor
            value={defaultValue}
            onChange={(value) => form.setFieldValue('defaultValue', value)}
            enabledLabel={t('designer.props.common.defaultValueEnabled')}
            placeholder={t('designer.variableManager.field.defaultValuePlaceholder')}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}

function ProcessVariablesDialog({ open, onClose }: ProcessVariablesDialogProps) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const currentProcess = useAppSelector(selectCurrentProcess)
  const [showEditModal, setShowEditModal] = useState(false)
  const [editingIndex, setEditingIndex] = useState<number>(-1)
  const [form] = Form.useForm<ProcessVariable>()
  useEscapeToClose(open && !showEditModal, onClose)
  const vars = currentProcess?.variables ?? []
  const rows = useMemo(
    () => vars.map((variable, index) => ({ ...variable, _index: index, key: index })),
    [vars]
  )

  const saveVars = useCallback(
    (newVars: ProcessVariable[]) => {
      dispatch(updateProcessInfo({ variables: newVars }))
    },
    [dispatch]
  )

  const handleAdd = useCallback(() => {
    setEditingIndex(-1)
    form.resetFields()
    form.setFieldsValue({ inOutType: 'param' })
    setShowEditModal(true)
  }, [form])

  const handleEdit = useCallback(
    (variable: ProcessVariable, index: number) => {
      setEditingIndex(index)
      form.resetFields()
      form.setFieldsValue(variable)
      setShowEditModal(true)
    },
    [form]
  )

  const handleDelete = useCallback(
    (index: number) => {
      saveVars(vars.filter((_, itemIndex) => itemIndex !== index))
      message.success(t('designer.variableManager.deleted'))
    },
    [saveVars, t, vars]
  )

  const handleSave = useCallback(async () => {
    try {
      const values = await form.validateFields()
      const normalizedValues = {
        ...values,
        name: values.name.trim(),
        type: values.type.trim(),
      }
      const duplicateName = vars.some(
        (variable, index) => index !== editingIndex && variable.name === normalizedValues.name
      )
      if (duplicateName) {
        form.setFields([
          {
            name: 'name',
            errors: [t('designer.variableManager.field.nameDuplicate')],
          },
        ])
        return
      }
      const newVars =
        editingIndex >= 0
          ? vars.map((variable, index) => (index === editingIndex ? normalizedValues : variable))
          : [...vars, normalizedValues]

      saveVars(newVars)
      setShowEditModal(false)
      message.success(
        editingIndex >= 0
          ? t('designer.variableManager.updated')
          : t('designer.variableManager.added')
      )
    } catch {
      return
    }
  }, [editingIndex, form, saveVars, t, vars])

  return (
    <>
      <Modal
        title={t('designer.variableManager.title')}
        open={open}
        onCancel={onClose}
        footer={null}
        width={700}
        className="designer-surface-modal"
        destroyOnHidden
      >
        <div className="variable-manager-actions">
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            {t('designer.variableManager.add')}
          </Button>
        </div>
        <VariableTable rows={rows} onDelete={handleDelete} onEdit={handleEdit} />
        <div className="variable-manager-tip">
          {decodeVariableTip(t('designer.variableManager.tip'))}
        </div>
      </Modal>

      <VariableEditModal
        editingIndex={editingIndex}
        form={form}
        open={showEditModal}
        onCancel={() => setShowEditModal(false)}
        onSave={() => void handleSave()}
      />
    </>
  )
}

export default ProcessVariablesDialog
