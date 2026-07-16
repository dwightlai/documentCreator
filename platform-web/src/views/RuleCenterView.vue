<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import {
  createDictionary, createRule, createTerm, deactivateRule, importTerms, listDictionaries, listRules, listTerms,
  publishRule, submitRule, testRule, type Dictionary, type DictionaryTerm, type ReviewRule,
} from '@/api/rule'

const mode = ref<'rules' | 'dictionaries'>('rules')
const loading = ref(false)
const rules = ref<ReviewRule[]>([])
const dictionaries = ref<Dictionary[]>([])
const terms = ref<DictionaryTerm[]>([])
const selectedRuleId = ref('')
const selectedDictionaryId = ref('')
const ruleDialog = ref(false)
const dictionaryDialog = ref(false)
const termDialog = ref(false)
const testText = ref('')
const expectedCount = ref(1)
const selectedRule = computed(() => rules.value.find(item => item.id === selectedRuleId.value))
const selectedDictionary = computed(() => dictionaries.value.find(item => item.id === selectedDictionaryId.value))
const activeRules = computed(() => rules.value.filter(item => item.lifecycleStatus === '生效').length)
const totalTerms = computed(() => dictionaries.value.reduce((sum, item) => sum + item.entryCount, 0))

const ruleForm = reactive({
  ruleCode: '', ruleName: '', category: '规范', applicableDocumentTypes: ['请示'] as string[], riskLevel: '一般',
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
    [rules.value, dictionaries.value] = await Promise.all([listRules(), listDictionaries()])
    selectedRuleId.value ||= rules.value.at(0)?.id ?? ''
    selectedDictionaryId.value ||= dictionaries.value.at(0)?.id ?? ''
  } catch (reason) { message(reason, '规则中心加载失败') }
  finally { loading.value = false }
}

async function saveRule() {
  if (!ruleForm.ruleCode || !ruleForm.ruleName || !ruleForm.ruleExpression) { ElMessage.warning('请填写规则代码、名称与表达式'); return }
  try {
    const created = await createRule({ ...ruleForm })
    rules.value.unshift(created); selectedRuleId.value = created.id; ruleDialog.value = false
    Object.assign(ruleForm, { ruleCode: '', ruleName: '', ruleExpression: '', basis: '' })
    ElMessage.success('规则草稿已建立')
  } catch (reason) { message(reason, '规则创建失败') }
}

async function runTest() {
  if (!selectedRule.value || !testText.value) { ElMessage.warning('请录入测试文本'); return }
  try {
    const result = await testRule(selectedRule.value.id, testText.value, expectedCount.value)
    ElMessage[result.passed ? 'success' : 'warning'](result.passed ? '测试通过，规则可提交审核' : '实际命中数与预期不一致')
    await load()
  } catch (reason) { message(reason, '规则测试失败') }
}

async function transition(action: 'submit' | 'publish' | 'deactivate') {
  if (!selectedRule.value) return
  try {
    const handlers = { submit: submitRule, publish: publishRule, deactivate: deactivateRule }
    const changed = await handlers[action](selectedRule.value.id)
    rules.value = rules.value.map(item => item.id === changed.id ? changed : item)
    ElMessage.success({ submit: '已提交审核', publish: '规则已发布并即时进入审校引擎', deactivate: '规则已停用' }[action])
  } catch (reason) { message(reason, '规则状态变更失败') }
}

async function saveDictionary() {
  if (!dictionaryForm.dictCode || !dictionaryForm.dictName) { ElMessage.warning('请填写词库代码与名称'); return }
  try {
    const created = await createDictionary({ ...dictionaryForm })
    dictionaries.value.push(created); selectedDictionaryId.value = created.id; dictionaryDialog.value = false
    Object.assign(dictionaryForm, { dictCode: '', dictName: '', sourceDescription: '' })
    ElMessage.success('词库已创建')
  } catch (reason) { message(reason, '词库创建失败') }
}

