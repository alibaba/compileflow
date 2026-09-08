import { expect, type Locator, type Page, test } from '@playwright/test'

const TIMEOUT = 20_000

const ACTION_CASES = [
  { modelType: 'tbbpm', nodeLabel: '自动任务', implementation: 'spring' },
  { modelType: 'tbbpm', nodeLabel: '脚本任务', implementation: 'script' },
  { modelType: 'bpmn', nodeLabel: '服务任务', implementation: 'spring' },
  { modelType: 'bpmn', nodeLabel: '脚本任务', implementation: 'script' },
] as const

const CONTAINER_CASES = [
  {
    modelType: 'tbbpm',
    outerLabel: '内嵌 BPM',
    innerLabel: '条件循环',
    childField: '内部节点',
    innerChildField: '循环体节点',
    outerTag: 'subBpm',
  },
  {
    modelType: 'tbbpm',
    outerLabel: '条件循环',
    innerLabel: '集合遍历',
    childField: '循环体节点',
    innerChildField: '循环体节点',
    outerTag: 'while',
  },
  {
    modelType: 'tbbpm',
    outerLabel: '集合遍历',
    innerLabel: '内嵌 BPM',
    childField: '循环体节点',
    innerChildField: '内部节点',
    outerTag: 'foreach',
  },
  {
    modelType: 'bpmn',
    outerLabel: '嵌入式子流程',
    innerLabel: '嵌入式子流程',
    childField: '直属子节点',
    innerChildField: '直属子节点',
    outerTag: 'bpmn:subProcess',
  },
] as const

async function gotoDesigner(page: Page, modelType: 'tbbpm' | 'bpmn') {
  await page.goto(`/build/designer?modelType=${modelType}&source=new`)
  await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
}

async function paletteItem(page: Page, label: string): Promise<Locator> {
  const item = page.locator('.drag-palette-item').filter({ hasText: label }).first()
  if (!(await item.isVisible())) {
    const category = page
      .locator('.palette-category-title, .ant-collapse-header')
      .filter({ hasText: /任务|组合|循环|子流程/ })
    for (let index = 0; index < (await category.count()) && !(await item.isVisible()); index += 1) {
      await category.nth(index).click()
    }
  }
  await expect(item).toBeVisible({ timeout: TIMEOUT })
  return item
}

async function addNode(page: Page, label: string) {
  const item = await paletteItem(page, label)
  const nodes = page.locator('.x6-node')
  const count = await nodes.count()
  await item.focus()
  await page.keyboard.press('Enter')
  await expect(nodes).toHaveCount(count + 1, { timeout: TIMEOUT })
}

function propertyPanel(page: Page, modelType: 'tbbpm' | 'bpmn') {
  return page.locator(`.${modelType}-designer-right-sider`)
}

async function openSpecificProperties(page: Page, modelType: 'tbbpm' | 'bpmn') {
  const panel = propertyPanel(page, modelType)
  await panel.locator('.ant-tabs-tab').nth(1).click()
  const pane = panel.locator('.ant-tabs-tabpane-active')
  await expect(pane).toBeVisible({ timeout: TIMEOUT })
  return pane
}

async function selectCanvasNode(page: Page, name: string) {
  await page.getByRole('button', { name: '搜索节点（Ctrl+F）' }).click()
  const dialog = page.getByRole('dialog').filter({ hasText: '搜索节点' })
  await dialog.getByRole('searchbox', { name: '搜索节点' }).fill(name)
  await dialog.locator('.node-search-result-item').filter({ hasText: name }).click()
}

async function selectOption(scope: Locator, label: string | RegExp, option: string | RegExp) {
  await scope.getByRole('combobox', { name: label, exact: typeof label === 'string' }).click()
  await scope
    .page()
    .locator('.ant-select-dropdown:visible .ant-select-item-option-content')
    .filter({ hasText: option })
    .click()
}

