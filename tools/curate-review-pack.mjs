import { readFileSync, writeFileSync } from 'node:fs'

const path = new URL('../platform-bootstrap/src/main/resources/standards/official-review-pack.json', import.meta.url)
const pack = JSON.parse(readFileSync(path, 'utf8'))

// Keep only data that the review engine can execute. Reference-only material belongs in the
// knowledge base, not in the rule and dictionary counts shown to reviewers.
pack.version = '2026.11.03-5'
pack.title = '党政机关公文可执行审校规则包（依据公开规范整理）'
pack.scope = '发文字号、成文日期和标点的明确格式检查；GF 1001—2001 异形词推荐词形提示'
pack.sources = Object.fromEntries(Object.entries(pack.sources).filter(([key]) =>
  ['format', 'punctuation', 'variant-words'].includes(key)))
pack.dictionaries = pack.dictionaries.filter((item) => item.code === 'NAT-VARIANT-WORDS-GF1001-2001')
pack.rules = pack.rules.filter((item) => item.method !== '人工核验')

const additions = [
  {
    code: 'STD-DATE-MONTH-LEADING-ZERO', name: '成文日期月份不编虚位', category: '格式与版式',
    risk: '一般', method: '正则', expression: '([12]\\d{3}年)0([1-9])月=>$1$2月',
    source: 'format', clause: '7.3.5.4', types: [],
  },
  {
    code: 'STD-DATE-DAY-LEADING-ZERO', name: '成文日期日期不编虚位', category: '格式与版式',
    risk: '一般', method: '正则', expression: '([12]\\d{3}年(?:0?[1-9]|1[0-2])月)0([1-9])日=>$1$2日',
    source: 'format', clause: '7.3.5.4', types: [],
  },
  {
    code: 'STD-PUNCT-REPEATED-COMMA', name: '连续重复逗号提示', category: '语言文字',
    risk: '提示', method: '正则', expression: '，{2,}=>，',
    source: 'punctuation', clause: '第4章：逗号用法', types: [],
  },
  {
    code: 'STD-PUNCT-REPEATED-PERIOD', name: '连续重复句号提示', category: '语言文字',
    risk: '提示', method: '正则', expression: '。{2,}=>。',
    source: 'punctuation', clause: '第4章：句号用法', types: [],
  },
]
const existing = new Set(pack.rules.map((item) => item.code))
for (const rule of additions) if (!existing.has(rule.code)) pack.rules.push(rule)

writeFileSync(path, JSON.stringify(pack, null, 2) + '\n', 'utf8')
