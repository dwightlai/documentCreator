import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(resolve(here, '../../platform-web/package.json'))
const { chromium } = require('@playwright/test')
const browser = await chromium.launch({ channel: 'chrome' })
const page = await browser.newPage()
const open = async (name) => page.goto(pathToFileURL(resolve(here, `${name}.html`)).href, { waitUntil: 'networkidle' })

const a4 = { format: 'A4', margin: { top: '14mm', bottom: '16mm', left: '14mm', right: '14mm' } }
const pages = (buf) => buf.toString('latin1').match(/\/Type\s*\/Page[^s]/g).length
await open('政文智枢_产品白皮书')
const total = await page.locator('section').count()
const start = []
let next = 3
for (let i = 0; i < total; i++) {
  const cont = await page.locator('section').nth(i).evaluate(el => el.classList.contains('cont'))
  if (cont) { start.push(start[i - 1]); continue }
  let end = i + 1
  while (end < total && await page.locator('section').nth(end).evaluate(el => el.classList.contains('cont'))) end++
  await page.evaluate(([a, b]) => {
    document.querySelectorAll('body > .page').forEach(el => { el.style.display = 'none' })
    document.querySelectorAll('section').forEach((el, k) => { el.style.display = k >= a && k < b ? '' : 'none' })
  }, [i, end])
  start.push(next)
  next += pages(await page.pdf(a4))
}
console.log(start.join(','), next - 1)
await open('政文智枢_产品白皮书')
await page.evaluate(start => document.querySelectorAll('[data-toc]').forEach(el => { el.textContent = start[+el.dataset.toc] }), start)
await page.pdf({
  path: resolve(here, '政文智枢_产品白皮书.pdf'),
  format: 'A4',
  printBackground: true,
  displayHeaderFooter: true,
  headerTemplate: '<div></div>',
  footerTemplate: '<div style="width:100%;font-size:9px;color:#667085;text-align:center;padding:0 16mm;"><span class="pageNumber"></span> / <span class="totalPages"></span></div>',
  margin: { top: '14mm', bottom: '16mm', left: '14mm', right: '14mm' },
})

await open('政文智枢_系统演示汇报介绍')
await page.pdf({ path: resolve(here, '政文智枢_系统演示汇报介绍.pdf'), printBackground: true, preferCSSPageSize: true })

await browser.close()
