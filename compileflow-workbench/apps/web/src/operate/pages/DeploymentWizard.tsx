import { CheckOutlined, LoadingOutlined, RocketOutlined } from '@ant-design/icons'
import {
  Alert,
  App,
  AutoComplete,
  Button,
  Form,
  Input,
  InputNumber,
  Result,
  Select,
  Space,
  Steps,
} from 'antd'
import { isAxiosError } from 'axios'
import type { TFunction } from 'i18next'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'

import styles from './DeploymentWizard.module.css'

import { createDeployment, getDeploymentRoute } from '@/operate/api/deployments'
import { getProcessByCode, getProcesses, getProcessVersions } from '@/shared/api/processes'
import { LoadErrorAlert } from '@/shared/components/LoadErrorAlert'
import { PageHeader } from '@/shared/components/page'
import {
  buildDeploymentDetailPath,
  DEPLOYMENT_ALIAS_PRESETS,
  getDeploymentAliasPreset,
  ROUTES,
} from '@/shared/constants'
import type {
  DeploymentRequest,
  DeploymentStrategy,
  ProcessSummary,
  ProcessVersion,
} from '@/shared/contracts'
import { useDebounce } from '@/shared/hooks/useDebounce'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { createUniqueId } from '@/shared/identifiers'

const { Option } = Select
const { TextArea } = Input

type DeployStatus = 'idle' | 'deploying' | 'success' | 'failed'

interface DeploymentFormValues {
  processCode?: string
  version?: string
  alias?: string
  strategy?: DeploymentStrategy
  canaryWeightBps?: number
  notes?: string
}

type DeploymentForm = ReturnType<typeof Form.useForm<DeploymentFormValues>>[0]
type Navigate = ReturnType<typeof useNavigate>

interface WizardStep {
  content: string
  title: string
}

interface DeploymentWizardState {
  canAdvance: boolean
  createdDeploymentId: string | undefined
  current: number
  deployStatus: DeployStatus
  processOptions: ProcessSummary[]
  processesError: boolean
  processesLoading: boolean
  processVersions: ProcessVersion[]
  form: DeploymentForm
  formData: DeploymentFormValues
  handleDeploy: () => Promise<void>
  handleProcessSearch: (keyword: string) => void
  handleNext: () => Promise<void>
  handlePrev: () => void
  handleReset: () => void
  handleVersionSearch: (prefix: string) => void
  retryProcesses: () => void
  retryVersions: () => void
  selectedAliasPreset: ReturnType<typeof getDeploymentAliasPreset> | undefined
  selectedProcess: ProcessSummary | undefined
  selectedStrategy: DeploymentFormValues['strategy']
  versionsLoading: boolean
  versionsError: boolean
}

function createSteps(t: TFunction): WizardStep[] {
  return [
    { title: t('deployment.wizard.selectProcess'), content: t('deployment.wizard.step1.desc') },
    {
      title: t('deployment.wizard.selectAlias'),
      content: t('deployment.wizard.step2.desc'),
    },
    { title: t('deployment.wizard.confirm'), content: t('deployment.wizard.step3.desc') },
  ]
}

function createDeploymentRequest(
  formData: DeploymentFormValues,
  selectedProcess: ProcessSummary | undefined,
  expectedRouteRevision: number,
  idempotencyKey: string
): DeploymentRequest {
  const processCode = selectedProcess?.code ?? formData.processCode
  if (!processCode || !formData.version || !formData.alias) {
    throw new Error('Deployment form is incomplete')
  }
  return {
    processCode,
    version: formData.version,
    alias: formData.alias,
    strategy: formData.strategy ?? 'all_at_once',
    expectedRouteRevision,
    idempotencyKey,
    canaryWeightBps: formData.strategy === 'canary' ? formData.canaryWeightBps : undefined,
    notes: formData.notes,
  }
}

function useInitialProcessSelection(
  form: DeploymentForm,
  initialProcessCode: string | undefined
): void {
  useEffect(() => {
    if (initialProcessCode) {
      form.setFieldsValue({ processCode: initialProcessCode })
    }
  }, [form, initialProcessCode])
}

