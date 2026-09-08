import { expect, test } from '@playwright/test'

const TIMEOUT = 25_000
const PRIMARY_ROUTES = [
  '/learn',
  '/learn/examples',
  '/learn/examples/learn.tbbpm.greeting',
  '/build',
  '/build/designer?modelType=bpmn&source=new',
  '/build/designer?modelType=tbbpm&source=new',
  '/operate',
  '/operate/processes',
  '/operate/deployments',
  '/operate/deployments/deploy-001',
  '/operate/deploy-wizard',
  '/operate/monitoring',
  '/operate/logs',
  '/settings',
  '/500',
  '/this-route-does-not-exist',
] as const

test.describe('Workbench semantic and edge-state journeys', () => {
  test('keyboard users can skip repeated navigation and get one main landmark', async ({
    browserName,
    page,
  }) => {
    await page.goto('/learn')
    await page.keyboard.press(browserName === 'webkit' ? 'Alt+Tab' : 'Tab')
    const skipLink = page.getByRole('link', { name: /跳转到主要内容|Skip to main content/i })
    await expect(skipLink).toBeFocused()
    await expect(skipLink).toBeVisible()
    await page.keyboard.press('Enter')
    await expect(page.locator('main#main-content')).toBeFocused()

    await page.goto('/build/designer?modelType=tbbpm&source=new', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('main')).toHaveCount(1)
    const processHeading = page.getByRole('heading', { level: 1 })
    await expect(processHeading).toBeAttached()
    const processName = (await processHeading.textContent())?.trim() || 'CompileFlow'
    await expect(page).toHaveTitle(new RegExp(processName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  })

  test('hub entry rails expose real list and list-item semantics', async ({ page }) => {
    for (const route of ['/learn', '/build', '/operate']) {
      await page.goto(route)
      const hubList = page.locator('[data-hub-surface] > div section ul').first()
      await expect(hubList).toBeVisible({ timeout: TIMEOUT })
      await expect(hubList.locator(':scope > li')).toHaveCount(route === '/operate' ? 4 : 3)
      await expect(hubList.locator(':scope > li > button').first()).toBeVisible()
    }
  })

  test('learn hero product image is loaded, not only present in the DOM', async ({ page }) => {
    await page.goto('/learn')
    const image = page.getByTestId('product-shot').locator('img')
    await expect(image).toBeVisible({ timeout: TIMEOUT })
    await expect
      .poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth))
      .toBeGreaterThan(0)
  })

  test('home document title follows the selected language', async ({ page }) => {
    await page.goto('/learn')
    await expect(page).toHaveTitle('学习 | CompileFlow Workbench')
    await page.getByRole('button', { name: /切换到英文|Switch to English/i }).click()
    await expect(page).toHaveTitle('Learn | CompileFlow Workbench')
  })

  test('breadcrumbs are keyboard links and dismissing the demo banner lasts across routes', async ({
    page,
  }) => {
    await page.goto('/operate/processes')
    const breadcrumbs = page.getByRole('navigation', {
      name: /面包屑导航|Breadcrumb/i,
    })
    await expect(breadcrumbs.getByRole('link', { name: /首页|Home/i })).toHaveAttribute(
      'href',
      '/learn'
    )
    await expect(breadcrumbs.getByRole('link', { name: /运维|Operate/i })).toHaveAttribute(
      'href',
      '/operate'
    )

    const dismiss = page.getByRole('button', { name: /关闭提示|Dismiss/i })
    await dismiss.click()
    await expect(dismiss).toHaveCount(0)
    await page.getByRole('menuitem', { name: /部署|Deployments/i }).click()
    await expect(page).toHaveURL('/operate/deployments')
    await expect(page.getByRole('button', { name: /关闭提示|Dismiss/i })).toHaveCount(0)
  })

  test('primary routes render without browser warnings or errors', async ({ page }) => {
    test.setTimeout(90_000)
    const diagnostics: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'warning' || message.type() === 'error') {
        diagnostics.push(`${page.url()} [${message.type()}] ${message.text()}`)
      }
    })
    page.on('pageerror', (error) => diagnostics.push(`${page.url()} [pageerror] ${error.message}`))

    for (const route of PRIMARY_ROUTES) {
      await page.goto(route)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: TIMEOUT })
      await page.evaluate(
        () =>
          new Promise<void>((resolve) =>
            requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
          )
      )
    }

    expect(diagnostics).toEqual([])
  })

  test('every primary route renders complete English copy without leaking translation keys', async ({
    page,
  }) => {
    test.setTimeout(90_000)
    await page.addInitScript(() => localStorage.setItem('compileflow:language', 'en'))
    const translationKey = /\b[A-Za-z][\w-]*(?:\.[A-Za-z][\w-]*){1,}\b/

    for (const route of PRIMARY_ROUTES) {
      await page.goto(route)
      await expect(page.locator('html')).toHaveAttribute('lang', 'en')
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: TIMEOUT })
      expect(
        (await page.locator('html > body').innerText()).match(translationKey),
        route
      ).toBeNull()
    }
  })

  test('designer palette and monitoring visualizations have meaningful accessible names', async ({
    page,
  }) => {
    await page.goto('/build/designer?modelType=bpmn&source=new', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('textbox', { name: /搜索节点|Search nodes/i }).first()).toBeVisible(
      { timeout: TIMEOUT }
    )

    await page.goto('/operate/monitoring')
    await expect(page.getByRole('combobox', { name: /时间范围|Time range/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByRole('img', { name: /执行趋势|Trends/i })).toBeVisible()
    await expect(page.getByRole('img', { name: /版本分布|Version distribution/i })).toBeVisible()
    await expect(page.getByRole('img', { name: /流程排行|Process ranking/i })).toBeVisible()
    const recentErrors = page.locator('section').filter({
      has: page.getByRole('heading', { name: /最近错误|Recent errors/i }),
    })
    await expect(recentErrors.getByRole('list')).toBeVisible()
    await expect(recentErrors.getByRole('listitem').first()).toBeVisible()
    await expect(page.getByRole('application')).toHaveCount(0)
  })

  test('missing resources keep a localized, persistent recovery state', async ({ page }) => {
    await page.goto('/learn/examples/not-found-example')
    await expect(page.getByRole('alert')).toContainText(/示例不存在|Example not found/i, {
      timeout: TIMEOUT,
    })
    await expect(page.getByText('Example not found: not-found-example')).toHaveCount(0)

    await page.goto('/learn/examples/learn.tbbpm.greeting')
    await expect(page.getByRole('heading', { name: /TBBPM/ })).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByRole('radio')).toHaveCount(0)

    await page.goto('/operate/deployments/missing-id')
    const alert = page
      .getByRole('alert')
      .filter({ hasText: /加载部署详情失败|deployment details/i })
    await expect(alert).toBeVisible({ timeout: TIMEOUT })
    await expect(alert.getByRole('button', { name: /重\s*试|Retry/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /部署历史|Deployment history/i })).toHaveCount(0)
  })

  test('invalid designer deep links fail locally without crashing the application shell', async ({
    page,
  }) => {
    const errors: string[] = []
    page.on('pageerror', (error) => errors.push(error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(message.text())
    })

    await page.goto('/build/designer?modelType=unknown&source=new', {
      waitUntil: 'domcontentloaded',
    })
    await expect(page.getByRole('banner', { name: /主导航|Main navigation/i })).toBeVisible()
    await expect(
      page.getByText(/设计器链接不完整或无效|designer link is incomplete or invalid/i)
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.getByRole('button', { name: /返回工作区|Back to workspace/i }).click()
    await expect(page).toHaveURL('/build')
    expect(errors).toEqual([])
  })

  test('learning progress is correct on first direct visit and survives reload', async ({
    page,
  }) => {
    await page.goto('/learn/examples/learn.tbbpm.greeting')
    await expect(page.getByText('0 / 4', { exact: true })).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByRole('heading', { name: '原理说明' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '下一步' })).toBeVisible()
    await expect(page.getByText('声明流程输入变量和返回变量')).toBeVisible()

    await page.getByRole('button', { name: /切换到英文|Switch to English/i }).click()
    await expect(page.getByRole('heading', { name: 'How it works' })).toBeVisible()
    await expect(page.getByText('Declare process input and return variables')).toBeVisible()
    await page.getByRole('button', { name: /切换到中文|Switch to Chinese/i }).click()
    await expect(page.getByRole('button', { name: /下一个 BPMN 金额路由/ })).toBeVisible()

    await expect(page.getByRole('radio')).toHaveCount(0)
    await expect(page.locator('main aside')).toHaveCount(1)
    await expect(page.getByRole('region', { name: /这个示例有帮助吗/ })).toBeVisible()

    await page.getByRole('tab', { name: '执行' }).click()
    await expect(page.getByRole('navigation', { name: '本页目录' })).toContainText(
      '输入参数（JSON）'
    )
    await expect(page.getByRole('navigation', { name: '本页目录' })).not.toContainText('你将学到')

    const params = page.getByRole('textbox', { name: '输入参数（JSON）' })
    await params.fill('{"cursor":1}')
    await params.press('ArrowRight')
    await expect(page).toHaveURL(/learn\/examples\/learn\.tbbpm\.greeting$/)

    await page.getByRole('button', { name: /标记为已完成|Mark as complete/i }).click()
    await expect(page.getByText('1 / 4', { exact: true })).toBeVisible()
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.getByText('1 / 4', { exact: true })).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByText(/已完成|Completed/i).first()).toBeVisible()
  })

  test('process filters survive reload and remain visible in the URL and controls', async ({
    page,
  }) => {
    await page.goto('/operate/processes?keyword=%E8%AE%A2%E5%8D%95&type=BPMN')
    const search = page.getByRole('searchbox', { name: /搜索流程|Search flows/i })
    const type = page.getByRole('combobox', { name: /全部类型|All types/i })
    const typeControl = type.locator('..')

    await expect(search).toHaveValue('订单', { timeout: TIMEOUT })
    await expect(typeControl).toContainText('BPMN')
    await expect(page.getByText('订单', { exact: true })).toBeVisible()

    await page.reload()
    await expect(search).toHaveValue('订单', { timeout: TIMEOUT })
    await expect(typeControl).toContainText('BPMN')
    await expect(page).toHaveURL(/keyword=%E8%AE%A2%E5%8D%95/)
    await expect(page).toHaveURL(/type=BPMN/)

    await page.getByText(/清空筛选|Clear filters/i).click()
    await expect(search).toHaveValue('')
    await expect(typeControl).not.toContainText('BPMN')
    await expect(type).toHaveAttribute('aria-label', /全部类型|All types/i)
    await expect(page).toHaveURL('/operate/processes')
  })

  test('deployment search and filters query the complete result set and survive reload', async ({
    page,
  }) => {
    test.setTimeout(60_000)
    await page.goto('/operate/deployments?page=broken&statusFilter=invalid&source=shared')
    await expect(page).toHaveURL('/operate/deployments?source=shared', { timeout: TIMEOUT })
    await page.goto('/operate/deployments?page=2&source=shared')
    await expect(page).toHaveURL('/operate/deployments?source=shared', { timeout: TIMEOUT })

    await page.goto('/operate/deployments')
    const search = page.getByRole('searchbox', {
      name: /按流程编码或部署 ID 搜索|Search by process code or deployment ID/i,
    })
    const deploymentRows = page.locator('main .ant-table-tbody > tr.ant-table-row')
    await expect(deploymentRows.first()).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('main')).not.toContainText(/\ball_at_once\b|\bcanary\b/)
    await expect(page.locator('main')).toContainText(/全量切换|灰度发布|All at once|Canary release/)
    await search.fill('PAYMENT')

    await expect(deploymentRows).toHaveCount(1, { timeout: TIMEOUT })
    await expect(deploymentRows).toContainText('deploy-002')
    await expect(page).toHaveURL(/searchText=PAYMENT/)

    await page.reload()
    await expect(search).toHaveValue('PAYMENT')
    await expect(deploymentRows).toHaveCount(1, { timeout: TIMEOUT })

    await search.fill('')
    await expect(page).not.toHaveURL(/searchText=/)
    await expect(deploymentRows).toHaveCount(4, { timeout: TIMEOUT })
    const alias = page.getByRole('combobox', {
      name: /选择别名|Select alias/i,
    })
    await alias.click()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')

    await expect(deploymentRows).toHaveCount(1, { timeout: TIMEOUT })
    await expect(deploymentRows).toContainText('deploy-003')
    await expect(page).toHaveURL(/aliasFilter=staging/)
  })

  test('deployment detail identifies both the process and immutable deployment id', async ({
    page,
  }) => {
    await page.goto('/operate/deployments/deploy-001')
    await expect(page.getByRole('heading', { name: 'order-approval-bpmn' })).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText('deploy-001', { exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: /部署历史|Deployment history/i })).toBeVisible()

    await page.goto('/operate/deployments/deploy-002')
    await expect(
      page.getByRole('slider', { name: /灰度流量权重|Canary traffic weight/i })
    ).toHaveAttribute('aria-valuetext', /bps/)
  })

  test('invalid and incomplete log filters normalize without deleting unrelated URL state', async ({
    page,
  }) => {
    await page.goto(
      '/operate/logs?startTime=broken&endTime=also-broken&status=INVALID&source=shared'
    )
    await expect(page).toHaveURL('/operate/logs?source=shared', { timeout: TIMEOUT })

    await page.goto('/operate/logs?startTime=2026-02-10T00%3A00%3A00.000Z&source=shared')
    await expect(page).toHaveURL('/operate/logs?source=shared', { timeout: TIMEOUT })
    await expect(
      page.getByRole('textbox', { name: /执行时间范围|Execution time range/i })
    ).toHaveCount(2)
  })

  test('monitoring time range survives reload and invalid values normalize', async ({ page }) => {
    await page.goto('/operate/monitoring?timeRange=7d&source=shared')
    const range = page.getByRole('combobox', { name: /时间范围|Time range/i })
    await expect(range.locator('..')).toContainText(/近 7 天|7d/, { timeout: TIMEOUT })
    await page.reload()
    await expect(range.locator('..')).toContainText(/近 7 天|7d/, { timeout: TIMEOUT })
    await expect(page).toHaveURL(/timeRange=7d/)
    await expect(page).toHaveURL(/source=shared/)

    await page.goto('/operate/monitoring?timeRange=invalid&source=shared')
    await expect(page).toHaveURL('/operate/monitoring?source=shared', { timeout: TIMEOUT })
    await expect(range.locator('..')).toContainText(/近 24 小时|24h/)
  })

  test('build offers TBBPM first and every quick template opens the correct canvas', async ({
    page,
  }) => {
    await page.goto('/build')

    const heroActions = page
      .locator('main')
      .getByRole('button')
      .filter({
        hasText: /新建 (?:TBBPM|BPMN)|New (?:TBBPM|BPMN)/,
      })
    await expect(heroActions.nth(0)).toContainText('TBBPM')
    await expect(heroActions.nth(1)).toContainText('BPMN')

    const templates = [
      { name: /TBBPM 问候示例|TBBPM Greeting/i, type: 'tbbpm', nodes: 3, edges: 2 },
      { name: /TBBPM 入门模板|TBBPM Starter/i, type: 'tbbpm', nodes: 2, edges: 1 },
      { name: /BPMN 入门模板|BPMN Starter/i, type: 'bpmn', nodes: 2, edges: 1 },
      { name: /空白 BPMN|Blank BPMN/i, type: 'bpmn', nodes: 0, edges: 0 },
    ] as const

    const templateButtons = page.getByRole('button').filter({
      hasText:
        /TBBPM 问候示例|TBBPM 入门模板|BPMN 入门模板|空白 BPMN|TBBPM Greeting|TBBPM Starter|BPMN Starter|Blank BPMN/i,
    })
    await expect(templateButtons).toHaveCount(4)
    await expect(templateButtons.nth(0)).toContainText('TBBPM')
    await expect(templateButtons.nth(1)).toContainText('TBBPM')

    for (const template of templates) {
      await page.getByRole('button', { name: template.name }).click()
      await expect(page).toHaveURL(
        new RegExp(`/build/designer\\?modelType=${template.type}.*source=workspaceProcess`),
        { timeout: TIMEOUT }
      )
      await expect(page.getByText(`${template.nodes} 节点`, { exact: true })).toBeVisible({
        timeout: TIMEOUT,
      })
      await expect(page.getByText(`${template.edges} 连线`, { exact: true })).toBeVisible()
      if (template.name.source.includes('问候')) {
        const [taskBox, endBox] = await Promise.all([
          page.locator('.x6-node[data-cell-id="buildGreeting"]').boundingBox(),
          page.locator('.x6-node[data-cell-id="end"]').boundingBox(),
        ])
        expect(taskBox).not.toBeNull()
        expect(endBox).not.toBeNull()
        expect(endBox!.x - (taskBox!.x + taskBox!.width)).toBeGreaterThan(20)
      }
      await page.goto('/build')
    }
  })

  test('operate create menu offers TBBPM before BPMN', async ({ page }) => {
    await page.goto('/operate/processes')
    await page.getByRole('button', { name: /创建流程|Create flow/i }).click()
    const createItems = page.locator('.ant-dropdown-menu').getByRole('menuitem')
    await expect(createItems.nth(0)).toContainText('TBBPM')
    await expect(createItems.nth(1)).toContainText('BPMN')
  })

  test('theme and language changes synchronize across open tabs', async ({ context, page }) => {
    const secondPage = await context.newPage()
    await Promise.all([page.goto('/settings'), secondPage.goto('/settings')])

    await page.getByRole('button', { name: /切换到深色模式|Switch to dark mode/i }).click()
    await expect(secondPage.locator('html')).toHaveAttribute('data-theme', 'dark')

    await page.getByRole('button', { name: /切换到英文|Switch to English/i }).click()
    await expect(secondPage.getByRole('heading', { level: 1, name: 'Settings' })).toBeVisible()
    await expect(secondPage.locator('html')).toHaveAttribute('lang', 'en')
    await secondPage.close()
  })

  test('slow lazy modules expose immediate progress instead of dead clicks', async ({ page }) => {
    await page.route('**/src/shell/components/GlobalSearch.tsx*', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1_000))
      await route.continue()
    })
    await page.goto('/learn')
    await page.getByRole('button', { name: /打开全局搜索|Open global search/i }).click()
    const progress = page.getByRole('status').filter({
      hasText: /正在打开全局搜索|Opening global search/i,
    })
    await expect(progress).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(progress).toBeHidden()
    await page.getByRole('button', { name: /打开全局搜索|Open global search/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: TIMEOUT })
  })

  test('standard pages do not create document-level horizontal overflow on mobile', async ({
    page,
  }) => {
    test.setTimeout(90_000)
    await page.setViewportSize({ width: 390, height: 844 })
    const routes = PRIMARY_ROUTES.filter((route) => !route.startsWith('/build/designer'))

    for (const route of routes) {
      await page.goto(route)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: TIMEOUT })
      const dimensions = await page.evaluate(() => ({
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: window.innerWidth,
      }))
      expect(dimensions.documentWidth, route).toBeLessThanOrEqual(dimensions.viewportWidth + 1)
    }
  })

  test('mobile error pages keep recovery actions in the first viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })

    await page.goto('/500')
    await expect(page.getByRole('button', { name: /刷新页面|Reload/i })).toBeInViewport({
      timeout: TIMEOUT,
    })
    await expect(page.getByRole('button', { name: /返回首页|Back home/i }).last()).toBeInViewport()
    await expect(page.locator('main')).toHaveCSS('padding', '0px')

    await page.goto('/missing-route')
    await expect(page.getByRole('button', { name: /返回首页|Back home/i }).last()).toBeInViewport({
      timeout: TIMEOUT,
    })

    await page.goto('/build/designer?modelType=bpmn&source=new', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { level: 1 })).toBeAttached({ timeout: TIMEOUT })
    const designerViewport = await page.evaluate(() => ({
      documentHeight: document.documentElement.scrollHeight,
      viewportHeight: window.innerHeight,
    }))
    expect(designerViewport.documentHeight).toBeLessThanOrEqual(designerViewport.viewportHeight + 1)
  })

  test('unsaved designer changes guard app navigation and browser history', async ({ page }) => {
    test.setTimeout(90_000)
    const editProcessName = async (name: string) => {
      await page.locator('.flow-name-display').click()
      const input = page.locator('.flow-name-editor input')
      await input.fill(name)
      await input.press('Enter')
      await expect(page.locator('.flow-name-text')).toHaveText(name)
      await expect(page.locator('.modified-indicator')).toBeVisible()
    }
    const unsavedDialog = page.getByRole('dialog').filter({
      has: page.getByText(/未保存的更改|Unsaved changes/i),
    })

    await page.goto('/build/designer?modelType=tbbpm&source=new', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.flow-name-display')).toBeVisible({ timeout: TIMEOUT })
    await editProcessName('导航保护验证')

    await page.getByRole('menuitem', { name: /运维|Operate/i }).click()
    await expect(unsavedDialog).toBeVisible({ timeout: TIMEOUT })
    await unsavedDialog.getByRole('button', { name: /取\s*消|Cancel/i }).click()
    await expect(unsavedDialog).toBeHidden({ timeout: TIMEOUT })
    await expect(page).toHaveURL(/\/build\/designer/)

    await page.getByRole('menuitem', { name: /运维|Operate/i }).click()
    await unsavedDialog.getByRole('button', { name: /保存并离开|Save and leave/i }).click()
    await expect(page).toHaveURL('/operate', { timeout: TIMEOUT })

    await page.getByRole('menuitem', { name: /构建|Build/i }).click()
    await expect(page).toHaveURL('/build', { timeout: TIMEOUT })
    await page.getByRole('button', { name: /TBBPM/ }).first().click()
    await expect(page).toHaveURL(/\/build\/designer\?modelType=tbbpm/)
    await expect(page.locator('.flow-name-display')).toBeVisible({ timeout: TIMEOUT })
    await editProcessName('历史保护验证')

    await page.evaluate(() => window.history.back())
    await expect(unsavedDialog).toBeVisible({ timeout: TIMEOUT })
    await unsavedDialog.getByRole('button', { name: /直接离开|Leave without saving/i }).click()
    await expect(page).toHaveURL('/build', { timeout: TIMEOUT })
  })
})
