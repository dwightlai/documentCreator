import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'node:fs'

const root = new URL('../platform-bootstrap/src/main/resources/standards/', import.meta.url)
const read = (file) => JSON.parse(readFileSync(new URL(file, root), 'utf8'))
const pack = read('official-review-pack.json')
const stale = new URL('lexicon-official-usage.json', root)
if (existsSync(stale)) unlinkSync(stale)

pack.version = '2026.11.04-1'
pack.scope = '发文字号、成文日期、份号和标点的明确格式检查；GF 1001—2001 正文异形词；公文高频错写、成语误写、现行机关和行政区划错写、非法定单位旧称、专名错字。两可词、历史旧称和已废止法律只提示、不自动替换。'
pack.sources.numerals = 'GB/T 15835—2011《出版物上数字用法》（现行），https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F5DAC3377DA99C8D78AE66735B6359C7'
pack.sources.ordinance = '《党政机关公文处理工作条例》（中办发〔2012〕14号），https://rsj.panjin.gov.cn/2020_07/23_16/content-280059.html'
pack.sources['civil-code'] = '《中华人民共和国民法典》第1260条（2021年1月1日起施行，同时废止婚姻法、继承法、民法通则、收养法、担保法、合同法、物权法、侵权责任法、民法总则）。国家法律法规数据库：https://flk.npc.gov.cn/'
pack.sources.agencies = '《国务院关于机构设置的通知》（国发〔2023〕5号），https://app.www.gov.cn/govdata/gov/202303/20/498124/article.html'
pack.sources.divisions = '现行行政区划全称。民政部全国行政区划信息：https://www.mca.gov.cn/'
pack.sources.units = '《中华人民共和国计量法》及国务院《关于在我国统一实行法定计量单位的命令》（1984年）。只收录与法定单位同义的旧称，不收录公斤、公里、亩。'
pack.sources.confusable = '公文高频错写中字形可以判定的推荐写法。不是国家标准词表，也不是商业词库复制。权利/权力、截止/截至、制定/制订、反应/反映等两可词不收录，不自动替换。'
pack.sources.idiom = '成语和固定词组的通行写法。只收录字形或语序可以判定的误写；语义易误用但字形正确的条目不收录。'
pack.sources.proper = '领导人姓名和固定表述中可以判定的错字。只提示字形，不评价政策内容。'

const lexicons = [
  ['standards/lexicon-confusable.json', 'NAT-LEXICON-CONFUSABLE', '公文高频易错词', '易错词', 'confusable', '通行推荐写法中可判定的错写', '命中高频错写。请核对语境后再改。', '一般'],
  ['standards/lexicon-idiom.json', 'NAT-LEXICON-IDIOM', '成语和固定词组误写', '成语误写', 'idiom', '通行成语写法', '命中成语或固定词组误写。', '一般'],
  ['standards/lexicon-agency.json', 'NAT-LEXICON-AGENCY', '机关名称易错写法', '机关名称', 'agencies', '国发〔2023〕5号现行机构全称', '现行机构名称错写。历史旧称不在本库自动替换。', '一般'],
  ['standards/lexicon-division.json', 'NAT-LEXICON-DIVISION', '行政区划易错写法', '行政区划', 'divisions', '现行行政区划全称', '现行行政区划全称错写。叙述历史沿革时需人工确认。', '一般'],
  ['standards/lexicon-unit.json', 'NAT-LEXICON-UNIT', '非法定计量单位旧称', '计量单位', 'units', '与法定单位同义的旧称', '与法定单位同义的旧称，可改为法定名称。', '一般'],
  ['standards/lexicon-proper.json', 'NAT-LEXICON-PROPER', '专名和固定表述错字', '专名误写', 'proper', '可判定的错字', '专名或固定表述错字。只改字形。', '一般']
]
const gf = pack.dictionaries.find((item) => item.code === 'NAT-VARIANT-WORDS-GF1001-2001')
gf.termNote = '试行规范推荐词形；仅在同义语境下提示核对，不应直接自动替换。'
gf.risk = '提示'
const resources = []
const digest = (file) => createHash('sha256').update(readFileSync(new URL(file, root))).digest('hex')
resources.push({
  path: 'gf1001-2001-variants.json',
  standard: 'GF 1001—2001《第一批异形词整理表》',
  publisher: '教育部、国家语言文字工作委员会',
  publicSource: 'https://www.moe.gov.cn/ewebeditor/uploadfile/2015/01/12/20150112165927212.pdf',
  groups: 338,
  variants: 362,
  sha256: digest('gf1001-2001-variants.json'),
  verification: '结构、重复词形和文件指纹自动校验；逐条对照标准原文仍需复核'
})
for (const [file, code, name, type, source, clause, termNote, risk] of lexicons) {
  const data = read(file.slice('standards/'.length))
  const variants = data.groups.reduce((sum, group) => sum + group.variants.length, 0)
  pack.dictionaries.push({ code, name, type, source, clause, dataResource: file, termNote, risk, groups: data.groups.length, variants })
  resources.push({
    path: file.slice('standards/'.length),
    standard: name,
    publisher: '项目依据公开资料整理，不是标准全文转录',
    publicSource: pack.sources[source],
    groups: data.groups.length,
    variants,
    sha256: digest(file.slice('standards/'.length)),
    verification: '结构、重复词形、跨库包含关系和文件指纹自动校验；逐条人工复核仍未完成，不能宣称准确率'
  })
}