async function addProcessVariable(page: Page, name: string) {
  await page.getByRole('button', { name: /变量管理/ }).click()
  const variables = page.getByRole('dialog', { name: /流程变量/ })
  await variables.getByRole('button', { name: /添加变量/ }).click()
  const editor = page.getByRole('dialog', { name: /添加变量/ })
  await editor.getByRole('textbox', { name: /变量名/ }).fill(name)
  await editor.getByRole('textbox', { name: '数据类型（Java 类名）' }).fill('java.lang.String')
  await editor.getByRole('button', { name: /保\s*存/ }).click()
  await expect(variables.getByText(name)).toBeVisible({ timeout: TIMEOUT })
  await variables.getByRole('button', { name: /关闭/ }).click()
}

function tbbpmContainerXml(outerTag: string, innerTag: string): string {
  const attributes = (tag: string) => {
    if (tag === 'while') return ' condition="active" maxIterations="10"'
    if (tag === 'foreach') {
      return ' collection="items" item="item" itemType="java.lang.String"'
    }
    return ''
  }
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="nested_scopes" name="Nested scopes">
  <var name="items" dataType="java.util.List" inOutType="param"/>
  <start id="rootStart"><transition to="outer"/></start>
  <${outerTag} id="outer" name="Outer scope"${attributes(outerTag)}>
    <transition to="rootEnd"/>
    <start id="outerStart"><transition to="inner"/></start>
    <${innerTag} id="inner" name="Inner scope"${attributes(innerTag)}>
      <transition to="outerEnd"/>
      <start id="innerStart"><transition to="innerEnd"/></start>
      <end id="innerEnd"/>
    </${innerTag}>
    <end id="outerEnd"/>
  </${outerTag}>
  <end id="rootEnd"/>
</bpm>`
}

const NESTED_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:cf="http://www.compileflow.org"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  id="Definitions_nested" targetNamespace="urn:compileflow:test">
  <bpmn:process id="nested_scopes" name="Nested scopes" isExecutable="true">
    <bpmn:startEvent id="rootStart"/>
    <bpmn:subProcess id="outer" name="Outer scope">
      <bpmn:startEvent id="outerStart"/>
      <bpmn:subProcess id="inner" name="Inner scope">
        <bpmn:startEvent id="innerStart"/>
        <bpmn:endEvent id="innerEnd"/>
        <bpmn:sequenceFlow id="innerFlow" sourceRef="innerStart" targetRef="innerEnd"/>
      </bpmn:subProcess>
      <bpmn:endEvent id="outerEnd"/>
      <bpmn:sequenceFlow id="outerIn" sourceRef="outerStart" targetRef="inner"/>
      <bpmn:sequenceFlow id="outerOut" sourceRef="inner" targetRef="outerEnd"/>
    </bpmn:subProcess>
    <bpmn:endEvent id="rootEnd"/>
    <bpmn:sequenceFlow id="rootIn" sourceRef="rootStart" targetRef="outer"/>
    <bpmn:sequenceFlow id="rootOut" sourceRef="outer" targetRef="rootEnd"/>
  </bpmn:process>
</bpmn:definitions>`

async function importXml(page: Page, modelType: 'tbbpm' | 'bpmn', xml: string) {
  const [fileChooser] = await Promise.all([
    page.waitForEvent('filechooser'),
    (async () => {
      await page.locator('.header-more-btn').click()
      await page.getByText('导入 XML').click()
    })(),
  ])
  await fileChooser.setFiles({
    name: modelType === 'tbbpm' ? 'nested.bpm' : 'nested.bpmn',
    mimeType: 'application/xml',
    buffer: Buffer.from(xml),
  })
  await expect(page.locator('.ant-message-notice').filter({ hasText: 'XML 已导入' })).toBeVisible()
  await expect(page.locator('.x6-node').filter({ hasText: 'Outer scope' })).toBeVisible()
}

async function fillScriptSource(scope: Locator, source: string) {
  const editor = scope.locator('.monaco-editor')
  await expect(editor).toBeVisible({ timeout: 60_000 })
  await editor.locator('.view-lines').click()
  await scope.page().keyboard.press('ControlOrMeta+A')
  await scope.page().keyboard.insertText(source)
  await expect(editor.locator('.view-lines')).toContainText(source)
}

async function readXml(page: Page): Promise<string> {
  await page.getByTestId('designer-tab-xml').click()
  await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
    timeout: 60_000,
  })
  await expect
    .poll(() =>
      page.evaluate(() => {
        const monaco = (
          window as unknown as {
            monaco?: {
              editor: { getModels(): Array<{ getLanguageId(): string; getValue(): string }> }
            }
          }
        ).monaco
        return monaco?.editor
          .getModels()
          .find((model) => model.getLanguageId() === 'xml')
          ?.getValue()
      })
    )
    .not.toBeFalsy()
  return (await page.evaluate(() => {
    const monaco = (
      window as unknown as {
        monaco?: { editor: { getModels(): Array<{ getLanguageId(): string; getValue(): string }> } }
      }
    ).monaco
    return monaco?.editor
      .getModels()
      .find((model) => model.getLanguageId() === 'xml')
      ?.getValue()
  })) as string
}

