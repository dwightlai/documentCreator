<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import {
  createDictionary, createRule, createTerm, deactivateRule, reactivateRule, deleteDictionary, deleteRule, deleteTerm, exportDictionariesJson, exportRulesJson, importDictionariesJson, importRulesJson, importTerms, listDictionaries, listRules, listTerms, getOfficialReviewPack, installOfficialReviewPack, type OfficialReviewPackInfo, type RuleImportResult,
  publishRule, submitRule, testRule, updateDictionary, updateRule, updateTerm, type Dictionary, type DictionaryTerm, type ReviewRule,
} from '@/api/rule'

const mode = ref<'rules' | 'dictionaries'>('rules')
const loading = ref(false)
const rules = ref<ReviewRule[]>([])
const dictionaries = ref<Dictionary[]>([])
const terms = ref<DictionaryTerm[]>([])
const ruleTerms = ref<DictionaryTerm[]>([])
const ruleTermQuery = ref('')
const officialPack = ref<OfficialReviewPackInfo>()
const selectedRuleId = ref('')
const selectedDictionaryId = ref('')
const ruleDialog = ref(false)
const dictionaryDialog = ref(false)
const termDialog = ref(false)
const editingRuleId = ref('')
const editingDictionaryId = ref('')
const editingTermId = ref('')
const testText = ref('')
const expectedCount = ref(1)
const testRunning = ref(false)
const testFeedback = ref<{ passed: boolean; actual: number; expected: number; matches: string[] } | null>(null)
const testError = ref('')
const ruleDetailOpen = ref(false)
const ruleQuery = ref('')
const ruleCategory = ref('全部')
const ruleStatus = ref('全部')
const rulePage = ref(1)
const rulePageSize = 8
const categoryOrder = ['政治与重要表述', '政策法规与事实依据', '公文业务规范', '内容与逻辑', '语言文字', '格式与版式']
const dictionaryTypes = ['机构与职务', '重要表述', '规范用语', '地名', '易错词', '标准文种', '格式要素', '其他']
const ruleCategories = computed(() => {
  const counts = new Map<string, number>()
  for (const rule of rules.value) counts.set(rule.category || '未分类', (counts.get(rule.category || '未分类') ?? 0) + 1)
  const names = [...counts.keys()].sort((a, b) => {
    const first = categoryOrder.indexOf(a)
    const second = categoryOrder.indexOf(b)
    if (first !== second) return (first < 0 ? 999 : first) - (second < 0 ? 999 : second)
    return a.localeCompare(b, 'zh-CN')
  })
  return [{ name: '全部', count: rules.value.length }, ...names.map(name => ({ name, count: counts.get(name) ?? 0 }))]
})
const visibleRules = computed(() => {
  const query = ruleQuery.value.trim().toLocaleLowerCase()
  return rules.value.filter(rule =>
    (ruleCategory.value === '全部' || (rule.category || '未分类') === ruleCategory.value)
    && (ruleStatus.value === '全部' || rule.lifecycleStatus === ruleStatus.value)
    && (!query || [rule.ruleName, rule.ruleCode, rule.category, rule.ruleExpression, rule.basis]
      .some(value => value?.toLocaleLowerCase().includes(query))))
})
const rulePageCount = computed(() => Math.max(1, Math.ceil(visibleRules.value.length / rulePageSize)))
const pagedRules = computed(() => visibleRules.value.slice((rulePage.value - 1) * rulePageSize, rulePage.value * rulePageSize))
const selectedRule = computed(() => rules.value.find(item => item.id === selectedRuleId.value))
const visibleRuleTerms = computed(() => {
  const query = ruleTermQuery.value.trim()
  if (!query) return ruleTerms.value
  return ruleTerms.value.filter(term => term.termText.includes(query) || (term.standardForm || '').includes(query) || term.aliases.some(alias => alias.includes(query)))
})
const selectedDictionary = computed(() => dictionaries.value.find(item => item.id === selectedDictionaryId.value))
const sourceUrl = (basis?: string) => basis?.match(/https?:\/\/[^；\s]+/)?.[0]

const ruleForm = reactive({
  ruleCode: '', ruleName: '', category: '语言文字', applicableDocumentTypes: ['请示'] as string[], riskLevel: '一般',
  detectionMethod: '词项', ruleExpression: '', basis: '', changeDescription: '创建初版',
})
const dictionaryForm = reactive({ dictCode: '', dictName: '', dictType: '易错词', sourceDescription: '' })
const termForm = reactive({ termText: '', standardForm: '', aliases: '', categoryTag: '规范用语', riskLevel: '一般', sourceReference: '', usageNote: '' })

function message(reason: unknown, fallback: string) {
  ElMessage.error(reason instanceof Error ? reason.message : fallback)
}

async function load() {
  loading.value = true
  try {
    [rules.value, dictionaries.value, officialPack.value] = await Promise.all([listRules(), listDictionaries(), getOfficialReviewPack()])
    if (!rules.value.some(item => item.id === selectedRuleId.value)) { selectedRuleId.value = ''; ruleDetailOpen.value = false }
    if (!dictionaries.value.some(item => item.id === selectedDictionaryId.value)) selectedDictionaryId.value = dictionaries.value.at(0)?.id ?? ''
  } catch (reason) { message(reason, '规则中心加载失败') }
  finally { loading.value = false }
}

