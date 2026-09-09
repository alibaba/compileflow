import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Empty, Input, Space, Tabs, Typography } from 'antd'
import { isAxiosError } from 'axios'
import type { TFunction } from 'i18next'
import type { RefObject } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import DifficultyRating from '../components/DifficultyRating'
import RelatedExamples from '../components/RelatedExamples'
import {
  getCategoryLabel,
  getExampleDuration,
  getExamplePresentation,
  getLevelLabel,
} from '../presentation/exampleMetadata'

import styles from './ExampleDetail.module.css'

import { getAllExamples, getExample } from '@/shared/api/examples'
import { executePreview } from '@/shared/api/execution'
import CodeBlock from '@/shared/components/CodeBlock'
import ExampleNavigation from '@/shared/components/ExampleNavigation'
import FeedbackActions from '@/shared/components/FeedbackActions'
import MarkdownRenderer from '@/shared/components/MarkdownRenderer'
import { modelTypeToTagKind, levelToTagKind, SemanticTag } from '@/shared/components/page'
import ExampleDetailSkeleton from '@/shared/components/skeletons/ExampleDetailSkeleton'
import TableOfContents from '@/shared/components/TableOfContents'
import { createLearnExamplesPath } from '@/shared/constants'
import type { Example } from '@/shared/contracts'
import type { ExecutionResponse } from '@/shared/contracts/executionContract'
import { toError } from '@/shared/errors'
import { localizeExample } from '@/shared/examples/localizeExample'
import { LearningProgressCard, useLearningProgress } from '@/shared/hooks/useLearningProgress'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { createLogger } from '@/shared/logging/logger'
import { openDesignerFromExample } from '@/shared/services/designerNavigation'

const { Paragraph, Text, Title } = Typography
const { TextArea } = Input
const logger = createLogger('ExampleDetail')

interface ExampleDataState {
  allExamples: Example[]
  error: string | null
  example: Example | null
  loading: boolean
}

interface ExecutionState {
  executing: boolean
  executionParams: string
  executionResult: ExecutionResponse | null
  onExecute: () => Promise<void>
  setExecutionParams: (value: string) => void
}

function exampleLoadErrorMessage(error: unknown, t: TFunction): string {
  const isMissingBuiltInExample = toError(error).message.startsWith('Example not found:')
  const isMissingRemoteExample = isAxiosError(error) && error.response?.status === 404

  return t(
    isMissingBuiltInExample || isMissingRemoteExample
      ? 'error.exampleNotFound'
      : 'error.networkError'
  )
}

