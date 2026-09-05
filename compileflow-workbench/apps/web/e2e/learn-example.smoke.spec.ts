import { expect, test } from '@playwright/test'

const TIMEOUT = 15_000

test.describe('Learn example smoke', () => {
  test('renders the example source with syntax highlighting', async ({ page }) => {
    const pageErrors: Error[] = []
    const cspViolations: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error))
    page.on('console', (message) => {
      if (message.type() === 'error' && message.text().includes('Content Security Policy')) {
        cspViolations.push(message.text())
      }
    })

    await page.goto('/learn/examples/learn.tbbpm.greeting')

    await expect(page.getByRole('heading', { name: /TBBPM (Greeting|问候流程)/ })).toBeVisible({
      timeout: TIMEOUT,
    })

    await page.getByRole('tab', { name: /Code|代码/ }).click()

    const source = page.locator('pre code.language-xml').first()
    await expect(source).toContainText('<bpm code="learn.tbbpm.greeting"')
    const hasHighlightedToken = await source.evaluate((codeElement) => {
      const inheritedColor = getComputedStyle(codeElement).color
      return Array.from(codeElement.querySelectorAll('span')).some(
        (token) => getComputedStyle(token).color !== inheritedColor
      )
    })
    expect(hasHighlightedToken).toBe(true)
    await expect(page.locator('body')).toHaveCSS('margin', '0px')
    expect(pageErrors).toEqual([])
    expect(cspViolations).toEqual([])
  })
})