async function installPack() {
  try {
    const result = await installOfficialReviewPack()
    await load()
    ElMessage.success(result.installed ? `已装载 ${result.rules} 条规则、${result.terms} 条标准词条` : '规范数据包已装载')
  } catch (reason) { message(reason, '规范数据包装载失败') }
}

function downloadJson(name: string, value: unknown) {
  const blob = new Blob([JSON.stringify(value, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${name}-${new Date().toISOString().slice(0, 10)}.json`
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function exportData(kind: 'rules' | 'dictionaries') {
  try {
    downloadJson(kind === 'rules' ? '审校规则' : '标准词库',
      kind === 'rules' ? await exportRulesJson() : await exportDictionariesJson())
    ElMessage.success('导出文件已生成')
  } catch (reason) { message(reason, '导出失败') }
}

async function importData(event: Event, kind: 'rules' | 'dictionaries') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    if (file.size > 5 * 1024 * 1024) { ElMessage.warning('JSON 文件不能超过 5 MB'); return }
    let bundle: unknown
    try { bundle = JSON.parse(await file.text()) }
    catch { ElMessage.error('文件不是有效的 JSON'); return }
    const result: RuleImportResult = kind === 'rules' ? await importRulesJson(bundle) : await importDictionariesJson(bundle)
    await load()
    const details = result.skippedCodes.length ? `\n已存在并跳过：${result.skippedCodes.slice(0, 15).join('、')}${result.skippedCodes.length > 15 ? '等' : ''}` : ''
    await ElMessageBox.alert(`共 ${result.total} ${kind === 'rules' ? '条规则' : '部词库'}，新增 ${result.imported}，跳过 ${result.skipped}${kind === 'dictionaries' ? `，新增词条 ${result.termsImported}` : ''}。${details}`, '导入完成', { confirmButtonText: '知道了' })
  } catch (reason) { message(reason, '导入失败') }
  finally { input.value = '' }
}

function openRule(rule?: ReviewRule) {
  editingRuleId.value = rule?.id ?? ''
  Object.assign(ruleForm, rule ? {
    ruleCode: rule.ruleCode, ruleName: rule.ruleName, category: rule.category,
    applicableDocumentTypes: [...rule.applicableDocumentTypes], riskLevel: rule.riskLevel,
    detectionMethod: rule.detectionMethod, ruleExpression: rule.ruleExpression,
    basis: rule.basis ?? '', changeDescription: '修改规则',
  } : { ruleCode: '', ruleName: '', category: '语言文字', applicableDocumentTypes: ['请示'], riskLevel: '一般', detectionMethod: '词项', ruleExpression: '', basis: '', changeDescription: '创建初版' })
  ruleDialog.value = true
}

function openDictionary(dictionary?: Dictionary) {
  editingDictionaryId.value = dictionary?.id ?? ''
  Object.assign(dictionaryForm, dictionary ? {
    dictCode: dictionary.dictCode, dictName: dictionary.dictName, dictType: dictionary.dictType,
    sourceDescription: dictionary.sourceDescription ?? '',
  } : { dictCode: '', dictName: '', dictType: '易错词', sourceDescription: '' })
  dictionaryDialog.value = true
}

function openTerm(term?: DictionaryTerm) {
  editingTermId.value = term?.id ?? ''
  Object.assign(termForm, term ? {
    termText: term.termText, standardForm: term.standardForm ?? '', aliases: term.aliases.join('；'),
    categoryTag: term.categoryTag ?? '', riskLevel: term.riskLevel ?? '一般',
    sourceReference: term.sourceReference ?? '', usageNote: term.usageNote ?? '',
  } : { termText: '', standardForm: '', aliases: '', categoryTag: '规范用语', riskLevel: '一般', sourceReference: '', usageNote: '' })
  termDialog.value = true
}

async function saveRule() {
  if (!ruleForm.ruleCode || !ruleForm.ruleName || !ruleForm.ruleExpression) { ElMessage.warning('请填写规则代码、名称与表达式'); return }
  try {
    const existing = rules.value.find(item => item.id === editingRuleId.value)
    const changed = existing
      ? await updateRule(existing.id, { ...ruleForm, lockVersion: existing.lockVersion })
      : await createRule({ ...ruleForm })
    ruleCategory.value = changed.category || '未分类'
    ruleStatus.value = '全部'
    ruleQuery.value = ''
    await load(); selectedRuleId.value = changed.id; testFeedback.value = null; testError.value = ''; ruleDetailOpen.value = true; ruleDialog.value = false
    ElMessage.success(existing ? '规则已更新，请重新测试后提交审核' : '规则草稿已建立')
  } catch (reason) { message(reason, '规则保存失败') }
}

async function removeRule() {
  const rule = selectedRule.value
  if (!rule) return
  try {
    await ElMessageBox.confirm(`确定删除规则“${rule.ruleName}”？`, '删除审校规则', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteRule(rule.id); ruleDetailOpen.value = false; await load(); ElMessage.success('规则已删除')
  } catch (reason) { if (reason !== 'cancel' && reason !== 'close') message(reason, '规则删除失败') }
}

async function runTest() {
  if (!selectedRule.value) return
  testFeedback.value = null
  testError.value = ''
  if (!testText.value.trim()) { testError.value = '请录入测试文本'; return }
  testRunning.value = true
  try {
    const result = await testRule(selectedRule.value.id, testText.value, expectedCount.value)
    const sample = result.results[0]
    testFeedback.value = { passed: result.passed, actual: sample.actualMatchCount, expected: sample.expectedMatchCount, matches: sample.matches }
    await load()
  } catch (reason) { testError.value = reason instanceof Error ? reason.message : '规则测试失败' }
  finally { testRunning.value = false }
}

async function transition(action: 'submit' | 'publish' | 'deactivate' | 'reactivate') {
  if (!selectedRule.value) return
  try {
    const handlers = { submit: submitRule, publish: publishRule, deactivate: deactivateRule, reactivate: reactivateRule }
    const changed = await handlers[action](selectedRule.value.id)
    rules.value = rules.value.map(item => item.id === changed.id ? changed : item)
    ElMessage.success({ submit: '已提交审核', publish: '规则已发布并即时进入审校引擎', deactivate: '规则已停用，后续审校不再使用', reactivate: '规则已重新启用，后续审校将使用此版本' }[action])
  } catch (reason) { message(reason, '规则状态变更失败') }
}

async function saveDictionary() {
  if (!dictionaryForm.dictCode || !dictionaryForm.dictName) { ElMessage.warning('请填写词库代码与名称'); return }
  try {
    const existing = dictionaries.value.find(item => item.id === editingDictionaryId.value)
    const changed = existing
      ? await updateDictionary(existing.id, { ...dictionaryForm, lockVersion: existing.lockVersion })
      : await createDictionary({ ...dictionaryForm })
    await load(); selectedDictionaryId.value = changed.id; dictionaryDialog.value = false
    ElMessage.success(existing ? '词库已更新' : '词库已创建')
  } catch (reason) { message(reason, '词库保存失败') }
}

async function removeDictionary() {
  const dictionary = selectedDictionary.value
  if (!dictionary) return
  try {
    await ElMessageBox.confirm(`确定删除词库“${dictionary.dictName}”及其 ${dictionary.entryCount} 条词条？`, '删除标准词库', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteDictionary(dictionary.id); await load(); ElMessage.success('词库已删除')
  } catch (reason) { if (reason !== 'cancel' && reason !== 'close') message(reason, '词库删除失败') }
}

async function saveTerm() {
  if (!selectedDictionary.value || !termForm.termText) { ElMessage.warning('请填写待规范词语'); return }
  try {
    const existing = terms.value.find(item => item.id === editingTermId.value)
    const payload = { ...termForm, aliases: termForm.aliases.split(/[;；|]/).map(item => item.trim()).filter(Boolean) }
    if (existing) await updateTerm(selectedDictionary.value.id, existing.id, { ...payload, lockVersion: existing.lockVersion })
    else await createTerm(selectedDictionary.value.id, payload)
    termDialog.value = false
    await loadTerms(selectedDictionary.value.id); await load()
    ElMessage.success(existing ? '词条已更新' : '词条已加入运行时词库')
  } catch (reason) { message(reason, '词条保存失败') }
}

async function removeTerm(term: DictionaryTerm) {
  if (!selectedDictionary.value) return
  try {
    await ElMessageBox.confirm(`确定删除词条“${term.termText}”？`, '删除标准词条', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteTerm(selectedDictionary.value.id, term.id)
    await loadTerms(selectedDictionary.value.id); await load(); ElMessage.success('词条已删除')
  } catch (reason) { if (reason !== 'cancel' && reason !== 'close') message(reason, '词条删除失败') }
}

async function uploadCsv(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedDictionary.value) return
  try {
    const result = await importTerms(selectedDictionary.value.id, file)
    await loadTerms(selectedDictionary.value.id); await load()
    ElMessage.success(`导入 ${result.importedRows} 条，跳过 ${result.skippedRows} 条`)
  } catch (reason) { message(reason, 'CSV 导入失败') }
  finally { input.value = '' }
}

async function loadTerms(id: string) {
  terms.value = id ? await listTerms(id) : []
}

watch(selectedDictionaryId, id => loadTerms(id).catch(reason => message(reason, '词条加载失败')))
watch([testText, expectedCount], () => { testFeedback.value = null; testError.value = '' })
watch([ruleQuery, ruleCategory, ruleStatus], () => { rulePage.value = 1 })
watch(rulePageCount, count => { if (rulePage.value > count) rulePage.value = count })
watch(mode, value => { if (value !== 'rules') ruleDetailOpen.value = false })
async function showRule(rule: ReviewRule) {
  selectedRuleId.value = rule.id
  testText.value = ''
  expectedCount.value = 1
  testFeedback.value = null
  testError.value = ''
  ruleTerms.value = []
  ruleTermQuery.value = ''
  ruleDetailOpen.value = true
  if (rule.detectionMethod !== '词库') return
  const dictionary = dictionaries.value.find(item => item.dictCode === rule.ruleExpression)
  if (!dictionary) return
  try { ruleTerms.value = await listTerms(dictionary.id) }
  catch (reason) { message(reason, '词条加载失败') }
}
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>规则与词库中心</template>
    <section class="rule-center" v-loading="loading">
      <header class="rule-hero">
        <div>
          <h2>规则与词库</h2>
          <p>规则须经过测试与审核后发布；有效词库会随下一次审校即时加载。</p>
        </div>
      </header>

      <section v-if="officialPack" class="official-pack-banner" aria-label="官方规范数据包">
        <div><strong>{{ officialPack.title }} · {{ officialPack.version }}</strong><p>{{ officialPack.scope }}。共 {{ officialPack.automaticRuleCount }} 条可执行规则、{{ officialPack.termCount }} 个参与规则匹配的词条。具体依据见每条记录。</p></div>
        <button v-if="!officialPack.installed" class="primary-button" type="button" @click="installPack">装载规范数据</button>
        <span v-else class="pack-installed">已装载</span>
      </section>

      <div class="rule-mode-switch" role="tablist">
        <button id="rules-tab" type="button" role="tab" aria-controls="rules-panel" :aria-selected="mode === 'rules'" :tabindex="mode === 'rules' ? 0 : -1" :class="{ active: mode === 'rules' }" @click="mode = 'rules'">审校规则</button>
        <button id="dictionaries-tab" type="button" role="tab" aria-controls="dictionaries-panel" :aria-selected="mode === 'dictionaries'" :tabindex="mode === 'dictionaries' ? 0 : -1" :class="{ active: mode === 'dictionaries' }" @click="mode = 'dictionaries'">标准词库</button>
      </div>

      <div v-if="mode === 'rules'" id="rules-panel" class="rule-workspace" role="tabpanel" aria-labelledby="rules-tab">
        <section class="rule-browser">
          <header class="rule-browser-head"><div><h3>审校规则</h3><p>按分类浏览，或输入名称、代码和依据查找。导入的规则从草稿开始审核。</p></div><div class="rule-exchange-actions"><label class="exchange-file">导入规则<input type="file" accept=".json,application/json" @change="importData($event, 'rules')"></label><button class="secondary-button" type="button" @click="exportData('rules')">导出规则</button><button class="primary-button" type="button" @click="openRule()">新建规则</button></div></header>
          <div class="rule-filters">
            <label class="rule-search">
              <span class="visually-hidden">搜索规则</span>
              <input v-model="ruleQuery" type="search" placeholder="搜索规则名称、代码或依据" />
            </label>
            <label class="rule-status-select"><span class="visually-hidden">按状态筛选规则</span><select v-model="ruleStatus"><option value="全部">全部状态</option><option v-for="status in ['生效', '审核', '测试', '草稿', '停用']" :key="status" :value="status">{{ status }}</option></select></label>
          </div>
          <div class="rule-category-list" aria-label="按分类筛选规则">
            <button v-for="item in ruleCategories" :key="item.name" type="button" :class="{ active: ruleCategory === item.name }" :aria-pressed="ruleCategory === item.name" @click="ruleCategory = item.name">
              <span>{{ item.name }}</span><b>{{ item.count }}</b>
            </button>
          </div>
          <div class="rule-list-count">共 {{ rules.length }} 条，当前显示 {{ visibleRules.length }} 条</div>
          <div v-if="pagedRules.length" class="rule-table-wrap">
            <table class="rule-table">
              <thead><tr><th scope="col">规则名称与内容</th><th scope="col">分类</th><th scope="col">检测方式</th><th scope="col">风险</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
              <tbody>
                <tr v-for="rule in pagedRules" :key="rule.id">
                  <td class="rule-main-cell"><button type="button" class="rule-name-button" @click="showRule(rule)">{{ rule.ruleName }}</button><small>{{ rule.ruleCode }}</small><p>{{ rule.ruleExpression }}</p></td>
                  <td>{{ rule.category || '未分类' }}</td>
                  <td>{{ rule.detectionMethod }}</td>
                  <td><span class="rule-risk" :data-risk="rule.riskLevel">{{ rule.riskLevel }}</span></td>
                  <td><span class="rule-status" :data-status="rule.lifecycleStatus">{{ rule.lifecycleStatus }}</span></td>
                  <td><button type="button" class="rule-view-button" :aria-label="'查看' + rule.ruleName" @click="showRule(rule)">查看详情</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="rule-no-results">{{ rules.length ? '没有找到符合条件的规则。' : '尚无规则，可新建规则。' }}<button v-if="rules.length" type="button" @click="ruleQuery = ''; ruleCategory = '全部'; ruleStatus = '全部'">清除筛选</button></div>
          <footer v-if="rulePageCount > 1" class="rule-pagination"><span>第 {{ rulePage }} / {{ rulePageCount }} 页</span><el-pagination v-model:current-page="rulePage" :page-size="rulePageSize" :total="visibleRules.length" layout="prev, pager, next" background /></footer>
        </section>
        <el-drawer v-model="ruleDetailOpen" title="规则详情" size="760px" class="rule-detail-drawer">
        <article v-if="selectedRule" class="rule-detail rule-drawer-content">
          <header><div><span>{{ selectedRule.ruleCode }}</span><h3>{{ selectedRule.ruleName }}</h3></div><b :data-status="selectedRule.lifecycleStatus">{{ selectedRule.lifecycleStatus }}</b></header>
          <div class="rule-meta">
            <div><small>风险等级</small><strong>{{ selectedRule.riskLevel }}</strong></div>
            <div><small>检测方式</small><strong>{{ selectedRule.detectionMethod }}</strong></div>
            <div><small>适用文种</small><strong>{{ selectedRule.applicableDocumentTypes.join('、') || '全部' }}</strong></div>
            <div><small>规则版本</small><strong>V{{ selectedRule.currentVersion }}</strong></div>
          </div>
          <section class="expression-sheet"><span>{{ selectedRule.detectionMethod === '人工核验' ? '核验要点' : selectedRule.detectionMethod === '词库' ? '引用词库' : '规则表达式' }}</span><code>{{ selectedRule.ruleExpression }}</code><p>{{ selectedRule.basis || '未填写规则依据' }}</p><a v-if="sourceUrl(selectedRule.basis)" :href="sourceUrl(selectedRule.basis)" target="_blank" rel="noopener noreferrer">查看规范原文</a></section>
          <section v-if="selectedRule.detectionMethod === '词库'" class="rule-term-sheet">
            <header><span>具体词条</span><small>{{ visibleRuleTerms.length }} / {{ ruleTerms.length }}</small></header>
            <input v-model="ruleTermQuery" type="search" placeholder="搜索词条或规范写法" />
            <div class="rule-term-list">
              <div v-for="term in visibleRuleTerms" :key="term.id" class="rule-term-row"><strong>{{ term.termText }}</strong><span>→</span><span>{{ term.standardForm && term.standardForm !== term.termText ? term.standardForm : '仅提示' }}</span></div>
              <p v-if="!ruleTerms.length">该词库暂无词条。</p>
              <p v-else-if="!visibleRuleTerms.length">没有匹配的词条。</p>
            </div>
          </section>
          <section v-if="!['人工核验', '词库'].includes(selectedRule.detectionMethod) && ['草稿', '测试'].includes(selectedRule.lifecycleStatus)" class="rule-test-bed">
            <header><div><h4>命中测试</h4></div><small>发布前必须通过</small></header>
            <textarea v-model="testText" :disabled="testRunning" placeholder="粘贴测试文本；例如半角 : 与全角 ： 会得到不同结果" />
            <div><label>预期命中 <el-input-number v-model="expectedCount" :min="0" :max="100" /></label><button class="secondary-button" type="button" :disabled="testRunning" @click="runTest">{{ testRunning ? '测试中…' : '运行测试' }}</button></div>
            <p v-if="testError" class="rule-test-feedback error" role="alert">测试失败：{{ testError }}</p>
            <div v-if="testFeedback" class="rule-test-feedback" :class="testFeedback.passed ? 'passed' : 'failed'" role="status">
              <strong>{{ testFeedback.passed ? '测试通过' : '未通过：实际命中与预期不一致' }}</strong>
              <span>预期 {{ testFeedback.expected }} 处，实际 {{ testFeedback.actual }} 处。</span>
              <span v-if="testFeedback.matches.length">命中内容：{{ testFeedback.matches.slice(0, 10).join('、') }}{{ testFeedback.matches.length > 10 ? '…' : '' }}</span>
              <span v-else>没有找到匹配内容。请检查测试文本中的字符以及规则表达式。</span>
            </div>
          </section>
          <p v-else-if="!['人工核验', '词库'].includes(selectedRule.detectionMethod)" class="rule-test-note">当前状态无需运行命中测试。修改规则会产生草稿版本，届时可重新测试。</p>
          <footer class="lifecycle-actions">
            <button v-if="['草稿', '测试', '停用'].includes(selectedRule.lifecycleStatus)" class="secondary-button" type="button" @click="openRule(selectedRule)">编辑规则</button>
            <button v-if="['草稿', '测试', '停用'].includes(selectedRule.lifecycleStatus)" class="secondary-button" type="button" @click="removeRule">删除规则</button>
            <button v-if="selectedRule.lifecycleStatus === '测试' || (['人工核验', '词库'].includes(selectedRule.detectionMethod) && selectedRule.lifecycleStatus === '草稿')" class="primary-button" type="button" @click="transition('submit')">提交审核</button>
            <button v-if="selectedRule.lifecycleStatus === '审核'" class="primary-button" type="button" @click="transition('publish')">批准并发布</button>
            <button v-if="selectedRule.lifecycleStatus === '生效'" class="secondary-button" type="button" @click="transition('deactivate')">停用规则</button>
            <button v-if="selectedRule.lifecycleStatus === '停用'" class="primary-button" type="button" @click="transition('reactivate')">重新启用</button>
            <span>{{ selectedRule.lifecycleStatus === '停用' ? '重新启用将恢复当前已审核版本；编辑规则后须重新测试、审核和发布。' : '草稿 → 测试 → 审核 → 生效' }}</span>
          </footer>
        </article>
        </el-drawer>
      </div>

      <div v-else id="dictionaries-panel" class="dictionary-workspace" role="tabpanel" aria-labelledby="dictionaries-tab">
        <div class="dictionary-exchange-bar"><div><strong>标准词库</strong><p>JSON 文件包含词库及全部词条；已有代码在导入时跳过。</p></div><div class="rule-exchange-actions"><label class="exchange-file">导入词库<input type="file" accept=".json,application/json" @change="importData($event, 'dictionaries')"></label><button class="secondary-button" type="button" @click="exportData('dictionaries')">导出词库</button></div></div>
        <div class="governance-grid dictionary-layout">
        <aside class="governance-list">
          <header><div><h3>词库目录</h3></div><button type="button" @click="openDictionary()">＋ 新建</button></header>
          <button v-for="item in dictionaries" :key="item.id" type="button" class="dictionary-row" :class="{ selected: item.id === selectedDictionaryId }" @click="selectedDictionaryId = item.id">
            <span>库</span><div><strong>{{ item.dictName }}</strong><small>{{ item.dictCode }} · V{{ item.currentVersion }}</small></div><b>{{ item.entryCount }}</b>
          </button>
          <div v-if="!dictionaries.length" class="governance-empty">尚无词库。</div>
        </aside>
        <article v-if="selectedDictionary" class="dictionary-detail">
          <header><div><span>{{ selectedDictionary.dictType }}</span><h3>{{ selectedDictionary.dictName }}</h3><p>{{ selectedDictionary.sourceDescription || '未填写来源说明' }}</p></div><div class="dictionary-tools"><button class="secondary-button" type="button" @click="openDictionary(selectedDictionary)">编辑词库</button><button class="secondary-button" type="button" @click="removeDictionary">删除词库</button><label>导入 CSV<input type="file" accept=".csv,text/csv" @change="uploadCsv"></label><button class="primary-button" type="button" @click="openTerm()">新增词条</button></div></header>
          <div class="term-table">
            <div class="term-table-head"><span>标准词条</span><span>规范写法</span><span>类别 / 风险</span><span>依据</span><span>操作</span></div>
            <div v-for="term in terms" :key="term.id" class="term-row">
              <strong>{{ term.termText }}<small v-if="term.aliases.length">别名：{{ term.aliases.join('、') }}</small></strong>
              <span>{{ term.standardForm || '仅提示' }}</span><span>{{ term.categoryTag || '未分类' }} · {{ term.riskLevel || '提示' }}</span><span><a v-if="sourceUrl(term.sourceReference)" :href="sourceUrl(term.sourceReference)" target="_blank" rel="noopener noreferrer">查看依据</a><template v-else>{{ term.sourceReference || '人工核定' }}</template></span><span class="term-actions"><button type="button" @click="openTerm(term)">编辑</button><button type="button" @click="removeTerm(term)">删除</button></span>
            </div>
            <div v-if="!terms.length" class="governance-empty">暂无词条，可逐条新增或导入 UTF-8 CSV。</div>
          </div>
        </article>
        </div>
      </div>
    </section>

    <el-dialog v-model="ruleDialog" :title="editingRuleId ? '编辑审校规则' : '建立审校规则'" width="620px" class="task-dialog">
      <el-form label-position="top"><div class="form-pair"><el-form-item label="规则代码"><el-input v-model="ruleForm.ruleCode" :disabled="!!editingRuleId" placeholder="WORD-001" /></el-form-item><el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" /></el-form-item></div>
        <div class="form-pair"><el-form-item label="业务分类"><el-select v-model="ruleForm.category"><el-option v-for="item in categoryOrder" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="风险等级"><el-select v-model="ruleForm.riskLevel"><el-option v-for="item in ['提示','一般','重要','严重']" :key="item" :value="item" /></el-select></el-form-item></div>
        <div class="form-pair"><el-form-item label="检测方式"><el-select v-model="ruleForm.detectionMethod"><el-option label="词项匹配" value="词项" /><el-option label="正则表达式" value="正则" /><el-option label="引用标准词库" value="词库" /><el-option label="人工核验" value="人工核验" /></el-select></el-form-item><el-form-item label="适用文种"><el-select v-model="ruleForm.applicableDocumentTypes" multiple><el-option v-for="item in ['请示','报告','通知','函','纪要']" :key="item" :value="item" /></el-select></el-form-item></div>
        <el-form-item :label="ruleForm.detectionMethod === '词库' ? '引用词库' : '规则表达式'"><el-select v-if="ruleForm.detectionMethod === '词库'" v-model="ruleForm.ruleExpression" placeholder="选择词库"><el-option v-for="item in dictionaries" :key="item.id" :label="item.dictName" :value="item.dictCode" /></el-select><el-input v-else v-model="ruleForm.ruleExpression" placeholder="错误表达=>建议表达" /></el-form-item><el-form-item label="规则依据"><el-input v-model="ruleForm.basis" type="textarea" /></el-form-item><el-form-item v-if="editingRuleId" label="修改说明"><el-input v-model="ruleForm.changeDescription" /></el-form-item></el-form>
      <template #footer><button class="secondary-button" type="button" @click="ruleDialog = false">取消</button><button class="primary-button" type="button" @click="saveRule">{{ editingRuleId ? '保存修改' : '建立草稿' }}</button></template>
    </el-dialog>
    <el-dialog v-model="dictionaryDialog" :title="editingDictionaryId ? '编辑标准词库' : '建立标准词库'" width="520px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="词库代码"><el-input v-model="dictionaryForm.dictCode" :disabled="!!editingDictionaryId" /></el-form-item><el-form-item label="词库名称"><el-input v-model="dictionaryForm.dictName" /></el-form-item></div><el-form-item label="词库数据类型"><el-select v-model="dictionaryForm.dictType"><el-option v-for="item in dictionaryTypes" :key="item" :value="item" /><el-option v-if="dictionaryForm.dictType && !dictionaryTypes.includes(dictionaryForm.dictType)" :value="dictionaryForm.dictType" /></el-select></el-form-item><el-form-item label="来源说明"><el-input v-model="dictionaryForm.sourceDescription" type="textarea" /></el-form-item></el-form><template #footer><button class="secondary-button" type="button" @click="dictionaryDialog = false">取消</button><button class="primary-button" type="button" @click="saveDictionary">{{ editingDictionaryId ? '保存修改' : '创建词库' }}</button></template></el-dialog>
    <el-dialog v-model="termDialog" :title="editingTermId ? '编辑标准词条' : '新增标准词条'" width="560px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="词条或待规范写法"><el-input v-model="termForm.termText" /></el-form-item><el-form-item label="标准形式"><el-input v-model="termForm.standardForm" /></el-form-item></div><p class="term-form-help">标准形式与词条相同时仅作规范参考，不会将正确写法报为错误。</p><el-form-item label="别名（用分号分隔）"><el-input v-model="termForm.aliases" /></el-form-item><div class="form-pair"><el-form-item label="分类"><el-input v-model="termForm.categoryTag" /></el-form-item><el-form-item label="风险等级"><el-select v-model="termForm.riskLevel"><el-option v-for="item in ['提示','一般','重要','严重']" :key="item" :value="item" /></el-select></el-form-item></div><el-form-item label="来源依据"><el-input v-model="termForm.sourceReference" /></el-form-item><el-form-item label="使用说明"><el-input v-model="termForm.usageNote" type="textarea" /></el-form-item></el-form><template #footer><button class="secondary-button" type="button" @click="termDialog = false">取消</button><button class="primary-button" type="button" @click="saveTerm">{{ editingTermId ? '保存修改' : '保存并生效' }}</button></template></el-dialog>
  </AppShell>
</template>

<style scoped>
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.rule-hero { color: #1d2939; background: #fff; border: 1px solid #e1e4ea; border-left: 4px solid var(--red); border-radius: 10px; }
.rule-hero h2 { color: #1d2939; }
.rule-hero p { color: #667085; }
.expression-sheet { background: #f8f9fb; border: 1px solid #e1e4ea; border-left: 3px solid var(--red); border-radius: 8px; }
.expression-sheet span { color: #667085; letter-spacing: 0; }
.expression-sheet code { color: #1d2939; font-family: inherit; font-weight: 500; white-space: pre-wrap; }
.expression-sheet p { color: #475467; line-height: 1.6; }
.expression-sheet a, .rule-drawer-content .expression-sheet a { display: inline-block; margin-top: 7px; color: var(--red); }
.rule-term-sheet { margin-top: 14px; }
.rule-term-sheet header { display: flex; justify-content: space-between; margin-bottom: 8px; color: #667085; font-size: 12px; }
.rule-term-sheet input { width: 100%; margin-bottom: 8px; padding: 8px 10px; border: 1px solid #e1e4ea; border-radius: 8px; }
.rule-term-list { max-height: 420px; overflow: auto; border: 1px solid #e1e4ea; border-radius: 8px; background: #fff; }
.rule-term-row { display: grid; grid-template-columns: 1fr 16px 1fr; gap: 8px; padding: 8px 12px; border-top: 1px solid #eef0f3; font-size: 13px; }
.rule-term-row:first-child { border-top: 0; }
.rule-term-list p { margin: 0; padding: 12px; color: #667085; }
.rule-exchange-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.exchange-file { min-height: 38px; padding: 0 14px; display: inline-flex; align-items: center; justify-content: center; color: var(--red); background: #fff; border: 1px solid #e8aaa6; border-radius: 8px; cursor: pointer; white-space: nowrap; font-size: 13px; }
.exchange-file:hover { background: #fff1f0; }
.exchange-file input { display: none; }
.dictionary-workspace { margin-top: 18px; }
.dictionary-exchange-bar { padding: 16px 20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; background: #fff; border: 1px solid var(--line); border-radius: 10px; }
.dictionary-exchange-bar strong { color: #1d2939; font-size: 15px; }
.dictionary-exchange-bar p { margin: 4px 0 0; color: #667085; font-size: 12px; }
.rule-workspace { margin-top: 18px; }
.rule-browser { padding: 24px; background: #fff; border: 1px solid var(--line); border-radius: 12px; box-shadow: var(--shadow); }
.rule-browser-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.rule-browser-head h3 { margin: 0; font-size: 20px; color: #1d2939; }
.rule-browser-head p { margin: 6px 0 0; color: #667085; font-size: 13px; }
.rule-filters { display: flex; gap: 12px; margin: 22px 0 14px; }
.rule-search { flex: 1; }
.rule-search input, .rule-status-select select { width: 100%; height: 40px; padding: 0 12px; color: #1d2939; background: #f8f9fb; border: 1px solid #d9dee7; border-radius: 8px; font: inherit; font-size: 13px; }
.rule-search input:focus, .rule-status-select select:focus { outline: 2px solid rgba(196, 48, 43, .16); border-color: var(--red); }
.rule-status-select { width: 150px; }
.rule-category-list { display: flex; flex-wrap: wrap; gap: 8px; padding-bottom: 18px; border-bottom: 1px solid #e7e9ee; }
.rule-category-list button { display: inline-flex; align-items: center; gap: 10px; min-height: 34px; padding: 0 12px; color: #475467; background: #f7f8fa; border: 1px solid #e1e4ea; border-radius: 18px; cursor: pointer; font-size: 13px; }
.rule-category-list button:hover { border-color: #e8aaa6; }
.rule-category-list button.active { color: var(--red); background: #fff1f0; border-color: #e8aaa6; font-weight: 600; }
.rule-category-list b { color: inherit; font-size: 12px; }
.rule-list-count { padding: 16px 0 10px; color: #667085; font-size: 12px; }
.rule-table-wrap { overflow-x: auto; }
.rule-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.rule-table th { padding: 11px 14px; color: #667085; background: #f7f8fa; border-bottom: 1px solid #e1e4ea; text-align: left; font-size: 12px; font-weight: 500; }
.rule-table th:first-child { width: 42%; }
.rule-table th:nth-child(2) { width: 13%; }
.rule-table th:nth-child(3) { width: 13%; }
.rule-table th:nth-child(4) { width: 9%; }
.rule-table th:nth-child(5) { width: 9%; }
.rule-table th:nth-child(6) { width: 14%; }
.rule-table td { padding: 12px 14px; border-bottom: 1px solid #e9ebef; color: #344054; font-size: 13px; vertical-align: middle; }
.rule-table tbody tr:hover { background: #fafbfc; }
.rule-main-cell { min-width: 320px; }
.rule-name-button { padding: 0; color: #1d2939; background: none; border: 0; cursor: pointer; text-align: left; font-size: 14px; font-weight: 600; }
.rule-name-button:hover, .rule-view-button:hover { color: var(--red); text-decoration: underline; }
.rule-main-cell small { display: block; margin-top: 4px; color: #98a2b3; font-size: 11px; }
.rule-main-cell p { display: -webkit-box; overflow: hidden; margin: 7px 0 0; color: #667085; font-size: 12px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 1; overflow-wrap: anywhere; }
.rule-risk, .rule-status { display: inline-block; padding: 4px 8px; border-radius: 14px; white-space: nowrap; background: #f2f4f7; color: #475467; font-size: 12px; }
.rule-risk[data-risk="重要"], .rule-risk[data-risk="严重"] { background: #fff1f0; color: #b42318; }
.rule-status[data-status="生效"] { background: #e8f5ec; color: #157347; }
.rule-status[data-status="审核"] { background: #fff4e5; color: #946200; }
.rule-view-button { padding: 0; color: var(--red); background: none; border: 0; cursor: pointer; white-space: nowrap; font-size: 13px; }
.rule-no-results { padding: 60px 16px; color: #667085; text-align: center; font-size: 13px; }
.rule-no-results button { margin-left: 8px; color: var(--red); border: 0; background: none; cursor: pointer; }
.rule-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 20px; color: #667085; font-size: 12px; }
.rule-drawer-content { min-height: 100%; padding: 12px 18px 24px; border: 0; box-shadow: none; }
.rule-drawer-content .expression-sheet { overflow-wrap: anywhere; }
.rule-drawer-content .lifecycle-actions { flex-wrap: wrap; }
.rule-drawer-content .lifecycle-actions span { width: 100%; margin-left: 0; }
.rule-test-feedback { display: flex !important; align-items: flex-start !important; flex-direction: column; gap: 5px; margin: 12px 0 0; padding: 10px 12px; border: 1px solid #f0c9a2; border-radius: 6px; background: #fff8ee; color: #87520d; font-size: 13px; line-height: 1.5; }
.rule-test-feedback.passed { border-color: #b7dbc2; background: #f1faf3; color: #17663c; }
.rule-test-feedback.error { border-color: #e9b5b2; background: #fff4f3; color: #a52a21; }
.rule-test-note { margin: 18px 0 0; color: #667085; font-size: 13px; }
:deep(.rule-detail-drawer .el-drawer__body) { padding: 4px 20px 20px; }
:deep(.rule-detail-drawer) { max-width: 100vw; }
.official-pack-banner { margin: 14px 0 2px; padding: 14px 18px; display: flex; align-items: center; justify-content: space-between; gap: 20px; background: #fff; border: 1px solid #e0e3e8; border-radius: 10px; }
.official-pack-banner strong { font-size: 14px; color: #1d2939; }
.official-pack-banner p { margin: 5px 0 0; color: #667085; font-size: 12px; line-height: 1.6; }
.pack-installed { color: #14804a; white-space: nowrap; font-size: 13px; }
.term-form-help { margin: -8px 0 12px; color: #667085; font-size: 12px; }
.term-table-head, .term-row { grid-template-columns: 1.2fr 1fr 1fr 1fr 100px; }
.term-actions { display: flex; gap: 10px; }
.term-actions button { padding: 0; color: var(--red); background: none; border: 0; cursor: pointer; font-size: 12px; }
.dictionary-tools { flex-wrap: wrap; justify-content: flex-end; }
@media (max-width: 1100px) {
  .rule-table { min-width: 860px; }
  .term-table-head, .term-row { grid-template-columns: 1.2fr 1fr 1fr 100px; }
  .term-table-head span:nth-child(4), .term-row > span:nth-child(4) { display: none; }
}
@media (max-width: 700px) {
  .rule-browser-head, .dictionary-exchange-bar { flex-direction: column; align-items: stretch; }
  .rule-exchange-actions { justify-content: flex-start; }
  .rule-browser { padding: 16px; }
  .rule-browser-head { align-items: flex-start; }
  .rule-filters { flex-direction: column; }
  .rule-status-select { width: 100%; }
  .rule-pagination { flex-direction: column; align-items: flex-start; }
  .rule-drawer-content { padding: 0; }
  .rule-drawer-content .rule-meta { grid-template-columns: repeat(2, 1fr); }
}
</style>
