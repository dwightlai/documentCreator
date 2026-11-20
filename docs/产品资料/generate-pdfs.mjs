import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(resolve(here, '../../platform-web/package.json'))
const { chromium } = require('@playwright/test')
const files = ['政文智枢_产品白皮书', '政文智枢_系统演示汇报介绍']
const browser = await chromium.launch({ channel: 'chrome' })
const page = await browser.newPage()
for (const name of files) {
  await page.goto(pathToFileURL(resolve(here, `${name}.html`)).href, { waitUntil: 'networkidle' })
  await page.pdf({
    path: resolve(here, `${name}.pdf`),
    format: 'A4',
    printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<div></div>',
    footerTemplate: '<div style="width:100%;font-size:9px;color:#667085;text-align:center;padding:0 16mm;"><span class="pageNumber"></span> / <span class="totalPages"></span></div>',
    margin: { top: '14mm', bottom: '16mm', left: '14mm', right: '14mm' },
  })
  console.log(name)
}
await browser.close()
