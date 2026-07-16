import { mkdir, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { performance } from 'node:perf_hooks'

const baseUrl = (process.env.PERF_BASE_URL || 'http://127.0.0.1:18082').replace(/\/$/, '')
const apiRoot = `${baseUrl}/api/v1`
const tenantId = process.env.PERF_TENANT_ID || 'performance-government'
const userId = process.env.PERF_USER_ID || 'performance-runner'
const writingUsers = Number(process.env.PERF_WRITING_USERS || 50)
const reviewSamples = Number(process.env.PERF_REVIEW_SAMPLES || 10)
const searchQps = Number(process.env.PERF_SEARCH_QPS || 100)
const searchWindows = Number(process.env.PERF_SEARCH_WINDOWS || 5)
const outputFile = path.resolve(process.env.PERF_OUTPUT || '../target/acceptance/performance-baseline.json')

if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(baseUrl)
    && process.env.ALLOW_MUTATING_LOAD_TEST !== 'true') {
  throw new Error('压测会创建业务数据。非本机地址必须显式设置 ALLOW_MUTATING_LOAD_TEST=true')
}

function headers(contentType = 'application/json') {
  return {
    'Content-Type': contentType,
    'X-Tenant-Id': tenantId,
    'X-User-Id': userId,
    'X-Request-Id': crypto.randomUUID().replaceAll('-', ''),
  }
}

async function api(relativePath, options = {}) {
  const started = performance.now()
  const response = await fetch(`${apiRoot}${relativePath}`, {
    ...options,
    headers: { ...headers(), ...options.headers },
  })
  const responseBody = await response.text()
  const latencyMs = performance.now() - started
  let envelope
  try { envelope = responseBody ? JSON.parse(responseBody) : undefined }
  catch { throw new Error(`${options.method || 'GET'} ${relativePath} 返回非 JSON（HTTP ${response.status}）：${responseBody.slice(0, 300)}`) }
  if (!response.ok || (envelope && envelope.code >= 400)) {
    throw new Error(`${options.method || 'GET'} ${relativePath} 失败（HTTP ${response.status}）：${envelope?.message || responseBody.slice(0, 300)}`)
  }
  return { data: envelope?.data, latencyMs, status: response.status }
}

const body = value => JSON.stringify(value)

function percentile(values, quantile) {
  if (!values.length) return null
  const sorted = [...values].sort((left, right) => left - right)
  return Number(sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * quantile) - 1)].toFixed(2))
}

function latencySummary(values) {
  return {
    samples: values.length,
    p50Ms: percentile(values, 0.50),
    p95Ms: percentile(values, 0.95),
    p99Ms: percentile(values, 0.99),
    maxMs: values.length ? Number(Math.max(...values).toFixed(2)) : null,
  }
}

function taskPayload(index) {
  return {
    taskName: `性能验收并发任务 ${Date.now()}-${index}`,
    documentTypeCode: '请示',
    documentCategory: '法定公文',
    writingTopic: '政务智能公文平台性能验收',
    writingPurpose: '验证并发创建、编辑与审校处理能力',
    direction: '上行文',
    targetAudience: '上级机关',
    primaryRecipient: '主管部门',
    writingTone: '正式',
    expectedLengthMin: 3000,
    expectedLengthMax: 6000,
    allowExternalKnowledge: false,
    securityLevel: '内部',
  }
}

async function writingConcurrencyScenario() {
  const started = performance.now()
  const results = await Promise.allSettled(Array.from({ length: writingUsers }, (_, index) =>
    api('/writing-tasks', { method: 'POST', body: body(taskPayload(index + 1)) })))
  const latency = results.filter(item => item.status === 'fulfilled').map(item => item.value.latencyMs)
  const errors = results.filter(item => item.status === 'rejected').map(item => String(item.reason?.message || item.reason))
  return {
    targetConcurrentUsers: writingUsers,
    succeeded: latency.length,
    failed: errors.length,
    elapsedMs: Number((performance.now() - started).toFixed(2)),
    latency: latencySummary(latency),
    errors: errors.slice(0, 10),
    passed: errors.length === 0 && latency.length === writingUsers,
  }
}

