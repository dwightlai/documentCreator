/** Build reviewed offline copies of registered public official documents. Run on a connected staging host. */
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
import { JSDOM, VirtualConsole } from 'jsdom'

const root = resolve(import.meta.dirname, '../..')
const resourceRoot = resolve(root, 'platform-bootstrap/src/main/resources/public-data')
const pack = JSON.parse(await readFile(resolve(resourceRoot, 'public-source-pack.json'), 'utf8'))
const profiles = [
  ['NORM-001', '.Q25_artcont'],
  ['POLICY-001', '#articleins'],
  ['POLICY-002', '#articleins'],
  ['POLICY-003', '#con_con'],
  ['POLICY-004', '#articleins'],
  ['FACT-001', '#articleins', '国务院关于机构设置的通知'],
  ['EXAMPLE-001', '#articleins'],
  ['EXAMPLE-003', '.TRS_Editor'],
]
const registered = new Map(pack.categories.flatMap(category =>
  category.entries.map(entry => [entry.id, { ...entry, categoryCode: category.code }])))
const blockTags = new Set(['ADDRESS', 'ARTICLE', 'BLOCKQUOTE', 'DIV', 'H1', 'H2', 'H3', 'H4', 'LI', 'P', 'SECTION', 'TABLE', 'TR'])

function officialUrl(value) {
  const url = new URL(value)
  return url.protocol === 'https:' && !url.username && !url.password && (!url.port || url.port === '443') &&
    (url.hostname === 'gov.cn' || url.hostname.endsWith('.gov.cn'))
}

async function fetchHtml(url) {
  if (!officialUrl(url)) throw new Error(`非登记政府 HTTPS 页面：${url}`)
  const host = new URL(url).hostname
  for (let redirects = 0; redirects <= 3; redirects++) {
    const response = await fetch(url, { redirect: 'manual', signal: AbortSignal.timeout(20000),
      headers: { 'user-agent': 'DocumentCreatorOfflinePublicPack/1.0' } })
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const next = new URL(response.headers.get('location') ?? '', url).toString()
      if (!officialUrl(next) || new URL(next).hostname !== host) throw new Error(`不允许跨站重定向：${next}`)
      url = next
      continue
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${url}`)
    const contentType = response.headers.get('content-type') ?? ''
    if (!/^text\/html\b/i.test(contentType)) throw new Error(`页面类型不是 HTML：${contentType}`)
    const bytes = Buffer.from(await response.arrayBuffer())
    if (bytes.length > 2 * 1024 * 1024) throw new Error(`页面超过 2 MB：${url}`)
    const charset = /charset=([\w-]+)/i.exec(contentType)?.[1] ?? 'utf-8'
    return { html: new TextDecoder(charset).decode(bytes), finalUrl: url, htmlSha256: sha256(bytes) }
  }
  throw new Error(`重定向过多：${url}`)
}

function sha256(value) { return createHash('sha256').update(value).digest('hex') }

function articleText(node) {
  const parts = []
  function visit(current) {
    if (current.nodeType === 3) { parts.push(current.nodeValue ?? ''); return }
    if (current.nodeType !== 1 || ['SCRIPT', 'STYLE', 'NOSCRIPT', 'BUTTON', 'IFRAME'].includes(current.tagName)) return
    if (current.tagName === 'BR') { parts.push('\n'); return }
    if (blockTags.has(current.tagName)) parts.push('\n')
    for (const child of current.childNodes) visit(child)
    if (blockTags.has(current.tagName)) parts.push('\n')
  }
  visit(node)
  return parts.join('').replace(/\u00a0/g, ' ').replace(/[\t\r ]+/g, ' ')
    .replace(/ *\n */g, '\n').replace(/\n{3,}/g, '\n\n').trim() + '\n'
}

await mkdir(resolve(resourceRoot, 'offline'), { recursive: true })
const documents = []
for (const [entryId, selector, officialTitle] of profiles) {
  const entry = registered.get(entryId)
  if (!entry || !officialUrl(entry.url)) throw new Error(`来源未登记：${entryId}`)
  const { html, finalUrl, htmlSha256 } = await fetchHtml(entry.url)
  const page = new JSDOM(html, { virtualConsole: new VirtualConsole() }).window.document
  const article = page.querySelector(selector)
  if (!article) throw new Error(`找不到原文区域：${entryId} ${selector}`)
  const text = articleText(article)
  if (text.length < 700 || !text.includes(entry.checkText ?? entry.title))
    throw new Error(`原文内容未通过标题与长度检查：${entryId} ${text.length}`)
  const filename = `${entryId}.txt`
  await writeFile(resolve(resourceRoot, 'offline', filename), text, 'utf8')
  documents.push({ entryId, categoryCode: entry.categoryCode, title: officialTitle ?? entry.title,
    sourceCardTitle: entry.title,
    publisher: entry.publisher, documentNumber: entry.documentNumber, issuedOn: entry.issuedOn, url: entry.url,
    finalUrl, file: `offline/${filename}`, selector, characters: text.length,
    htmlSha256, textSha256: sha256(text), completeness: entryId === 'EXAMPLE-001' ? '公开页注明原文有删减' : '按公开页正文区域提取' })
  console.log(`${entryId} ${text.length} 字符 ${filename}`)
}
await writeFile(resolve(resourceRoot, 'offline-manifest.json'), JSON.stringify({
  version: pack.version, extractedOn: new Date().toISOString().slice(0, 10), documents,
}, null, 2) + '\n', 'utf8')