function downloadExampleXml(example: Example): void {
  if (!example.code) return

  const blob = new Blob([example.code], { type: 'application/xml' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${example.id}.${example.modelType === 'TBBPM' ? 'bpm' : 'bpmn'}`
  anchor.click()
  URL.revokeObjectURL(url)
}

function parseExecutionParams(value: string): Record<string, unknown> | null {
  if (!value.trim()) return {}

  const parsed = JSON.parse(value) as unknown
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return null
  }

  return parsed as Record<string, unknown>
}

function getExamplesPath(state: unknown): string {
  const defaultPath = createLearnExamplesPath()
  if (!state || typeof state !== 'object') return defaultPath
  const examplesPath = Reflect.get(state, 'examplesPath')
  return typeof examplesPath === 'string' &&
    (examplesPath === defaultPath || examplesPath.startsWith(`${defaultPath}?`))
    ? examplesPath
    : defaultPath
}

function useExampleData(
  exampleId: string | undefined,
  setTotalExamples: (total: number) => void
): ExampleDataState {
  const { t } = useTranslation()
  const tRef = useRef(t)
  const setTotalExamplesRef = useRef(setTotalExamples)
  const catalogRequestRef = useRef(0)
  const loadRequestRef = useRef(0)
  const [allExamples, setAllExamples] = useState<Example[]>([])
  const [example, setExample] = useState<Example | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    tRef.current = t
  }, [t])

  useEffect(() => {
    setTotalExamplesRef.current = setTotalExamples
  }, [setTotalExamples])

  const loadAllExamples = useCallback(async () => {
    const requestId = ++catalogRequestRef.current
    try {
      const data = await getAllExamples()
      if (requestId !== catalogRequestRef.current) return
      setAllExamples(data)
      setTotalExamplesRef.current(data.length)
    } catch (error) {
      if (requestId !== catalogRequestRef.current) return
      setAllExamples([])
      logger.error('Failed to load example catalog', toError(error))
    }
  }, [])

  const loadExample = useCallback(async (id: string) => {
    const requestId = ++loadRequestRef.current
    try {
      setLoading(true)
      setError(null)

      const loadedExample = await getExample(id)
      if (requestId === loadRequestRef.current) setExample(loadedExample)
    } catch (error) {
      if (requestId !== loadRequestRef.current) return
      setExample(null)
      setError(exampleLoadErrorMessage(error, tRef.current))
      logger.error('Failed to load example', toError(error), { exampleId: id })
    } finally {
      if (requestId === loadRequestRef.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAllExamples()
    return () => {
      catalogRequestRef.current += 1
    }
  }, [loadAllExamples])

  useEffect(() => {
    if (!exampleId) {
      loadRequestRef.current += 1
      setLoading(false)
      setError(tRef.current('error.exampleNotFound'))
      return
    }

    void loadExample(exampleId)
    return () => {
      loadRequestRef.current += 1
    }
  }, [exampleId, loadExample])

  return {
    allExamples,
    error,
    example,
    loading,
  }
}

function useExampleExecution(example: Example | null): ExecutionState {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [executing, setExecuting] = useState(false)
  const [executionParams, setExecutionParams] = useState('{}')
  const [executionResult, setExecutionResult] = useState<ExecutionResponse | null>(null)
  const executionRequestRef = useRef(0)
  const executionInFlightRef = useRef(false)

  useEffect(() => {
    executionRequestRef.current += 1
    executionInFlightRef.current = false
    setExecuting(false)
    setExecutionParams('{}')
    setExecutionResult(null)

    return () => {
      executionRequestRef.current += 1
    }
  }, [example?.id])

  const onExecute = useCallback(async () => {
    if (executionInFlightRef.current) return
    if (!example?.code) {
      message.error(t('exec.noCode'))
      return
    }

    let params: Record<string, unknown>
    try {
      const parsedParams = parseExecutionParams(executionParams)
      if (!parsedParams) {
        message.error(t('exec.paramError'))
        return
      }
      params = parsedParams
    } catch {
      message.error(t('exec.paramError'))
      return
    }

    const requestId = ++executionRequestRef.current
    executionInFlightRef.current = true
    try {
      setExecuting(true)
      setExecutionResult(null)
      const result = await executePreview({
        code: example.id,
        modelType: example.modelType,
        xml: example.code,
        params,
      })
      if (requestId !== executionRequestRef.current) return
      setExecutionResult(result)
      if (result.success) {
        message.success(t('exec.success'))
      } else {
        message.error(`${t('exec.failed')}: ${result.message}`)
      }
    } catch (error) {
      if (requestId !== executionRequestRef.current) return
      message.error(t('exec.error', { error: toError(error).message }))
    } finally {
      if (requestId === executionRequestRef.current) {
        executionInFlightRef.current = false
        setExecuting(false)
      }
    }
  }, [example, executionParams, message, t])

  return {
    executing,
    executionParams,
    executionResult,
    onExecute,
    setExecutionParams,
  }
}

function ExampleDetailError({ description, onBack }: { description: string; onBack: () => void }) {
  const { t } = useTranslation()

  return (
    <Alert
      title={t('error.loadFailed')}
      description={description}
      type="error"
      showIcon
      action={<Button onClick={onBack}>{t('detail.back')}</Button>}
    />
  )
}

function ExampleHeader({ example }: { example: Example }) {
  const { t } = useTranslation()
  const presentation = getExamplePresentation(example, t)

  return (
    <header className={styles.headerSection}>
      <div className={styles.tagRow}>
        <SemanticTag kind={levelToTagKind(example.level)}>
          {getLevelLabel(example.level, t)}
        </SemanticTag>
        <SemanticTag kind="default">{getCategoryLabel(example.category, t)}</SemanticTag>
        {example.modelType && (
          <SemanticTag kind={modelTypeToTagKind(example.modelType)}>
            {example.modelType}
          </SemanticTag>
        )}
      </div>

      <Title level={1} className={styles.pageTitle}>
        {presentation.name}
      </Title>
      <Paragraph className={styles.description}>{presentation.description}</Paragraph>

      <div className={styles.metaRow}>
        <span className={styles.metaItem}>
          <ClockCircleOutlined className={styles.clockIcon} />
          {getExampleDuration(example.duration, t)}
        </span>
        <span className={styles.metaSeparator}>|</span>
        <DifficultyRating
          className={styles.difficultyRate}
          value={example.difficulty}
          label={t('detail.difficultyValue', { value: example.difficulty })}
        />
      </div>

      {example.tags.length > 0 && (
        <div className={styles.contentTags}>
          {example.tags.map((tag) => (
            <SemanticTag key={tag} kind="default">
              {tag}
            </SemanticTag>
          ))}
        </div>
      )}
    </header>
  )
}

function ConceptList({
  className,
  items,
  title,
  titleClassName,
}: {
  className: string
  items?: string[]
  title: string
  titleClassName: string
}) {
  if (!items?.length) return null

  return (
    <div className={className}>
      <Title level={2} className={titleClassName}>
        {title}
      </Title>
      <ul className={styles.conceptList}>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  )
}

function OverviewTab({ example }: { example: Example }) {
  const { t } = useTranslation()

  return (
    <>
      {example.overview && <MarkdownRenderer content={example.overview} />}
      <ConceptList
        className={styles.conceptBox}
        items={example.whatYouWillLearn}
        title={t('detail.whatYouWillLearn')}
        titleClassName={styles.conceptTitle}
      />
      <ConceptList
        className={styles.keyConceptBox}
        items={example.keyConcepts}
        title={t('detail.keyConcepts')}
        titleClassName={styles.keyConceptTitle}
      />
      {example.explanation && (
        <section>
          <Title level={2}>{t('detail.explanation')}</Title>
          <MarkdownRenderer content={example.explanation} />
        </section>
      )}
      {example.nextSteps && (
        <section>
          <Title level={2}>{t('detail.nextSteps')}</Title>
          <MarkdownRenderer content={example.nextSteps} />
        </section>
      )}
    </>
  )
}

function CodeTab({ example }: { example: Example }) {
  const { t } = useTranslation()

  if (!example.code) {
    return <Empty description={t('detail.noCode')} />
  }

  return (
    <CodeBlock
      code={example.code}
      language="xml"
      filename={`${example.id}.${example.modelType === 'TBBPM' ? 'bpm' : 'bpmn'}`}
      showLineNumbers
    />
  )
}

function ExecuteTab({ execution }: { execution: ExecutionState }) {
  const { t } = useTranslation()

  return (
    <Space vertical size="large" className={styles.execTabContent}>
      <Alert
        title={t('exec.title')}
        description={t('exec.desc')}
        type="warning"
        showIcon
        className={styles.execAlert}
      />
      <div>
        <Title level={2}>{t('exec.params')}</Title>
        <TextArea
          aria-label={t('exec.params')}
          rows={6}
          value={execution.executionParams}
          onChange={(event) => execution.setExecutionParams(event.target.value)}
          placeholder={t('exec.paramsPlaceholder')}
          className={styles.execTextarea}
        />
      </div>
      <Button
        type="primary"
        size="large"
        icon={<PlayCircleOutlined />}
        loading={execution.executing}
        onClick={execution.onExecute}
        block
        className={styles.execButton}
      >
        {execution.executing ? t('exec.executing') : t('exec.execute')}
      </Button>
      {execution.executionResult && <ExecutionResult result={execution.executionResult} />}
    </Space>
  )
}

function ExecutionResult({ result }: { result: ExecutionResponse }) {
  const { t } = useTranslation()
  const payload = result.success
    ? (result.result ?? {})
    : {
        errorCode: result.errorCode,
        error: result.error,
        message: result.message,
      }

  return (
    <div className={`${styles.execResultBlock} ${result.success ? styles.success : styles.error}`}>
      <div className={styles.execResultHeader}>
        {result.success ? (
          <>
            <CheckCircleOutlined className={styles.resultIconSuccess} />
            <Text strong>{t('exec.success')}</Text>
          </>
        ) : (
          <>
            <CloseCircleOutlined className={styles.resultIconError} />
            <Text strong>{t('exec.failed')}</Text>
          </>
        )}
      </div>
      <Paragraph type="secondary">{result.message}</Paragraph>
      <pre className={styles.execPre}>{JSON.stringify(payload, null, 2)}</pre>
    </div>
  )
}

function DocsTab({ example }: { example: Example }) {
  const { t } = useTranslation()

  return example.documentation ? (
    <MarkdownRenderer content={example.documentation} />
  ) : (
    <Empty description={t('detail.noDocs')} />
  )
}

function ExampleTabs({
  activeTabKey,
  example,
  execution,
  onTabChange,
}: {
  activeTabKey: string
  example: Example
  execution: ExecutionState
  onTabChange: (key: string) => void
}) {
  const { t } = useTranslation()
  const items = useMemo(
    () => [
      {
        key: 'overview',
        label: t('detail.tab.overview'),
        children: <OverviewTab example={example} />,
      },
      {
        key: 'code',
        label: t('detail.tab.code'),
        children: <CodeTab example={example} />,
      },
      {
        key: 'execute',
        label: t('detail.tab.execute'),
        children: <ExecuteTab execution={execution} />,
      },
      {
        key: 'docs',
        label: t('detail.tab.docs'),
        children: <DocsTab example={example} />,
      },
    ],
    [example, execution, t]
  )

  return <Tabs activeKey={activeTabKey} onChange={onTabChange} items={items} />
}

function QuickActions({
  example,
  executing,
  onDownload,
  onExecute,
  onOpenInDesigner,
}: {
  example: Example
  executing: boolean
  onDownload: () => void
  onExecute: () => void
  onOpenInDesigner: () => void
}) {
  const { t } = useTranslation()

  return (
    <section className={styles.sidebarPanel}>
      <h2 className={styles.sidebarPanelTitle}>{t('detail.quickActions')}</h2>
      <Space vertical size="middle" className={styles.sidebarStack}>
        <Button
          type="primary"
          block
          size="large"
          icon={<EditOutlined />}
          onClick={onOpenInDesigner}
        >
          {t('detail.openInDesigner')}
        </Button>
        <Button
          block
          size="large"
          icon={<PlayCircleOutlined />}
          loading={executing}
          onClick={onExecute}
        >
          {executing ? t('exec.executing') : t('exec.execute')}
        </Button>
        <Button block size="large" icon={<DownloadOutlined />} onClick={onDownload}>
          {example.code ? t('common.download') : t('detail.noCode')}
        </Button>
      </Space>
    </section>
  )
}

function ExampleSidebar({
  allExamples,
  contentRef,
  example,
  executing,
  examplesPath,
  learningProgress,
  onDownload,
  onExecute,
  onOpenInDesigner,
}: {
  allExamples: Example[]
  contentRef: RefObject<HTMLDivElement | null>
  example: Example
  executing: boolean
  examplesPath: string
  learningProgress: ReturnType<typeof useLearningProgress>
  onDownload: () => void
  onExecute: () => void
  onOpenInDesigner: () => void
}) {
  return (
    <aside className={styles.sidebarColumn}>
      <div className={styles.sidebarStack}>
        <LearningProgressCard exampleId={example.id} learningProgress={learningProgress} />
        <QuickActions
          example={example}
          executing={executing}
          onDownload={onDownload}
          onExecute={onExecute}
          onOpenInDesigner={onOpenInDesigner}
        />
        <TableOfContents contentRef={contentRef} />
        <RelatedExamples
          currentExample={example}
          allExamples={allExamples}
          maxRecommendations={3}
          examplesPath={examplesPath}
        />
      </div>
    </aside>
  )
}

function ExampleDetail() {
  const { message } = App.useApp()
  const { id } = useParams<{ id: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const { i18n, t } = useTranslation()
  const contentRef = useRef<HTMLDivElement>(null)
  const [activeTabKey, setActiveTabKey] = useState<string>('overview')
  const learningProgress = useLearningProgress()
  const {
    allExamples,
    error,
    example: sourceExample,
    loading,
  } = useExampleData(id, learningProgress.setTotalExamples)
  const language = i18n.resolvedLanguage || i18n.language
  const example = sourceExample ? localizeExample(sourceExample, t, language) : null
  const localizedExamples = useMemo(
    () => allExamples.map((item) => localizeExample(item, t, language)),
    [allExamples, language, t]
  )
  const availableExamples =
    localizedExamples.length > 0 ? localizedExamples : example ? [example] : []
  usePageTitle('pageTitle.learn.detail', example?.name)
  const execution = useExampleExecution(example)
  const examplesPath = getExamplesPath(location.state)

  useEffect(() => {
    setActiveTabKey('overview')
  }, [id])

  const handleBack = useCallback(() => {
    void navigate(examplesPath)
  }, [examplesPath, navigate])

  const handleOpenInDesigner = useCallback(() => {
    if (!example) return

    openDesignerFromExample(navigate, {
      modelType: example.modelType,
      exampleId: example.id,
    })
    message.success(t('detail.openInDesignerSuccess', { type: example.modelType }))
  }, [example, navigate, t])

  const handleDownload = useCallback(() => {
    if (!example?.code) {
      message.warning(t('detail.noCode'))
      return
    }
    downloadExampleXml(example)
    message.success(t('detail.downloadSuccess', { name: example.name }))
  }, [example, t])

  if (loading) return <ExampleDetailSkeleton />
  if (error || !example) {
    return (
      <ExampleDetailError description={error || t('error.exampleNotFound')} onBack={handleBack} />
    )
  }

  return (
    <div className={`fade-in ${styles.detailPage}`}>
      <Button icon={<ArrowLeftOutlined />} onClick={handleBack} className={styles.backButton}>
        {t('detail.back')}
      </Button>

      <ExampleHeader example={example} />

      <div className={styles.contentLayout}>
        <div className={styles.mainColumn}>
          <div ref={contentRef} className={styles.tabPanel}>
            <ExampleTabs
              activeTabKey={activeTabKey}
              example={example}
              execution={execution}
              onTabChange={setActiveTabKey}
            />
          </div>
          <div className={styles.feedbackSection}>
            <FeedbackActions key={example.id} exampleId={example.id} exampleTitle={example.name} />
          </div>
        </div>

        <ExampleSidebar
          allExamples={availableExamples}
          contentRef={contentRef}
          example={example}
          executing={execution.executing}
          examplesPath={examplesPath}
          learningProgress={learningProgress}
          onDownload={handleDownload}
          onExecute={() => {
            setActiveTabKey('execute')
            void execution.onExecute()
          }}
          onOpenInDesigner={handleOpenInDesigner}
        />
      </div>

      <ExampleNavigation
        currentExample={example}
        allExamples={availableExamples}
        examplesPath={examplesPath}
      />
    </div>
  )
}

export default ExampleDetail
