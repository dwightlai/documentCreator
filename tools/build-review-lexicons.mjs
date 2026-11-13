import { createHash } from 'node:crypto'
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'

const root = new URL('../platform-bootstrap/src/main/resources/standards/', import.meta.url)
const gf = JSON.parse(readFileSync(new URL('gf1001-2001-variants.json', root), 'utf8'))
const taken = new Set()
const preferred = new Set()
const ownerOf = new Map()
for (const group of gf.groups) {
  preferred.add(group.preferred)
  for (const variant of group.variants) {
    taken.add(variant)
    ownerOf.set(variant, group.preferred)
  }
}

const pairFiles = readdirSync(new URL('./', import.meta.url)).filter((name) => /^lexicon-pairs.*\.tsv$/.test(name)).sort()
const lines = pairFiles.flatMap((name) => readFileSync(new URL(name, import.meta.url), 'utf8').split(/\r?\n/))
const buckets = new Map()
const rejected = []
for (const line of lines) {
  if (!line || line.startsWith('#')) continue
  const [dict, variant, standard, note] = line.split('\t')
  if (!dict || !variant || !standard) throw new Error('词条列不足: ' + line)
  if (variant === standard) continue
  if (note && /不强制|不作为错词|不收录|不自动|不互换|易误伤/.test(note)) continue
  if (variant.length < 2 || standard.includes(variant)) {
    rejected.push(variant)
    continue
  }
  if (taken.has(variant) || preferred.has(variant)) {
    rejected.push(variant)
    continue
  }
  let blocked = false
  for (const [known, knownStandard] of ownerOf) {
    if ((standard.includes(known) && knownStandard !== standard) || (knownStandard.includes(variant) && knownStandard !== standard)) {
      blocked = true
      break
    }
  }
  if (blocked) {
    rejected.push(variant)
    continue
  }
  taken.add(variant)
  preferred.add(standard)
  ownerOf.set(variant, standard)
  if (!buckets.has(dict)) buckets.set(dict, [])
  buckets.get(dict).push({ preferred: standard, variants: [variant], ...(note ? { note } : {}) })
}

const specs = {
  word: ['lexicon-confusable.json', '公文高频易错词', '现代汉语通行推荐写法中可判定的错写，不含权利/权力等两可词'],
  idiom: ['lexicon-idiom.json', '成语和固定词组误写', '成语、惯用语的通行写法；不因语义易误用而标记正确词形'],
  phrase: ['lexicon-official-usage.json', '公文冗余和不规范搭配', '公文中可判定的冗余搭配，提示后由拟稿人确认'],
  agency: ['lexicon-agency.json', '机关名称易错写法', '现行国务院机构的确定错写；历史旧称不在本库自动替换'],
  division: ['lexicon-division.json', '行政区划易错写法', '现行行政区划全称的确定错写；叙述历史沿革时需人工确认'],
  unit: ['lexicon-unit.json', '非法定计量单位名称', '与法定单位同义、可以替换名称的旧称'],
  name: ['lexicon-proper.json', '重要专名错写', '专名用字错误，只纠正确定的错字']
}
const written = []
for (const [dict, groups] of buckets) {
  const spec = specs[dict]
  if (!spec) throw new Error('未知词库分类: ' + dict)
  const body = {
    title: spec[1],
    note: spec[2],
    groups: groups.sort((a, b) => a.variants[0].localeCompare(b.variants[0], 'zh'))
  }
  const file = spec[0]
  const json = JSON.stringify(body, null, 2) + '\n'
  writeFileSync(new URL(file, root), json)
  written.push({ file, groups: groups.length, variants: groups.length, sha256: createHash('sha256').update(json).digest('hex') })
}
const pack = JSON.parse(readFileSync(new URL('official-review-pack.json', root), 'utf8'))
const manifest = JSON.parse(readFileSync(new URL('review-data-manifest.json', root), 'utf8'))
for (const item of written) {
  const dictionary = pack.dictionaries.find((entry) => entry.dataResource === 'standards/' + item.file)
  if (dictionary) {
    dictionary.groups = item.groups
    dictionary.variants = item.variants
  }
  const resource = manifest.resources.find((entry) => entry.path === item.file)
  if (resource) {
    resource.groups = item.groups
    resource.variants = item.variants
    resource.sha256 = item.sha256
  }
}
writeFileSync(new URL('official-review-pack.json', root), JSON.stringify(pack, null, 2) + '\n')
writeFileSync(new URL('review-data-manifest.json', root), JSON.stringify(manifest, null, 2) + '\n')
if (rejected.length) console.log(rejected.join('\n'))
console.log(JSON.stringify(written, null, 2))
console.log('rejected', rejected.length)
