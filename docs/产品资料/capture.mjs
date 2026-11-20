import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdirSync } from 'node:fs'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(resolve(here, '../../platform-web/package.json'))
const { chromium } = require('@playwright/test')
const shots = resolve(here, '界面')
mkdirSync(shots, { recursive: true })
const api = 'http://localhost:18089/api/v1'
const web = 'http://localhost:5175'

const login = await (await fetch(`${api}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ tenantCode: 'local-government', username: 'admin', password: 'admin123' }) })).json()
const token = login.data.token
const h = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
const call = async (method, path, body, extra = {}) => {
  const r = await fetch(api + path, { method, headers: { ...h, ...extra }, body: body ? JSON.stringify(body) : undefined })
  const j = await r.json()
  if (!r.ok) throw new Error(`${method} ${path} ${r.status} ${JSON.stringify(j)}`)
  return j.data
}

const title = '关于推进政务服务“高效办成一件事”的实施意见'
const listed = await call('GET', '/writing-tasks')
const stale = (listed.items ?? listed.records ?? []).filter(t => t.taskName === title)
for (const t of stale) await fetch(`${api}/writing-tasks/${t.id}`, { method: 'DELETE', headers: h })
const task = await call('POST', '/writing-tasks', {
  taskName: title, documentTypeCode: '意见', documentCategory: '法定公文',
  writingTopic: '政务服务“高效办成一件事”', writingPurpose: '部署推进政务服务“高效办成一件事”，明确总体要求、主要任务和保障措施，推动企业和群众办事由“多地跑、多次办”向“一次办、就近办”转变',
  primaryRecipient: '各区人民政府，市政府各部门', securityLevel: '内部', expectedLengthMin: 3000, expectedLengthMax: 4500,
}, { 'Idempotency-Key': `brochure-${Date.now()}` })
await call('POST', `/writing-tasks/${task.id}/materials`, {
  materialName: '演示材料：本市政务服务现状摘要（虚构数据，仅用于演示）', sourceType: '上传', authorityLevel: '工作材料', usageMode: '全文参考', citationPurpose: '背景资料', permissionStatus: '允许', locked: false,
  selectedFragment: '本市已梳理企业和个人全生命周期“一件事”清单 18 项，其中 9 项实现线上一表申请。部分事项仍存在材料重复提交、部门间数据共享不足、线下窗口分散等问题。下一步计划完善“一件事”集成办理机制，推动高频事项全程网办，建立事项清单动态调整和办理质效评价机制。',
})
let outline = await call('POST', `/writing-tasks/${task.id}/outlines`)
const rename = { '主要任务一': '推进“一件事”集成办理', '主要任务二': '优化线上线下服务渠道', '主要任务三': '强化数据共享和业务协同' }
for (const node of outline.nodes) {
  if (!rename[node.title]) continue
  outline = await call('PUT', `/writing-tasks/${task.id}/outlines/${outline.version}/nodes/${node.id}`, {
    parentNodeId: node.parentNodeId, order: node.order, title: rename[node.title], purpose: node.purpose,
    referencedMaterialIds: [], targetWordCount: node.targetWordCount, required: node.required, lockVersion: node.lockVersion,
  })
}

const browser = await chromium.launch({ channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5 })
await page.goto(`${web}/`)
await page.evaluate(t => localStorage.setItem('platform.session', t), token)
const shot = async (name) => { await page.waitForTimeout(900); await page.screenshot({ path: resolve(shots, `${name}.png`) }); console.log(name) }

await page.goto(`${web}/writing`)
await page.getByText(title).first().click()
await page.waitForTimeout(800)
await shot('01-writing')

await page.goto(`${web}/writing/${task.id}/editor`)
await page.waitForSelector('.outline-item')
await page.getByRole('button', { name: '编排提纲' }).click()
await page.waitForSelector('.oe-row')
await page.locator('.oe-row', { hasText: '优化线上线下服务渠道' }).click()
await shot('02-outline')
await page.locator('.oe-footer-actions button.primary').click()
await page.waitForSelector('.empty-generate')
await shot('03-empty')
await page.locator('.empty-generate').click()
await page.waitForSelector('[data-paragraph-id]', { timeout: 600000 })
await page.locator('.outline-item', { hasText: '推进“一件事”集成办理' }).click()
await shot('04-editor')

await page.getByRole('button', { name: '一键发起审校' }).click()
await page.waitForURL('**/review**')
await page.waitForTimeout(8000)
await shot('05-review')

for (const [path, name] of [['/rules', '06-rules'], ['/knowledge', '07-knowledge'], ['/templates', '08-templates'], ['/governance', '09-governance'], ['/open-apps', '10-open-apps'], ['/open-api', '11-open-api'], ['/operations', '12-operations']]) {
  await page.goto(web + path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(path === '/open-api' ? 3000 : 1200)
  await shot(name)
}
await browser.close()
console.log('task', task.id)
