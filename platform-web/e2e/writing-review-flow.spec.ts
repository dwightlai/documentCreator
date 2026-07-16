import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze()
  const violations = results.violations.filter(item => item.impact === 'critical' || item.impact === 'serious')
  expect(violations, violations.map(item => `${item.id}: ${item.help}`).join('\n')).toEqual([])
}

test('creates a task and completes outline, generation and review handoff', async ({ page }) => {
  const taskName = `E2E 专项资金请示 ${Date.now()}`
  const pageErrors: string[] = []
  page.on('pageerror', error => pageErrors.push(error.message))

  await page.goto('/writing')
  await expect(page.getByRole('heading', { name: '智能写作工作台' })).toBeVisible()
  await page.getByRole('button', { name: '新建写作任务' }).click()
  await page.getByPlaceholder('例如：关于申请专项建设资金的请示').fill(taskName)
  await page.getByPlaceholder('说明公文要解决的核心事项').fill('申请建设政务材料可信溯源平台')
  await page.getByPlaceholder('说明行文对象、预期结果和关键要求').fill('向上级机关说明建设必要性并申请批准立项')
  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/api/v1/writing-tasks'),
  )
  await page.getByRole('button', { name: '创建任务', exact: true }).click()
  const createResponse = await createResponsePromise
  const createResponseBody = await createResponse.text()
  expect(createResponse.status(), `创建任务响应：${createResponseBody}`).toBe(200)

  await expect(page.getByRole('heading', { name: taskName, exact: true })).toBeVisible()
  await page.getByRole('button', { name: '生成写作提纲', exact: true }).click()
  await expect(page.getByText('提纲已生成，可进入提纲编辑', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: '进入材料 / 提纲 / 正文工作区' }).click()

  await expect(page).toHaveURL(/\/writing\/[^/]+\/editor$/)
  await expect(page.getByRole('heading', { name: taskName })).toBeVisible()
  await page.getByRole('button', { name: '确认提纲', exact: true }).click()
  await expect(page.getByText('提纲已人工确认，正文生成门禁已开启', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '生成本节', exact: true }).click()
  await expect(page.getByText('章节已生成，请人工复核', { exact: true })).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText(/第 1 段 · \d+ 字/)).toBeVisible()

  await page.getByRole('button', { name: '一键发起审校', exact: true }).click()
  await expect(page).toHaveURL(/\/review\?taskId=/)
  await expect(page.getByRole('heading', { name: '审校问题清单' })).toBeVisible()
  expect(pageErrors).toEqual([])
})

test('governance workspaces render without serious automated accessibility violations', async ({ page }) => {
  const routes = [
    ['/writing', '智能写作工作台'],
    ['/review', '政务智能审校'],
    ['/rules', '规则与词库'],
    ['/knowledge', '知识与依据'],
    ['/operations', '任务与运行中心'],
    ['/templates', '输出模板中心'],
    ['/governance', '模型与配置治理'],
  ] as const

  for (const [route, heading] of routes) {
    await page.goto(route)
    await expect(page.getByRole('heading', { name: heading })).toBeVisible()
    await expectNoSeriousAccessibilityViolations(page)
  }
})