function useCanAdvance(
  form: DeploymentForm,
  current: number,
  selectedProcessCode: string | undefined,
  selectedStrategy: DeploymentFormValues['strategy']
): boolean {
  const version = Form.useWatch('version', form)
  const alias = Form.useWatch('alias', form)
  const canaryWeight = Form.useWatch('canaryWeightBps', form)

  if (current === 0) return Boolean(selectedProcessCode && version)
  if (current !== 1) return true
  if (!alias) return false
  return (
    selectedStrategy !== 'canary' ||
    Boolean(canaryWeight && canaryWeight >= 1 && canaryWeight <= 9_999)
  )
}

async function validatedStepValues(
  form: DeploymentForm
): Promise<DeploymentFormValues | undefined> {
  try {
    return await form.validateFields()
  } catch {
    // Ant Design displays field errors inline.
    return undefined
  }
}

function useProcessOptions(
  initialProcessCode: string | undefined,
  selectedProcessCode: string | undefined,
  onMissingSelection: (code: string) => void
) {
  const [processOptions, setProcessOptions] = useState<ProcessSummary[]>([])
  const [processesError, setProcessesError] = useState(false)
  const [processesLoading, setProcessesLoading] = useState(false)
  const [processSearch, setProcessSearch] = useState('')
  const processKeyword = useDebounce(processSearch, 300)
  const requestGeneration = useRef(0)

  const loadProcesses = useCallback(async () => {
    const generation = ++requestGeneration.current
    try {
      setProcessesLoading(true)
      setProcessesError(false)
      const response = await getProcesses({
        page: 1,
        pageSize: 100,
        keyword: processKeyword || undefined,
      })
      const pinnedCode = selectedProcessCode ?? initialProcessCode
      if (!pinnedCode || response.data.some((process) => process.code === pinnedCode)) {
        if (generation === requestGeneration.current) setProcessOptions(response.data)
        return
      }
      let selected: ProcessSummary
      try {
        selected = await getProcessByCode(pinnedCode)
      } catch (error) {
        if (!isAxiosError(error) || error.response?.status !== 404) throw error
        if (generation === requestGeneration.current) {
          setProcessOptions(response.data)
          onMissingSelection(pinnedCode)
        }
        return
      }
      if (generation === requestGeneration.current) {
        setProcessOptions([selected, ...response.data])
      }
    } catch {
      if (generation === requestGeneration.current) setProcessesError(true)
    } finally {
      if (generation === requestGeneration.current) setProcessesLoading(false)
    }
  }, [initialProcessCode, onMissingSelection, processKeyword, selectedProcessCode])

  useEffect(() => {
    void loadProcesses()
    return () => {
      requestGeneration.current += 1
    }
  }, [loadProcesses])

  return {
    processOptions,
    processesError,
    processesLoading,
    handleProcessSearch: setProcessSearch,
    resetProcessSearch: () => setProcessSearch(''),
    retryProcesses: () => void loadProcesses(),
  }
}

function useProcessVersions(form: DeploymentForm, selectedProcessCode: string | undefined) {
  const [processVersions, setProcessVersions] = useState<ProcessVersion[]>([])
  const [versionsLoading, setVersionsLoading] = useState(false)
  const [versionsError, setVersionsError] = useState(false)
  const [versionRefresh, setVersionRefresh] = useState(0)
  const [versionSearch, setVersionSearch] = useState('')
  const versionPrefix = useDebounce(versionSearch, 300)

  useEffect(() => {
    form.setFields([{ name: 'version', value: undefined, errors: [] }])
    setProcessVersions([])
    setVersionsLoading(false)
    setVersionSearch('')
  }, [form, selectedProcessCode])

  useEffect(() => {
    if (!selectedProcessCode) return
    let active = true
    setVersionsLoading(true)
    setVersionsError(false)
    void getProcessVersions(selectedProcessCode, {
      limit: 100,
      versionPrefix: versionPrefix || undefined,
    })
      .then((response) => {
        if (active) setProcessVersions(response.data)
      })
      .catch(() => {
        if (active) setVersionsError(true)
      })
      .finally(() => {
        if (active) setVersionsLoading(false)
      })
    return () => {
      active = false
    }
  }, [selectedProcessCode, versionPrefix, versionRefresh])

  return {
    processVersions,
    handleVersionSearch: setVersionSearch,
    retryVersions: () => setVersionRefresh((value) => value + 1),
    versionsError,
    versionsLoading,
  }
}