test.describe('complex action properties', () => {
  for (const actionCase of ACTION_CASES) {
    test(`${actionCase.modelType} ${actionCase.nodeLabel}: action, policies and mappings round-trip`, async ({
      page,
    }) => {
      await gotoDesigner(page, actionCase.modelType)
      await addNode(page, actionCase.nodeLabel)
      const pane = await openSpecificProperties(page, actionCase.modelType)

      if (actionCase.implementation === 'spring') {
        await selectOption(pane, '动作类型', 'Spring Bean')
        await pane.getByRole('textbox', { name: 'Bean 名称' }).fill('orderService')
        await pane.getByRole('textbox', { name: 'Java 类名' }).fill('com.example.OrderService')
        await pane.getByRole('textbox', { name: '方法名（可选）' }).fill('submit')
      } else {
        await expect(pane.getByRole('combobox', { name: '动作类型', exact: true })).toHaveCount(0)
        await pane.getByRole('combobox', { name: '语言' }).fill('qlexpress')
        await fillScriptSource(pane, 'resultValue = amount + 1;')
      }

      await selectOption(pane, 'Durable 动作类型', 'Effect')
      await pane.getByRole('switch', { name: '启用调用策略' }).click()
      await pane.getByRole('textbox', { name: '调用总超时' }).fill('PT20S')
      await pane.getByRole('spinbutton', { name: '最大调用次数' }).fill('3')

      await pane.getByRole('switch', { name: '启用 Effect 恢复策略' }).click()
      await selectOption(pane, '恢复策略', '重试')
      await pane.getByRole('spinbutton', { name: '最大恢复尝试次数' }).fill('4')
      await pane.getByRole('textbox', { name: '恢复延迟' }).fill('PT1S')

      await pane.getByRole('button', { name: /添加/ }).click()
      const firstMapping = pane.locator('.action-mapping-card').first()
      await firstMapping.getByRole('textbox', { name: '局部变量名' }).fill('requestValue')
      await firstMapping.getByRole('textbox', { name: '来源/目标' }).fill('payload')
      await selectOption(firstMapping, '方向', 'output')
      await expect(firstMapping.getByRole('textbox', { name: '局部变量名' })).toBeDisabled()
      await expect(firstMapping.getByRole('switch', { name: '配置默认值' })).toHaveCount(0)
      await expect(firstMapping.getByRole('combobox', { name: '来源/目标' })).toBeVisible()
      await selectOption(firstMapping, '方向', 'input')
      await firstMapping.getByRole('textbox', { name: '局部变量名' }).fill('requestValue')
      await firstMapping.getByRole('textbox', { name: '来源/目标' }).fill('payload')

      await pane.getByRole('button', { name: /添加/ }).click()
      await expect(pane.locator('.action-mapping-card')).toHaveCount(2)
      await pane.getByRole('button', { name: '删除 2' }).click()
      await expect(pane.locator('.action-mapping-card')).toHaveCount(1)

      const panelBox = await pane.boundingBox()
      const cardBox = await firstMapping.boundingBox()
      expect(panelBox).toBeTruthy()
      expect(cardBox).toBeTruthy()
      expect(cardBox!.x).toBeGreaterThanOrEqual(panelBox!.x)
      expect(cardBox!.x + cardBox!.width).toBeLessThanOrEqual(panelBox!.x + panelBox!.width + 1)

      const xml = await readXml(page)
      if (actionCase.implementation === 'spring') {
        expect(xml).toContain('type="spring-bean"')
        expect(xml).toContain('bean="orderService"')
        expect(xml).toContain('class="com.example.OrderService"')
      } else {
        expect(xml).toContain('qlexpress')
        expect(xml).toContain('resultValue = amount + 1;')
      }
      expect(xml).toContain('execution="effect"')
      expect(xml).toMatch(/<(?:cf:)?invocationPolicy timeout="PT20S"/)
      expect(xml).toContain('maxAttempts="3"')
      expect(xml).toMatch(/<(?:cf:)?effectPolicy recovery="retry" maxAttempts="4"/)
      expect(xml).toContain('recoveryDelay="PT1S"')
      expect(xml).toContain('source="payload"')
      expect(xml).toContain('target="requestValue"')
    })
  }

  test('BPMN exposes process variables required by output mappings', async ({ page }) => {
    await gotoDesigner(page, 'bpmn')
    await addProcessVariable(page, 'resultValue')
    await page.getByRole('button', { name: /变量管理/ }).click()
    await expect(
      page.getByRole('dialog', { name: /流程变量/ }).getByText('resultValue')
    ).toBeVisible()
  })
})