const rules = [
  ['STD-PUNCT-ASCII-SEMICOLON', '汉字之间使用半角分号', '语言文字', '提示', 'punctuation', '4.6 分号', '(?<=[\\p{IsHan}]);(?=[\\p{IsHan}])=>；', '办理;反馈', '办理；反馈', '；'],
  ['STD-PUNCT-ASCII-QUESTION', '汉字之间使用半角问号', '语言文字', '提示', 'punctuation', '4.8 问号', '(?<=[\\p{IsHan}])\\?(?=[\\p{IsHan}])=>？', '是否办结?请回复', '是否办结？请回复', '？'],
  ['STD-PUNCT-ASCII-EXCLAMATION', '汉字之间使用半角叹号', '语言文字', '提示', 'punctuation', '4.7 叹号', '(?<=[\\p{IsHan}])!(?=[\\p{IsHan}])=>！', '立即办理!不得延误', '立即办理！不得延误', '！'],
  ['STD-PUNCT-ASCII-DOT-BETWEEN-HAN', '汉字之间使用半角句点', '语言文字', '提示', 'punctuation', '4.2 句号；间隔号另见', '(?<=[\\p{IsHan}])\\.(?=[\\p{IsHan}])', '李.明', '李·明'],
  ['STD-PUNCT-ASCII-PARENS', '汉字之间使用半角括号', '语言文字', '提示', 'punctuation', '4.9 括号', '(?<=[\\p{IsHan}])\\(([^()\\n]{1,40})\\)(?=[\\p{IsHan}])=>（$1）', '办法(试行)稿', '办法（试行）稿', '（试行）'],
  ['STD-PUNCT-ASCII-QUOTES', '汉字之间使用半角引号', '语言文字', '提示', 'punctuation', '4.11 引号', '(?<=[\\p{IsHan}])\\"([^\\"\\n]{1,40})\\"(?=[\\p{IsHan}])=>“$1”', '称为"高效办成一件事"工作', '称为“高效办成一件事”工作', '“高效办成一件事”'],
  ['STD-PUNCT-REPEATED-EXCLAMATION', '叹号重复', '语言文字', '提示', 'punctuation', '4.7 叹号', '！{2,}=>！', '请立即办理！！', '请立即办理！', '！'],
  ['STD-PUNCT-REPEATED-QUESTION', '问号重复', '语言文字', '提示', 'punctuation', '4.8 问号', '？{2,}=>？', '是否办结？？', '是否办结？', '？'],
  ['STD-PUNCT-REPEATED-DUNHAO', '顿号重复', '语言文字', '提示', 'punctuation', '4.5 顿号', '、{2,}=>、', '事项、、请核对', '事项、请核对', '、'],
  ['STD-PUNCT-SPACE-BEFORE', '中文标点前有空格', '语言文字', '提示', 'punctuation', '标点与文字之间不留空格', '([ \\t]+)([，。；：、！？])=>$2', '请办理 ，并反馈', '请办理，并反馈', '，'],
  ['STD-PUNCT-HAN-HYPHEN', '汉字之间用连字符代替破折号', '语言文字', '提示', 'punctuation', '4.10 破折号', '(?<=[\\p{IsHan}])--(?=[\\p{IsHan}])=>——', '事项--说明', '事项——说明', '——'],
  ['STD-PUNCT-YEAR-RANGE', '年份起止用连字符', '语言文字', '提示', 'numerals', '连接号', '((?:19|20)\\d{2})-{1,2}((?:19|20)\\d{2})(?=年)=>$1—$2', '2020-2026年', '2020—2026年', '2020—2026'],
  ['STD-PUNCT-BOOK-TITLE-ANGLE', '书名号误用单角括号', '语言文字', '提示', 'punctuation', '4.15 书名号', '〈([^〈〉\\n]{1,40})〉=>《$1》', '〈党政机关公文处理工作条例〉', '《党政机关公文处理工作条例》', '《党政机关公文处理工作条例》'],
  ['STD-NUM-YEAR-THOUSANDS', '年份使用千分位', '格式与版式', '一般', 'numerals', '年份用阿拉伯数字，不使用千分位', '(?<!\\d)([12]),(\\d{3})(?=年)=>$1$2', '2,026年', '2026年', '2026'],
  ['STD-DATE-DOTTED', '日期使用点号分隔', '格式与版式', '一般', 'format', '7.3.5.4', '(?<!\\d)((?:19|20)\\d{2})\\.(0?)([1-9]|1[0-2])\\.(0?)([1-9]|[12]\\d|3[01])(?!\\d)=>$1年$3月$5日', '2026.03.09', '2026年3月9日', '2026年3月9日'],
  ['STD-DATE-CHINESE-NUMERAL', '成文日期使用汉字数字', '格式与版式', '提示', 'format', '7.3.5.4', '[〇零一二三四五六七八九]{4}年[一二三四五六七八九十〇零]{1,3}月', '二〇二六年三月', '2026年3月'],
  ['STD-ISSUE-YEAR-TWO-DIGIT', '发文字号年份不是四位', '格式与版式', '提示', 'format', '7.2.5', '〔\\d{2}〕(?=\\d*号)', '某办发〔26〕12号', '某办发〔2026〕12号'],
  ['STD-ISSUE-INNER-SPACE', '发文字号内部有空格', '格式与版式', '提示', 'format', '7.2.5', '〔\\s*[12]\\d{3}\\s*〕\\s+\\d+\\s*号|〔\\s+[12]\\d{3}\\s*〕\\s*\\d+\\s*号|〔\\s*[12]\\d{3}\\s+〕\\s*\\d+\\s*号', '某办发〔2026〕 12号', '某办发〔2026〕12号'],
  ['STD-ISSUE-FULLWIDTH-YEAR', '发文字号年份使用全角数字', '格式与版式', '提示', 'format', '7.2.5', '〔[０-９]{4}〕', '某办发〔２０２６〕12号', '某办发〔2026〕12号'],
  ['STD-ATTACHMENT-NUMBERED', '附件说明写成附件序号', '格式与版式', '提示', 'format', '7.3.4', '附件\\s*[0-9０-９]{1,2}\\s*[:：]', '附件1：名册', '附件：名册'],
  ['STD-COPY-NUMBER-SHORT', '份号不足六位', '格式与版式', '提示', 'format', '份号为6位阿拉伯数字', '(?m)(?:^|\\n)份号[:：]\\s*\\d{1,5}(?!\\d)', '份号：12345', '份号：000001'],
  ['STD-COPY-NUMBER-LONG', '份号超过六位', '格式与版式', '提示', 'format', '份号为6位阿拉伯数字', '(?m)(?:^|\\n)份号[:：]\\s*\\d{7,}(?!\\d)', '份号：0000001', '份号：000001'],
  ['STD-URGENCY-PINGJI', '公文紧急程度写成平急', '公文业务规范', '提示', 'ordinance', '第九条。公文紧急程度为特急、加急；平急用于电报', '紧急程度[:：]\\s*平急', '紧急程度：平急', '紧急程度：特急'],
  ['STD-SECURITY-INTERNAL', '公文密级写成内部', '公文业务规范', '提示', 'ordinance', '第九条。密级为绝密、机密、秘密', '密级[:：]\\s*内部', '密级：内部', '密级：秘密'],
  ['STD-DOC-TYPE-REQUEST-REPORT', '文种混用请示报告', '公文业务规范', '提示', 'ordinance', '第八条。请示和报告是两种文种', '请示报告|申请报告', '关于经费的请示报告', '关于经费的请示'],
  ['STD-LAW-REPEALED-CIVIL', '引用已废止的民事法律', '政策法规与事实依据', '提示', 'civil-code', '第1260条。只提示，不自动替换', '《(?:中华人民共和国)?(?:婚姻法|继承法|民法通则|收养法|担保法|合同法|物权法|侵权责任法|民法总则)》', '《中华人民共和国合同法》', '《中华人民共和国民法典》'],
  ['STD-AGENCY-SUPERSEDED-2018', '使用已调整机构的旧称', '政策法规与事实依据', '提示', 'agencies', '2018年机构改革后旧称。只提示，不自动替换', '(?:国土资源部|环境保护部|监察部)(?!门)|国家安全生产监督管理总局|国家卫生和计划生育委员会|中国银行保险监督管理委员会|中国银行业监督管理委员会|中国保险监督管理委员会|国家工商行政管理总局|国家质量监督检验检疫总局|国家新闻出版广电总局|国家食品药品监督管理总局|银保监会|银监会|保监会', '原国土资源部职责', '环境保护部门继续履职'],
  ['STD-ADVERB-DE', '状语误用的', '语言文字', '一般', 'confusable', '状语后用“地”。只覆盖明确的动词搭配', '(进一步|认真|切实|深入|全面|持续|坚决|积极|主动|及时)的(?=做好|开展|推进|落实|贯彻|执行|实施|加强|提高|完成|抓好|组织|研究|部署)=>$1地', '认真的落实', '认真的态度已经说明', '认真地'],
  ['STD-APPROX-DUNHAO', '概数中间加顿号', '语言文字', '一般', 'numerals', '相邻概数中间不加顿号', '(?<!第)([一二两三四五六七八九十])、([一二两三四五六七八九十])(?=个|名|人|天|日|项|条|次|户|家|倍|成|岁)=>$1$2', '三、四个问题', '一、工作安排已经三四个', '三四'],
  ['STD-REDUNDANT-DAYUE', '大约和左右并用', '语言文字', '提示', 'confusable', '冗余搭配', '大约([^，。；\\n]{1,12})左右=>大约$1', '大约三天左右', '大约三天', '大约三天'],
  ['STD-REDUNDANT-CHAOGUO', '超过和以上并用', '语言文字', '提示', 'confusable', '冗余搭配', '超过([^，。；\\n]{1,12})以上=>超过$1', '超过三天以上', '超过三天', '超过三天'],
  ['STD-REDUNDANT-JIEYU', '介于和之间并用', '语言文字', '提示', 'confusable', '冗余搭配', '介于([^，。；\\n]{1,16})之间=>介于$1', '介于三至五之间', '介于三至五', '介于三至五'],
  ['STD-REDUNDANT-YUANYIN', '原因是因为', '语言文字', '提示', 'confusable', '冗余搭配', '原因是因为=>原因是', '原因是因为材料不全', '原因是材料不全', '原因是'],
  ['STD-REDUNDANT-MUDI', '目的是为了', '语言文字', '提示', 'confusable', '冗余搭配', '目的是为了=>目的是', '目的是为了提高效率', '目的是提高效率', '目的是'],
  ['STD-PERCENT-SPACE', '百分号前有空格', '语言文字', '提示', 'numerals', '百分号紧贴数字', '(\\d)\\s+[%％]=>$1%', '完成80 %', '完成80%', '0%'],
  ['STD-ABSOLUTE-PROMISE', '绝对化承诺', '公文业务规范', '提示', 'ordinance', '公文表述留有余地。只提示，不自动替换', '绝对(?:保证|安全|正确|可靠|没有问题)', '绝对保证完成', '原则上完成'],
  ['STD-UNIT-NONLEGAL-PROMPT', '非同义的非法定单位', '语言文字', '提示', 'units', '不能按同义单位直接替换。只提示', '(?<![A-Za-z0-9])(?:英寸|英尺|英里|加仑|市斤)(?![A-Za-z0-9])', '约3英寸', '约3厘米'],
  ['STD-LEXICON-CONFUSABLE', '公文高频易错词', '语言文字', '一般', 'confusable', '通行推荐写法中可判定的错写', 'NAT-LEXICON-CONFUSABLE'],
  ['STD-LEXICON-IDIOM', '成语和固定词组误写', '语言文字', '一般', 'idiom', '通行成语写法', 'NAT-LEXICON-IDIOM'],
  ['STD-LEXICON-AGENCY', '机关名称易错写法', '政策法规与事实依据', '一般', 'agencies', '国发〔2023〕5号现行机构全称', 'NAT-LEXICON-AGENCY'],
  ['STD-LEXICON-DIVISION', '行政区划易错写法', '政策法规与事实依据', '一般', 'divisions', '现行行政区划全称', 'NAT-LEXICON-DIVISION'],
  ['STD-LEXICON-UNIT', '非法定计量单位旧称', '语言文字', '一般', 'units', '与法定单位同义的旧称', 'NAT-LEXICON-UNIT'],
  ['STD-LEXICON-PROPER', '专名和固定表述错字', '语言文字', '一般', 'proper', '可判定的错字', 'NAT-LEXICON-PROPER']
]