async function generateSectionAndMeasureFirstText(taskId, outlineNodeId) {
  const started = performance.now()
  const response = await fetch(`${apiRoot}/writing-tasks/${taskId}/generate/section/stream`, {
    method: 'POST',
    headers: headers(),
    body: body({ outlineNodeId, instruction: '生成用于平台性能验收的规范请示正文。' }),
  })
  if (!response.ok || !response.body) throw new Error(`流式生成失败（HTTP ${response.status}）：${await response.text()}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let streamText = ''
  let firstTextMs = null
  for (;;) {
    const { done, value } = await reader.read()
    if (value) {
      streamText += decoder.decode(value, { stream: !done })
      if (firstTextMs === null && /event:\s*delta/.test(streamText)) firstTextMs = performance.now() - started
    }
    if (done) break
  }
  if (firstTextMs === null) throw new Error(`流式响应未出现 delta 事件：${streamText.slice(0, 500)}`)
  return Number(firstTextMs.toFixed(2))
}

async function prepareFiveThousandCharacterDocument() {
  const task = (await api('/writing-tasks', { method: 'POST', body: body(taskPayload('review')) })).data
  const outline = (await api(`/writing-tasks/${task.id}/outlines`, { method: 'POST' })).data
  await api(`/writing-tasks/${task.id}/outlines/${outline.version}/confirm`, { method: 'POST' })
  const firstTextMs = await generateSectionAndMeasureFirstText(task.id, outline.nodes[0].id)
  const document = (await api(`/writing-tasks/${task.id}/document`)).data
  const paragraph = document.paragraphs[0]
  const seed = '为推进政务材料可信溯源平台建设，现将有关事项请示如下。建设过程坚持依法合规、安全可控、权责清晰、全程留痕。'
  const text = seed.repeat(Math.ceil(5000 / seed.length)).slice(0, 5000)
  await api(`/writing-tasks/${task.id}/document/paragraphs/${paragraph.id}`, {
    method: 'PUT',
    body: body({ plainText: text, lockVersion: paragraph.lockVersion, changeDescription: '5000 字基础审校性能验收样本' }),
  })
  return { taskId: task.id, firstTextMs, characterCount: text.length }
}

async function reviewScenario(prepared) {
  const latency = []
  let reviewTaskId
  for (let index = 0; index < reviewSamples; index += 1) {
    const result = index === 0
      ? await api('/review-tasks', { method: 'POST', body: body({ writingTaskId: prepared.taskId }) })
      : await api(`/review-tasks/${reviewTaskId}/re-review`, { method: 'POST' })
    reviewTaskId ||= result.data.id
    latency.push(result.latencyMs)
  }
  const summary = latencySummary(latency)
  return {
    documentCharacters: prepared.characterCount,
    reviewSamples,
    latency: summary,
    thresholdP99Ms: 10_000,
    passed: summary.p99Ms !== null && summary.p99Ms <= 10_000,
  }
}

async function knowledgeSearchScenario() {
  const latency = []
  const windows = []
  const errors = []
  for (let window = 0; window < searchWindows; window += 1) {
    const started = performance.now()
    const results = await Promise.allSettled(Array.from({ length: searchQps }, () =>
      api('/knowledge/search?q=政务平台&limit=10')))
    const elapsedMs = performance.now() - started
    results.forEach((result) => {
      if (result.status === 'fulfilled') latency.push(result.value.latencyMs)
      else errors.push(String(result.reason?.message || result.reason))
    })
    windows.push({
      window: window + 1,
      requests: searchQps,
      elapsedMs: Number(elapsedMs.toFixed(2)),
      serviceQps: Number((searchQps * 1000 / elapsedMs).toFixed(2)),
    })
    if (elapsedMs < 1000) await new Promise(resolve => setTimeout(resolve, 1000 - elapsedMs))
  }
  const minimumServiceQps = Number(Math.min(...windows.map(item => item.serviceQps)).toFixed(2))
  return {
    targetQps: searchQps,
    sustainedWindows: searchWindows,
    totalRequests: searchQps * searchWindows,
    succeeded: latency.length,
    failed: errors.length,
    minimumServiceQps,
    windows,
    latency: latencySummary(latency),
    errors: errors.slice(0, 10),
    passed: errors.length === 0 && minimumServiceQps >= searchQps,
  }
}

const report = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  resultType: process.env.STRICT_ACCEPTANCE === 'true' ? 'ACCEPTANCE' : 'LOCAL_BASELINE',
  environment: {
    baseUrl,
    platform: `${os.platform()} ${os.release()} ${os.arch()}`,
    cpuModel: os.cpus()[0]?.model || 'unknown',
    cpuCount: os.cpus().length,
    totalMemoryBytes: os.totalmem(),
    nodeVersion: process.version,
    applicationSpec: process.env.PERF_APPLICATION_SPEC || 'local-unspecified',
    databaseSpec: process.env.PERF_DATABASE_SPEC || 'local-H2-unspecified',
    networkCondition: process.env.PERF_NETWORK_CONDITION || 'loopback',
    modelVersion: process.env.PERF_MODEL_VERSION || 'local-fallback',
    datasetVersion: process.env.PERF_DATASET_VERSION || 'generated-v1',
    codeBuild: process.env.BUILD_SHA || 'working-tree',
    warmup: '10 knowledge search requests',
  },
  thresholds: {
    writingConcurrentUsers: writingUsers,
    generationFirstTextMs: 5_000,
    basicReview5000CharactersP99Ms: 10_000,
    knowledgeSearchQps: searchQps,
  },
  scenarios: {},
}

for (let index = 0; index < 10; index += 1) await api('/knowledge/search?q=政务平台&limit=10')
report.scenarios.writingConcurrency = await writingConcurrencyScenario()
const prepared = await prepareFiveThousandCharacterDocument()
report.scenarios.generationFirstText = {
  samples: 1,
  firstTextMs: prepared.firstTextMs,
  thresholdMs: 5_000,
  passed: prepared.firstTextMs <= 5_000,
}
report.scenarios.basicReview = await reviewScenario(prepared)
report.scenarios.knowledgeSearch = await knowledgeSearchScenario()
report.passed = Object.values(report.scenarios).every(scenario => scenario.passed)

await mkdir(path.dirname(outputFile), { recursive: true })
await writeFile(outputFile, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
console.log(JSON.stringify({ outputFile, passed: report.passed, scenarios: report.scenarios }, null, 2))
if (!report.passed) process.exitCode = 1