test.describe('structured container ownership', () => {
  for (const containerCase of CONTAINER_CASES) {
    test(`${containerCase.modelType} ${containerCase.outerLabel}: assign, release and reject cycles`, async ({
      page,
    }) => {
      await gotoDesigner(page, containerCase.modelType)
      const innerTag =
        containerCase.innerLabel === '条件循环'
          ? 'while'
          : containerCase.innerLabel === '集合遍历'
            ? 'foreach'
            : 'subBpm'
      await importXml(
        page,
        containerCase.modelType,
        containerCase.modelType === 'bpmn'
          ? NESTED_BPMN
          : tbbpmContainerXml(containerCase.outerTag, innerTag)
      )

      await selectCanvasNode(page, 'Outer scope')
      let pane = await openSpecificProperties(page, containerCase.modelType)
      let children = pane.getByRole('combobox', { name: containerCase.childField })
      await children.click()
      let innerOption = page
        .locator('.ant-select-dropdown:visible .ant-select-item-option-content')
        .filter({ hasText: /^Inner scope \(/ })
      await innerOption.click()
      await expect(
        pane.locator('.ant-select-selection-item-content').filter({ hasText: /^Inner scope \(/ })
      ).toHaveCount(0)
      innerOption = page
        .locator('.ant-select-dropdown:visible .ant-select-item-option-content')
        .filter({ hasText: /^Inner scope \(/ })
      await expect(innerOption).toBeVisible()
      await innerOption.click()
      await expect(
        pane.locator('.ant-select-selection-item-content').filter({ hasText: /^Inner scope \(/ })
      ).toHaveCount(1)
      await page.keyboard.press('Escape')

      await selectCanvasNode(page, 'Inner scope')
      pane = await openSpecificProperties(page, containerCase.modelType)
      children = pane.getByRole('combobox', { name: containerCase.innerChildField })
      await children.click()
      const options = page.locator('.ant-select-dropdown:visible .ant-select-item-option-content')
      await expect(options.filter({ hasText: /^Outer scope \(/ })).toHaveCount(0)
      await expect(options.filter({ hasText: /^Inner scope \(/ })).toHaveCount(0)
      await page.keyboard.press('Escape')

      const xml = await readXml(page)
      const outerStart = xml.indexOf(`name="Outer scope"`)
      const innerStart = xml.indexOf(`name="Inner scope"`)
      const outerEnd = xml.indexOf(`</${containerCase.outerTag}>`, outerStart)
      expect(outerStart).toBeGreaterThan(-1)
      expect(innerStart).toBeGreaterThan(outerStart)
      expect(outerEnd).toBeGreaterThan(innerStart)
    })
  }
})