const cases = JSON.parse(readFileSync(new URL('../platform-bootstrap/src/test/resources/standards/review-rule-cases.json', import.meta.url), 'utf8'))
const existing = new Set(pack.rules.map((rule) => rule.code))
for (const rule of rules) {
  const [code, name, category, risk, source, clause, expression, positive, negative, replacement] = rule
  if (existing.has(code)) throw new Error('规则已存在: ' + code)
  const method = expression.startsWith('NAT-') ? '词库' : '正则'
  pack.rules.push({ code, name, category, risk, method, expression, source, clause, types: [] })
  if (method === '正则') {
    const example = { rule: code, positive, negative }
    if (replacement !== undefined) example.replacement = replacement
    cases.push(example)
  }
}

writeFileSync(new URL('official-review-pack.json', root), JSON.stringify(pack, null, 2) + '\n')
writeFileSync(new URL('../platform-bootstrap/src/test/resources/standards/review-rule-cases.json', import.meta.url), JSON.stringify(cases, null, 2) + '\n')
const manifest = {
  packVersion: pack.version,
  checkedOn: '2026-11-04',
  resources
}
writeFileSync(new URL('review-data-manifest.json', root), JSON.stringify(manifest, null, 2) + '\n')
const terms = resources.reduce((sum, item) => sum + item.variants, 0)
process.stdout.write(`rules ${pack.rules.length} dicts ${pack.dictionaries.length} terms ${terms} cases ${cases.length}\n`)