function useDeploymentSubmission(
  formData: DeploymentFormValues,
  selectedProcess: ProcessSummary | undefined,
  t: TFunction
) {
  const { message } = App.useApp()
  const [deployStatus, setDeployStatus] = useState<DeployStatus>('idle')
  const [createdDeploymentId, setCreatedDeploymentId] = useState<string>()
  const submission = useRef<{ fingerprint: string; request: DeploymentRequest } | null>(null)
  const submitting = useRef(false)
  const active = useRef(true)

  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
    }
  }, [])

  const handleDeploy = useCallback(async () => {
    if (submitting.current) return
    submitting.current = true
    setDeployStatus('deploying')
    try {
      const fields = createDeploymentRequest(formData, selectedProcess, 0, '')
      const fingerprint = JSON.stringify(fields)
      if (submission.current?.fingerprint !== fingerprint) {
        const route = await getDeploymentRoute(fields.processCode, fields.alias)
        if (!active.current) return
        submission.current = {
          fingerprint,
          request: {
            ...fields,
            expectedRouteRevision: route?.revision ?? 0,
            idempotencyKey: createUniqueId(),
          },
        }
      }
      // An uncertain response must replay the exact body, including the original CAS revision.
      const deployment = await createDeployment(submission.current.request)
      if (!active.current) return
      setCreatedDeploymentId(deployment.id)
      setDeployStatus('success')
    } catch {
      if (!active.current) return
      setDeployStatus('failed')
      message.error(t('deployment.wizard.deployFailed'))
    } finally {
      submitting.current = false
    }
  }, [formData, message, selectedProcess, t])

  const resetDeployment = useCallback(() => {
    setCreatedDeploymentId(undefined)
    setDeployStatus('idle')
    submission.current = null
  }, [])

  return { createdDeploymentId, deployStatus, handleDeploy, resetDeployment }
}

function useDeploymentWizardState(
  initialProcessCode: string | undefined,
  onMissingInitialProcess: (code: string) => void,
  t: TFunction
): DeploymentWizardState {
  const [current, setCurrent] = useState(0)
  const [formData, setFormData] = useState<DeploymentFormValues>(
    initialProcessCode ? { processCode: initialProcessCode } : {}
  )
  const [form] = Form.useForm<DeploymentFormValues>()
  const selectedStrategy = Form.useWatch('strategy', form) ?? formData.strategy ?? 'all_at_once'
  const selectedProcessCode = Form.useWatch('processCode', form) ?? formData.processCode
  const canAdvance = useCanAdvance(form, current, selectedProcessCode, selectedStrategy)
  const clearMissingSelection = useCallback(
    (code: string) => {
      if (form.getFieldValue('processCode') === code) {
        form.setFieldValue('processCode', undefined)
        setFormData((previous) => ({ ...previous, processCode: undefined, version: undefined }))
      }
      onMissingInitialProcess(code)
    },
    [form, onMissingInitialProcess]
  )
  const processOptions = useProcessOptions(
    initialProcessCode,
    selectedProcessCode,
    clearMissingSelection
  )
  const processVersions = useProcessVersions(form, selectedProcessCode)

  useInitialProcessSelection(form, initialProcessCode)

  const selectedProcess = useMemo(
    () => processOptions.processOptions.find((process) => process.code === formData.processCode),
    [formData.processCode, processOptions.processOptions]
  )
  const selectedAliasPreset = formData.alias ? getDeploymentAliasPreset(formData.alias) : undefined
  const deployment = useDeploymentSubmission(formData, selectedProcess, t)
  const advancing = useRef(false)
  const handleNext = useCallback(async () => {
    if (advancing.current) return
    advancing.current = true
    try {
      const values = await validatedStepValues(form)
      if (!values) return
      setFormData((previous) => ({ ...previous, ...values }))
      setCurrent((step) => step + 1)
    } finally {
      advancing.current = false
    }
  }, [form])

  const handlePrev = useCallback(() => setCurrent((step) => step - 1), [])

  const handleReset = useCallback(() => {
    setCurrent(0)
    setFormData({})
    processOptions.resetProcessSearch()
    form.resetFields()
    deployment.resetDeployment()
  }, [deployment, form, processOptions])

  return {
    canAdvance,
    createdDeploymentId: deployment.createdDeploymentId,
    current,
    deployStatus: deployment.deployStatus,
    processOptions: processOptions.processOptions,
    processesError: processOptions.processesError,
    processesLoading: processOptions.processesLoading,
    processVersions: processVersions.processVersions,
    form,
    formData,
    handleDeploy: deployment.handleDeploy,
    handleProcessSearch: processOptions.handleProcessSearch,
    handleNext,
    handlePrev,
    handleReset,
    handleVersionSearch: processVersions.handleVersionSearch,
    retryProcesses: processOptions.retryProcesses,
    retryVersions: processVersions.retryVersions,
    selectedAliasPreset,
    selectedProcess,
    selectedStrategy,
    versionsLoading: processVersions.versionsLoading,
    versionsError: processVersions.versionsError,
  }
}

