import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'

const root = new URL('../platform-bootstrap/src/main/resources/standards/', import.meta.url)
const read = (file) => JSON.parse(readFileSync(new URL(file, root), 'utf8'))
const fail = (message) => { throw new Error(message) }
const manifest = read('review-data-manifest.json')
const pack = read('official-review-pack.json')
if (pack.version !== manifest.packVersion) fail('规则包版本与来源清单不一致')

const codes = new Set()
const dictionaryCodes = new Set(pack.dictionaries.map((item) => item.code))
for (const rule of pack.rules) {
  if (!rule.code || codes.has(rule.code)) fail(`规则代码缺失或重复：${rule.code}`)
  codes.add(rule.code)
  if (!['正则', '词项', '词库'].includes(rule.method)) fail(`规则不能自动执行：${rule.code}`)
  if (!rule.expression || !pack.sources[rule.source] || !rule.clause) fail(`规则缺少表达式或依据：${rule.code}`)
  if (rule.method === '词库' && !dictionaryCodes.has(rule.expression)) fail(`规则引用了不存在的词库：${rule.code}`)
}

const examples = JSON.parse(readFileSync(new URL('../platform-bootstrap/src/test/resources/standards/review-rule-cases.json', import.meta.url), 'utf8'))
const testedCodes = new Set()
for (const example of examples) {
  if (!codes.has(example.rule) || testedCodes.has(example.rule) || !example.positive || !example.negative || example.positive === example.negative)
    fail(`规则正反例无效或重复：${example.rule}`)
  testedCodes.add(example.rule)
}
for (const rule of pack.rules) {
  if (rule.method !== '词库' && !testedCodes.has(rule.code)) fail(`规则缺少正反例：${rule.code}`)
}
for (const dictionary of pack.dictionaries) {
  if (!pack.rules.some((rule) => rule.method === '词库' && rule.expression === dictionary.code))
    fail(`词库没有被任何规则调用：${dictionary.code}`)
}

for (const resource of manifest.resources) {
  const bytes = readFileSync(new URL(resource.path, root))
  const digest = createHash('sha256').update(bytes).digest('hex')
  if (digest !== resource.sha256) fail(`资源指纹变化，须重新核验来源：${resource.path}`)
  const data = JSON.parse(bytes.toString('utf8'))
  if (data.groups.length !== resource.groups) fail(`分组数量不符：${resource.path}`)
  const variants = new Map()
  for (const group of data.groups) {
    if (!group.preferred || !Array.isArray(group.variants) || group.variants.length === 0)
      fail(`空白推荐词形：${resource.path}`)
    for (const variant of group.variants) {
      if (!variant || variant === group.preferred || variants.has(variant))
        fail(`重复或无效异形词：${variant}`)
      variants.set(variant, group.preferred)
    }
  }
  if (variants.size !== resource.variants) fail(`词形数量不符：${resource.path}`)
  if (resource.path === 'gf1001-2001-variants.json' && !resource.publicSource.startsWith('https://www.moe.gov.cn/'))
    fail('异形词来源不是登记的教育部公开地址')
  if (!resource.publicSource || resource.publicSource.length < 12) fail(`来源说明缺失：${resource.path}`)
}
const preferred = new Set()
const variants = new Map()
for (const resource of manifest.resources) {
  const data = read(resource.path)
  for (const group of data.groups) {
    preferred.add(group.preferred)
    for (const variant of group.variants) {
      if (variants.has(variant)) fail(`词形跨库重复：${variant}`)
      variants.set(variant, group.preferred)
    }
  }
}
for (const [variant, standard] of variants) {
  for (const form of preferred) {
    if (form.includes(variant) && form.length > variant.length) fail(`词形「${variant}」包含于推荐写法「${form}」`)
  }
}

const termCount = manifest.resources.reduce((sum, item) => sum + item.variants, 0)
process.stdout.write(`规则 ${pack.rules.length} 条；词库 ${pack.dictionaries.length} 部；词条 ${termCount} 条。结构及指纹校验通过。\n`)
