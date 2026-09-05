import { RocketOutlined, SearchOutlined } from '@ant-design/icons'
import { AutoComplete, Button, Input, Select, Table } from 'antd'
import React, { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useDeploymentFilters } from '@/operate/hooks/useDeploymentFilters'
import { useDeploymentManagementData } from '@/operate/hooks/useDeploymentManagementData'
import { createDeploymentColumns } from '@/operate/pages/deploymentManagementColumns'
import { LoadErrorAlert } from '@/shared/components/LoadErrorAlert'
import { DataPageShell, FilterBar } from '@/shared/components/page'
import { buildDeploymentDetailPath, DEPLOYMENT_ALIAS_PRESETS, ROUTES } from '@/shared/constants'
import { useDebounce } from '@/shared/hooks/useDebounce'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { usePaginationItemRender } from '@/shared/hooks/usePaginationItemRender'

const { Option } = Select

const DeploymentManagement: React.FC = () => {
  usePageTitle('pageTitle.operate.deployments')
  const { t } = useTranslation()
  const paginationItemRender = usePaginationItemRender()
  const navigate = useNavigate()

  const { filters, updateFilter, clearAll } = useDeploymentFilters()
  const keyword = useDebounce(filters.searchText.trim(), 300)
  const { deployments, error, loading, hasMore, loadMore, reload, handleRestoreBaseline } =
    useDeploymentManagementData({
      keyword: keyword || undefined,
      alias: filters.aliasFilter || undefined,
      status: filters.statusFilter || undefined,
    })

  const columns = useMemo(
    () =>
      createDeploymentColumns({
        t,
        onRestoreBaseline: handleRestoreBaseline,
        onViewDetail: (deploymentId) => navigate(buildDeploymentDetailPath(deploymentId)),
      }),
    [t, handleRestoreBaseline, navigate]
  )

  const activeFilters = useMemo(() => {
    const items = []
    if (filters.searchText) items.push({ key: 'search', label: filters.searchText })
    if (filters.statusFilter) items.push({ key: 'status', label: filters.statusFilter })
    if (filters.aliasFilter) items.push({ key: 'alias', label: filters.aliasFilter })
    return items
  }, [filters])

  return (
    <DataPageShell
      title={t('deployment.management')}
      accent="operate"
      eyebrow={t('nav.operate')}
      subtitle={t('deployment.managementDesc')}
      actions={
        <Button
          type="primary"
          icon={<RocketOutlined />}
          onClick={() => navigate(ROUTES.OPERATE_DEPLOY_WIZARD)}
        >
          {t('deployment.newDeployment')}
        </Button>
      }
      filters={
        <FilterBar
          activeFilters={activeFilters}
          onRemoveFilter={(key) => {
            if (key === 'search') updateFilter('searchText', '')
            if (key === 'status') updateFilter('statusFilter', '')
            if (key === 'alias') updateFilter('aliasFilter', '')
          }}
          onClearAll={clearAll}
          activeLabel={t('filters.active')}
          clearLabel={t('filters.clear')}
        >
          <Input.Search
            prefix={<SearchOutlined aria-hidden="true" />}
            enterButton={<SearchOutlined aria-label={t('common.search')} />}
            placeholder={t('deployment.searchPlaceholder')}
            aria-label={t('deployment.searchPlaceholder')}
            value={filters.searchText}
            maxLength={128}
            onChange={(e) => updateFilter('searchText', e.target.value)}
            onSearch={(v) => updateFilter('searchText', v)}
            allowClear
            style={{ width: 220 }}
          />
          <Select
            placeholder={t('deployment.filterStatus')}
            aria-label={t('deployment.filterStatus')}
            value={filters.statusFilter || undefined}
            onChange={(v) => updateFilter('statusFilter', v)}
            allowClear
            style={{ width: 130 }}
          >
            {['in_progress', 'completed', 'aborted'].map((s) => (
              <Option key={s} value={s}>
                {t(`deployment.status.${s}`)}
              </Option>
            ))}
          </Select>
          <AutoComplete
            placeholder={t('deployment.filterAlias')}
            aria-label={t('deployment.filterAlias')}
            value={filters.aliasFilter || undefined}
            onChange={(v) => updateFilter('aliasFilter', v)}
            allowClear
            options={DEPLOYMENT_ALIAS_PRESETS.map((preset) => ({
              value: preset.value,
              label: t(preset.labelKey),
            }))}
            style={{ width: 160 }}
          />
        </FilterBar>
      }
    >
      {error && <LoadErrorAlert onRetry={reload} />}
      <Table
        columns={columns}
        dataSource={deployments}
        rowKey="id"
        loading={loading}
        scroll={{ x: 'max-content' }}
        pagination={{
          pageSize: 10,
          itemRender: paginationItemRender,
          showTotal: (total) => t('common.totalItems', { total }),
        }}
      />
      {hasMore && (
        <Button block loading={loading} onClick={loadMore}>
          {t('common.loadMore')}
        </Button>
      )}
    </DataPageShell>
  )
}

export default DeploymentManagement