function SelectProcessStep({
  processOptions,
  processesError,
  processesLoading,
  processVersions,
  form,
  onProcessSearch,
  onVersionSearch,
  onRetryProcesses,
  onRetryVersions,
  t,
  versionsLoading,
  versionsError,
}: {
  processOptions: ProcessSummary[]
  processesError: boolean
  processesLoading: boolean
  processVersions: ProcessVersion[]
  form: DeploymentForm
  onProcessSearch: (keyword: string) => void
  onVersionSearch: (prefix: string) => void
  onRetryProcesses: () => void
  onRetryVersions: () => void
  t: TFunction
  versionsLoading: boolean
  versionsError: boolean
}) {
  const selectedProcessCode = Form.useWatch('processCode', form)

  return (
    <>
      {processesError && <LoadErrorAlert onRetry={onRetryProcesses} />}
      {versionsError && <LoadErrorAlert onRetry={onRetryVersions} />}
      <Form form={form} layout="vertical">
        <Form.Item
          label={t('deployment.wizard.selectProcessLabel')}
          name="processCode"
          rules={[{ required: true, message: t('deployment.wizard.selectProcessRequired') }]}
        >
          <Select
            placeholder={t('deployment.selectProcessPlaceholder')}
            size="large"
            showSearch
            filterOption={false}
            onSearch={onProcessSearch}
            loading={processesLoading}
          >
            {processOptions.map((process) => (
              <Option key={process.code} value={process.code}>
                {process.name} ({process.type})
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item
          label={t('deployment.version')}
          name="version"
          rules={[{ required: true, message: t('deployment.wizard.versionRequired') }]}
        >
          <Select
            disabled={!selectedProcessCode}
            filterOption={false}
            loading={versionsLoading}
            onSearch={onVersionSearch}
            placeholder={t('deployment.wizard.versionPlaceholder')}
            showSearch
            size="large"
            notFoundContent={t('deployment.wizard.noPublishedVersions')}
          >
            {processVersions.map((version) => (
              <Option key={version.version} value={version.version}>
                {version.version}
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Alert description={t('deployment.wizard.step1.hint')} type="info" showIcon />
      </Form>
    </>
  )
}

function AliasStep({
  form,
  selectedStrategy,
  t,
}: {
  form: DeploymentForm
  selectedStrategy: DeploymentFormValues['strategy']
  t: TFunction
}) {
  return (
    <Form form={form} layout="vertical">
      <Form.Item
        label={t('deployment.alias')}
        name="alias"
        rules={[
          { required: true, message: t('deployment.wizard.selectAliasRequired') },
          {
            pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,
            message: t('deployment.wizard.aliasInvalid'),
          },
        ]}
      >
        <AutoComplete
          placeholder={t('deployment.wizard.selectAliasPlaceholder')}
          size="large"
          options={DEPLOYMENT_ALIAS_PRESETS.map((preset) => ({
            value: preset.value,
            label: <div className={styles.aliasName}>{t(preset.labelKey)}</div>,
          }))}
        />
      </Form.Item>

      <Form.Item label={t('deployment.notes')} name="notes">
        <TextArea
          rows={4}
          placeholder={t('deployment.notesPlaceholder')}
          maxLength={200}
          showCount
        />
      </Form.Item>

      <Form.Item label={t('deployment.strategy')} name="strategy" initialValue="all_at_once">
        <Select size="large">
          <Option value="all_at_once">{t('deployment.strategy.all_at_once')}</Option>
          <Option value="canary">{t('deployment.strategy.canary')}</Option>
        </Select>
      </Form.Item>

      {selectedStrategy === 'canary' && (
        <Form.Item
          label={t('deployment.canaryWeightBps')}
          name="canaryWeightBps"
          initialValue={1_000}
          rules={[
            {
              required: true,
              message: t('deployment.canaryRequired'),
            },
            {
              type: 'number',
              min: 1,
              max: 9_999,
              message: t('deployment.canaryRange'),
            },
            {
              validator: (_, value: number | undefined) =>
                value == null || Number.isInteger(value)
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('deployment.canaryInteger'))),
            },
          ]}
        >
          <InputNumber min={1} max={9_999} suffix="bps" style={{ width: '100%' }} size="large" />
        </Form.Item>
      )}

      <Alert
        title={t('deployment.wizard.step2.warningTitle')}
        description={t('deployment.wizard.step2.warningDesc')}
        type="warning"
        showIcon
      />
    </Form>
  )
}

function ConfirmStep({ state, t }: { state: DeploymentWizardState; t: TFunction }) {
  return (
    <div>
      <div className={styles.confirmPanel}>
        <h3 className={styles.confirmTitle}>{t('deployment.wizard.confirmTitle')}</h3>
        <dl className={styles.confirmGrid}>
          <dt>{t('deployment.processName')}</dt>
          <dd>{state.selectedProcess?.name}</dd>
          <dt>{t('deployment.wizard.processType')}</dt>
          <dd>{state.selectedProcess?.type}</dd>
          <dt>{t('deployment.version')}</dt>
          <dd>{state.formData.version}</dd>
          <dt>{t('deployment.alias')}</dt>
          <dd>
            {state.selectedAliasPreset
              ? t(state.selectedAliasPreset.labelKey)
              : state.formData.alias}
          </dd>
          <dt>{t('deployment.strategy')}</dt>
          <dd>{t(`deployment.strategy.${state.formData.strategy ?? 'all_at_once'}`)}</dd>
          {state.formData.strategy === 'canary' && (
            <>
              <dt>{t('deployment.canaryWeightBps')}</dt>
              <dd>
                {state.formData.canaryWeightBps ?? 1_000} bps (
                {(state.formData.canaryWeightBps ?? 1_000) / 100}%)
              </dd>
            </>
          )}
          {state.formData.notes && (
            <>
              <dt>{t('deployment.notes')}</dt>
              <dd>{state.formData.notes}</dd>
            </>
          )}
        </dl>
      </div>

      <Alert
        title={t('deployment.wizard.readyTitle')}
        description={t('deployment.wizard.readyDesc')}
        type="success"
        showIcon
        icon={<CheckOutlined />}
      />
    </div>
  )
}

function StepContent({ state, t }: { state: DeploymentWizardState; t: TFunction }) {
  if (state.current === 0) {
    return (
      <SelectProcessStep
        processOptions={state.processOptions}
        processesError={state.processesError}
        processesLoading={state.processesLoading}
        processVersions={state.processVersions}
        form={state.form}
        onProcessSearch={state.handleProcessSearch}
        onVersionSearch={state.handleVersionSearch}
        onRetryProcesses={state.retryProcesses}
        onRetryVersions={state.retryVersions}
        t={t}
        versionsLoading={state.versionsLoading}
        versionsError={state.versionsError}
      />
    )
  }

  if (state.current === 1) {
    return <AliasStep form={state.form} selectedStrategy={state.selectedStrategy} t={t} />
  }

  return <ConfirmStep state={state} t={t} />
}

function WizardActions({
  navigate,
  state,
  stepCount,
  t,
}: {
  navigate: Navigate
  state: DeploymentWizardState
  stepCount: number
  t: TFunction
}) {
  const deploying = state.deployStatus === 'deploying'

  return (
    <div className={styles.actions}>
      <Space>
        {state.current > 0 && !deploying && (
          <Button onClick={state.handlePrev}>{t('common.previous')}</Button>
        )}
        {state.current < stepCount - 1 && (
          <Button
            type="primary"
            disabled={!state.canAdvance}
            onClick={() => {
              void state.handleNext()
            }}
          >
            {t('common.next')}
          </Button>
        )}
        {state.current === stepCount - 1 && (
          <Button
            type="primary"
            onClick={() => {
              void state.handleDeploy()
            }}
            icon={deploying ? <LoadingOutlined /> : <RocketOutlined />}
            loading={deploying}
            disabled={deploying}
          >
            {deploying ? t('deployment.wizard.deploying') : t('deployment.deploy')}
          </Button>
        )}
        <Button onClick={() => navigate(ROUTES.OPERATE_DEPLOYMENTS)} disabled={deploying}>
          {t('common.cancel')}
        </Button>
      </Space>
    </div>
  )
}

function SuccessResult({
  navigate,
  state,
  t,
}: {
  navigate: Navigate
  state: DeploymentWizardState
  t: TFunction
}) {
  return (
    <div className={styles.wizardPage}>
      <Result
        status="success"
        title={t('deployment.wizard.deploySuccess')}
        subTitle={t('deployment.wizard.deploySuccessDesc', {
          process: state.selectedProcess?.name,
          alias: state.selectedAliasPreset
            ? t(state.selectedAliasPreset.labelKey)
            : state.formData.alias,
        })}
        extra={[
          <Button
            type="primary"
            key="view"
            onClick={() =>
              navigate(
                state.createdDeploymentId
                  ? buildDeploymentDetailPath(state.createdDeploymentId)
                  : ROUTES.OPERATE_DEPLOYMENTS
              )
            }
          >
            {t('deployment.wizard.viewDeployment')}
          </Button>,
          <Button key="new" onClick={state.handleReset}>
            {t('deployment.wizard.continueDeploy')}
          </Button>,
        ]}
      />
    </div>
  )
}

function DeploymentWizard() {
  usePageTitle('pageTitle.operate.deployWizard')
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const handleMissingInitialProcess = useCallback(
    (code: string) => {
      if (searchParams.get('processCode') !== code) return
      const next = new URLSearchParams(searchParams)
      next.delete('processCode')
      setSearchParams(next, { replace: true })
    },
    [searchParams, setSearchParams]
  )
  const state = useDeploymentWizardState(
    searchParams.get('processCode') ?? undefined,
    handleMissingInitialProcess,
    t
  )
  const steps = useMemo(() => createSteps(t), [t])

  if (state.deployStatus === 'success') {
    return <SuccessResult navigate={navigate} state={state} t={t} />
  }

  return (
    <div className={styles.wizardPage}>
      <PageHeader
        title={t('deployment.wizard.title')}
        subtitle={t('deployment.wizard.subtitle')}
        eyebrow={t('nav.operate')}
        accent="operate"
        compact
      />
      <Steps current={state.current} items={steps} className={styles.steps} />
      <div className={styles.stepContent}>
        <StepContent state={state} t={t} />
      </div>
      <WizardActions navigate={navigate} state={state} stepCount={steps.length} t={t} />
    </div>
  )
}

export default DeploymentWizard
