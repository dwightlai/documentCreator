import { readFileSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'

const source = JSON.parse(readFileSync(new URL('../platform-bootstrap/src/main/resources/public-data/public-source-pack.json', import.meta.url), 'utf8'))
const manifest = JSON.parse(readFileSync(new URL('../platform-bootstrap/src/main/resources/public-data/offline-manifest.json', import.meta.url), 'utf8'))
const category = (code) => source.categories.find((item) => item.code === code)?.entries ?? []
const official = (url) => /^https:\/\/(?:app\.www\.gov\.cn|www\.gov\.cn|www\.miit\.gov\.cn)\//.test(url)
const snapshotIds = ['FACT-001', 'POLICY-001', 'POLICY-002', 'POLICY-003', 'POLICY-004']
const sourceSnapshots = snapshotIds.map((id) => {
  const text = readFileSync(new URL(`../platform-bootstrap/src/main/resources/public-data/offline/${id}.txt`, import.meta.url), 'utf8')
  const sha256 = createHash('sha256').update(text).digest('hex')
  const manifestEntry = manifest.documents.find((item) => item.entryId === id)
  if (manifestEntry?.textSha256 !== sha256 || !official(manifestEntry.url))
    throw new Error(`公开页正文与提取清单不一致：${id}`)
  return { id, sourceUrl: manifestEntry.url, sha256, text }
})

const institutions = category('PUBLIC-FACTS')
  .filter((item) => /^FACT-(?:00[12456789]|0[12]\d)$/.test(item.id) && item.url.includes('498124/article.html'))
  .map((item) => ({
    id: item.id,
    name: item.checkText,
    aliasesToReview: [],
    recordedOn: item.issuedOn,
    currentStatus: '未核验',
    sourceTitle: '国务院关于机构设置的通知（国发〔2023〕5号）',
    sourceUrl: item.url,
    snapshotId: 'FACT-001',
  }))

const policies = category('PUBLIC-POLICY').map((item) => ({
  id: item.id,
  title: item.title,
  documentNumber: item.documentNumber,
  issuedOn: item.issuedOn,
  currentStatus: '未核验',
  statusBasisUrl: null,
  sourceUrl: item.url,
  snapshotId: item.id,
}))

if (institutions.length !== 27 || policies.length !== 4 || [...institutions, ...policies].some((item) => !official(item.sourceUrl)))
  throw new Error('公开来源卡片数量或官方域名异常，停止构建离线证据包')

const pack = {
  code: 'CN-OFFLINE-REVIEW-EVIDENCE',
  version: '2026.11.03-1',
  preparedOn: '2026-11-03',
  description: '公开来源的历史机构快照与政策索引。当前有效性未核验，不得作为自动判错依据。',
  sourceSnapshots,
  institutions,
  positions: [],
  politicalExpressions: [],
  policies,
}
writeFileSync(new URL('../platform-review/src/main/resources/offline-evidence/baseline.json', import.meta.url), `${JSON.stringify(pack, null, 2)}\n`)
process.stdout.write(`已整理 ${institutions.length} 条机构快照、${policies.length} 条政策索引；均标记为未核验。\n`)