async function saveTerm() {
  if (!selectedDictionary.value || !termForm.termText) { ElMessage.warning('请填写待规范词语'); return }
  try {
    const created = await createTerm(selectedDictionary.value.id, {
      ...termForm, aliases: termForm.aliases.split(/[;；|]/).map(item => item.trim()).filter(Boolean),
    })
    terms.value.push(created); termDialog.value = false
    Object.assign(termForm, { termText: '', standardForm: '', aliases: '', sourceReference: '', usageNote: '' })
    await load(); ElMessage.success('词条已加入运行时词库')
  } catch (reason) { message(reason, '词条创建失败') }
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
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>规则与词库中心</template>
    <section class="rule-center" v-loading="loading">
      <header class="rule-hero">
        <div>
          <span class="section-no">GOVERNANCE / 规则治理</span>
          <h2>让每一条审校依据，<br><em>可验证、可追溯、可生效。</em></h2>
          <p>规则须经过测试与审核后发布；有效词库会随下一次审校即时加载。</p>
        </div>
        <dl>
          <div><dt>规则总数</dt><dd>{{ rules.length }}</dd></div>
          <div><dt>生效规则</dt><dd>{{ activeRules }}</dd></div>
          <div><dt>词条规模</dt><dd>{{ totalTerms }}</dd></div>
        </dl>
      </header>

      <div class="rule-mode-switch" role="tablist">
        <button id="rules-tab" type="button" role="tab" aria-controls="rules-panel" :aria-selected="mode === 'rules'" :tabindex="mode === 'rules' ? 0 : -1" :class="{ active: mode === 'rules' }" @click="mode = 'rules'">审校规则</button>
        <button id="dictionaries-tab" type="button" role="tab" aria-controls="dictionaries-panel" :aria-selected="mode === 'dictionaries'" :tabindex="mode === 'dictionaries' ? 0 : -1" :class="{ active: mode === 'dictionaries' }" @click="mode = 'dictionaries'">标准词库</button>
      </div>

      <div v-if="mode === 'rules'" id="rules-panel" class="governance-grid" role="tabpanel" aria-labelledby="rules-tab">
        <aside class="governance-list">
          <header><div><span class="section-no">01</span><h3>规则目录</h3></div><button type="button" @click="ruleDialog = true">＋ 新建</button></header>
          <button v-for="rule in rules" :key="rule.id" type="button" class="rule-row" :class="{ selected: rule.id === selectedRuleId }" @click="selectedRuleId = rule.id">
            <span class="risk-pin" :data-risk="rule.riskLevel">{{ rule.riskLevel.slice(0, 1) }}</span>
            <div><strong>{{ rule.ruleName }}</strong><small>{{ rule.ruleCode }} · {{ rule.category }}</small></div>
            <i :data-status="rule.lifecycleStatus">{{ rule.lifecycleStatus }}</i>
          </button>
          <div v-if="!rules.length" class="governance-empty">尚无规则，建立第一条可验证规则。</div>
        </aside>

        <article v-if="selectedRule" class="rule-detail">
          <header><div><span>{{ selectedRule.ruleCode }}</span><h3>{{ selectedRule.ruleName }}</h3></div><b :data-status="selectedRule.lifecycleStatus">{{ selectedRule.lifecycleStatus }}</b></header>
          <div class="rule-meta">
            <div><small>风险等级</small><strong>{{ selectedRule.riskLevel }}</strong></div>
            <div><small>检测方式</small><strong>{{ selectedRule.detectionMethod }}</strong></div>
            <div><small>适用文种</small><strong>{{ selectedRule.applicableDocumentTypes.join('、') || '全部' }}</strong></div>
            <div><small>规则版本</small><strong>V{{ selectedRule.currentVersion }}</strong></div>
          </div>
          <section class="expression-sheet"><span>RULE EXPRESSION</span><code>{{ selectedRule.ruleExpression }}</code><p>{{ selectedRule.basis || '未填写规则依据' }}</p></section>
          <section class="rule-test-bed">
            <header><div><span class="section-no">02</span><h4>命中测试</h4></div><small>发布前必须通过</small></header>
            <textarea v-model="testText" placeholder="粘贴一段包含或不包含目标表达的测试文本…" />
            <div><label>预期命中 <el-input-number v-model="expectedCount" :min="0" :max="100" /></label><button class="secondary-button" type="button" @click="runTest">运行测试</button></div>
          </section>
          <footer class="lifecycle-actions">
            <button v-if="selectedRule.lifecycleStatus === '测试'" class="primary-button" type="button" @click="transition('submit')">提交审核</button>
            <button v-if="selectedRule.lifecycleStatus === '审核'" class="primary-button" type="button" @click="transition('publish')">批准并发布</button>
            <button v-if="selectedRule.lifecycleStatus === '生效'" class="secondary-button" type="button" @click="transition('deactivate')">停用规则</button>
            <span>草稿 → 测试 → 审核 → 生效</span>
          </footer>
        </article>
        <div v-else class="canvas-empty"><span>规</span><h2>选择或创建规则</h2><p>生命周期状态与测试记录将在此呈现。</p></div>
      </div>

      <div v-else id="dictionaries-panel" class="governance-grid dictionary-layout" role="tabpanel" aria-labelledby="dictionaries-tab">
        <aside class="governance-list">
          <header><div><span class="section-no">01</span><h3>词库目录</h3></div><button type="button" @click="dictionaryDialog = true">＋ 新建</button></header>
          <button v-for="item in dictionaries" :key="item.id" type="button" class="dictionary-row" :class="{ selected: item.id === selectedDictionaryId }" @click="selectedDictionaryId = item.id">
            <span>库</span><div><strong>{{ item.dictName }}</strong><small>{{ item.dictCode }} · V{{ item.currentVersion }}</small></div><b>{{ item.entryCount }}</b>
          </button>
          <div v-if="!dictionaries.length" class="governance-empty">尚无词库。</div>
        </aside>
        <article v-if="selectedDictionary" class="dictionary-detail">
          <header><div><span>{{ selectedDictionary.dictType }}</span><h3>{{ selectedDictionary.dictName }}</h3><p>{{ selectedDictionary.sourceDescription || '未填写来源说明' }}</p></div><div class="dictionary-tools"><label>导入 CSV<input type="file" accept=".csv,text/csv" @change="uploadCsv"></label><button class="primary-button" type="button" @click="termDialog = true">新增词条</button></div></header>
          <div class="term-table">
            <div class="term-table-head"><span>待规范词语</span><span>标准形式</span><span>类别 / 风险</span><span>依据</span></div>
            <div v-for="term in terms" :key="term.id" class="term-row">
              <strong>{{ term.termText }}<small v-if="term.aliases.length">别名：{{ term.aliases.join('、') }}</small></strong>
              <span>{{ term.standardForm || '仅提示' }}</span><span>{{ term.categoryTag || '未分类' }} · {{ term.riskLevel || '提示' }}</span><span>{{ term.sourceReference || '人工核定' }}</span>
            </div>
            <div v-if="!terms.length" class="governance-empty">暂无词条，可逐条新增或导入 UTF-8 CSV。</div>
          </div>
        </article>
      </div>
    </section>

    <el-dialog v-model="ruleDialog" title="建立审校规则" width="620px" class="task-dialog">
      <el-form label-position="top"><div class="form-pair"><el-form-item label="规则代码"><el-input v-model="ruleForm.ruleCode" placeholder="WORD-001" /></el-form-item><el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" /></el-form-item></div>
        <div class="form-pair"><el-form-item label="分类"><el-input v-model="ruleForm.category" /></el-form-item><el-form-item label="风险等级"><el-select v-model="ruleForm.riskLevel"><el-option v-for="item in ['提示','一般','重要','严重']" :key="item" :value="item" /></el-select></el-form-item></div>
        <div class="form-pair"><el-form-item label="检测方式"><el-select v-model="ruleForm.detectionMethod"><el-option label="词项匹配" value="词项" /><el-option label="正则表达式" value="正则" /></el-select></el-form-item><el-form-item label="适用文种"><el-select v-model="ruleForm.applicableDocumentTypes" multiple><el-option v-for="item in ['请示','报告','通知','函','纪要']" :key="item" :value="item" /></el-select></el-form-item></div>
        <el-form-item label="规则表达式"><el-input v-model="ruleForm.ruleExpression" placeholder="错误表达=>建议表达" /></el-form-item><el-form-item label="规则依据"><el-input v-model="ruleForm.basis" type="textarea" /></el-form-item></el-form>
      <template #footer><button class="secondary-button" type="button" @click="ruleDialog = false">取消</button><button class="primary-button" type="button" @click="saveRule">建立草稿</button></template>
    </el-dialog>
    <el-dialog v-model="dictionaryDialog" title="建立标准词库" width="520px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="词库代码"><el-input v-model="dictionaryForm.dictCode" /></el-form-item><el-form-item label="词库名称"><el-input v-model="dictionaryForm.dictName" /></el-form-item></div><el-form-item label="词库类型"><el-input v-model="dictionaryForm.dictType" /></el-form-item><el-form-item label="来源说明"><el-input v-model="dictionaryForm.sourceDescription" type="textarea" /></el-form-item></el-form><template #footer><button class="secondary-button" type="button" @click="dictionaryDialog = false">取消</button><button class="primary-button" type="button" @click="saveDictionary">创建词库</button></template></el-dialog>
    <el-dialog v-model="termDialog" title="新增标准词条" width="560px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="待规范词语"><el-input v-model="termForm.termText" /></el-form-item><el-form-item label="标准形式"><el-input v-model="termForm.standardForm" /></el-form-item></div><el-form-item label="别名（用分号分隔）"><el-input v-model="termForm.aliases" /></el-form-item><div class="form-pair"><el-form-item label="分类"><el-input v-model="termForm.categoryTag" /></el-form-item><el-form-item label="风险等级"><el-select v-model="termForm.riskLevel"><el-option v-for="item in ['提示','一般','重要','严重']" :key="item" :value="item" /></el-select></el-form-item></div><el-form-item label="来源依据"><el-input v-model="termForm.sourceReference" /></el-form-item><el-form-item label="使用说明"><el-input v-model="termForm.usageNote" type="textarea" /></el-form-item></el-form><template #footer><button class="secondary-button" type="button" @click="termDialog = false">取消</button><button class="primary-button" type="button" @click="saveTerm">保存并生效</button></template></el-dialog>
  </AppShell>
</template>
