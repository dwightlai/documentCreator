<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import {
  createDocumentType,
  createModelConfig,
  createPromptTemplate,
  deactivateDocumentType,
  deactivatePromptTemplate,
  listAuditLogs,
  listDocumentTypes,
  listModelConfigs,
  listPromptTemplates,
  publishDocumentType,
  publishPromptTemplate,
  submitDocumentType,
  submitPromptTemplate,
  testDocumentType,
  testPromptTemplate,
  getSubmissionGate,
  updateSubmissionGate,
  type SubmissionGateConfig,
  type AuditLog,
  type DocumentTypeConfig,
  type ModelConfig,
  type PromptTemplate,
} from '@/api/governance'

type Panel = 'models' | 'prompts' | 'documentTypes' | 'gate' | 'audit'

const activePanel = ref<Panel>('models')
const gate = ref<SubmissionGateConfig>()
watch(activePanel, (panel) => { if (panel === 'gate' && !gate.value) loadGate() })
const GATE_RULES: { key: keyof SubmissionGateConfig; label: string; hint: string }[] = [
  { key: 'requireReview', label: '必须完成智能审校', hint: '未发起审校的文稿不得提交' },
  { key: 'requireLatestReview', label: '审校必须对应最新文稿', hint: '审校后除采纳审校建议外的修改，须重新审校' },
  { key: 'blockOnSevere', label: '存在严重问题禁止提交', hint: '严重问题须处理；忽略时必须填写原因' },
  { key: 'requireImportantProcessed', label: '重要问题必须全部处理', hint: '重要问题不得处于待处理状态' },
  { key: 'requireReport', label: '必须生成审校报告', hint: '提交前须生成审校报告留档' },
]
async function loadGate() { try { gate.value = await getSubmissionGate() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '门禁配置加载失败') } }
async function saveGate() {
  if (!gate.value) return
  try { gate.value = await updateSubmissionGate(gate.value); ElMessage.success('提交门禁配置已保存') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
}
const loading = ref(false)
const models = ref<ModelConfig[]>([])
const prompts = ref<PromptTemplate[]>([])
const documentTypes = ref<DocumentTypeConfig[]>([])
const audits = ref<AuditLog[]>([])
const modelDialog = ref(false)
const promptDialog = ref(false)
const documentTypeDialog = ref(false)
const auditType = ref('')
const auditTarget = ref('')

const modelForm = reactive({
  configCode: '', configName: '', provider: 'OpenAI Compatible', modelName: '', modelVersion: '', scene: '段落生成', dataLevel: '内部',
  maxContextTokens: 32768, timeoutMs: 60000, maxRetries: 2, costLimitDaily: 100, sensitiveWordFilter: true, outputValidation: true, active: true,
  endpointUrl: '', credentialRef: '', inputCostPer1k: 0, outputCostPer1k: 0, qpsLimit: 5, dailyTokenLimit: 1000000,
})
const promptForm = reactive({
  templateCode: '', templateName: '', applicableScene: '段落生成', documentTypeCode: '', applicableModelsText: '',
  systemRole: '你是一名严谨的政务文稿起草助手。', documentTypeMethod: '围绕主题 {{topic}} 生成结构严谨、表述规范的段落。', materialCitationRule: '引用材料时标注来源，不得改变事实含义。',
  antiFabricationRule: '不得编造事实、数据、政策依据或出处；依据不足时明确提示。', outputFormat: '只输出可直接写入正文的纯文本。',
  riskConstraint: '涉及敏感信息时停止生成并提示人工处理。', changeDescription: '创建初始版本',
})
const documentTypeForm = reactive({
  typeCode: '', typeName: '', documentCategory: '法定公文' as const, applicableScenario: '', direction: '上行' as '上行' | '下行' | '平行' | '内部',
  requiredFieldsText: '标题,主送机关,正文,落款', recommendedStructureText: '背景与依据,主要事项,工作要求', forbiddenPatternsText: '', closingPatternsText: '妥否，请批示。', changeDescription: '创建初始版本',
})

const activeModels = computed(() => models.value.filter((item) => item.active).length)
const effectivePrompts = computed(() => prompts.value.filter((item) => item.lifecycleStatus === '生效').length)
const effectiveDocumentTypes = computed(() => documentTypes.value.filter((item) => item.lifecycleStatus === '生效').length)
const riskAudits = computed(() => audits.value.filter((item) => item.auditType === '安全审计' || item.dataLevel === '敏感').length)

function splitList(value: string) {
  return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean)
}
function displayTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}
function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
function statusTone(status: string) {
  return status === '生效' || status === '启用' ? 'success' : status === '审核' || status === '测试' ? 'warning' : status === '停用' ? 'muted' : 'draft'
}

async function refreshAll() {
  loading.value = true
  try {
    const [modelData, promptData, documentTypeData, auditData] = await Promise.all([
      listModelConfigs(), listPromptTemplates(), listDocumentTypes(), listAuditLogs(),
    ])
    models.value = modelData
    prompts.value = promptData
    documentTypes.value = documentTypeData
    audits.value = auditData
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function saveModel() {
  try {
    await createModelConfig({ ...modelForm })
    modelDialog.value = false
    ElMessage.success('模型路由已登记，密钥仅通过环境变量引用')
    models.value = await listModelConfigs()
  } catch (error) { ElMessage.error(errorMessage(error)) }
}

async function savePrompt() {
  try {
    await createPromptTemplate({
      templateCode: promptForm.templateCode, templateName: promptForm.templateName, applicableScene: promptForm.applicableScene,
      documentTypeCode: promptForm.documentTypeCode, applicableModels: splitList(promptForm.applicableModelsText), systemRole: promptForm.systemRole,
      documentTypeMethod: promptForm.documentTypeMethod, inputFields: { topic: { required: true, description: '写作主题' } },
      materialCitationRule: promptForm.materialCitationRule, antiFabricationRule: promptForm.antiFabricationRule, outputFormat: promptForm.outputFormat,
      riskConstraint: promptForm.riskConstraint, modelParameters: { temperature: 0.2 }, testSampleIds: [], changeDescription: promptForm.changeDescription,
    })
    promptDialog.value = false
    ElMessage.success('提示词草稿已创建')
    prompts.value = await listPromptTemplates()
  } catch (error) { ElMessage.error(errorMessage(error)) }
}

async function saveDocumentType() {
  try {
    await createDocumentType({
      typeCode: documentTypeForm.typeCode, typeName: documentTypeForm.typeName, documentCategory: documentTypeForm.documentCategory,
      applicableScenario: documentTypeForm.applicableScenario, direction: documentTypeForm.direction,
      requiredFields: splitList(documentTypeForm.requiredFieldsText), recommendedStructure: splitList(documentTypeForm.recommendedStructureText),
      forbiddenPatterns: splitList(documentTypeForm.forbiddenPatternsText), closingPatterns: splitList(documentTypeForm.closingPatternsText),
      changeDescription: documentTypeForm.changeDescription,
    })
    documentTypeDialog.value = false
    ElMessage.success('文种配置草稿已创建')
    documentTypes.value = await listDocumentTypes()
  } catch (error) { ElMessage.error(errorMessage(error)) }
}

async function advancePrompt(item: PromptTemplate, action: 'test' | 'submit' | 'publish' | 'deactivate') {
  try {
    if (action === 'test') {
      const result = await testPromptTemplate(item.id, { topic: '基层治理能力提升' })
      if (!result.passed) throw new Error(`测试未通过：${result.unresolvedPlaceholders?.join('、') || '存在未满足约束'}`)
      ElMessage.success(`测试通过 · ${result.promptHash?.slice(0, 12) ?? '校验完成'}`)
    } else if (action === 'submit') await submitPromptTemplate(item.id)
    else if (action === 'publish') await publishPromptTemplate(item.id)
    else await deactivatePromptTemplate(item.id)
    prompts.value = await listPromptTemplates()
    audits.value = await listAuditLogs()
  } catch (error) { ElMessage.error(errorMessage(error)) }
}

async function advanceDocumentType(item: DocumentTypeConfig, action: 'test' | 'submit' | 'publish' | 'deactivate') {
  try {
    if (action === 'test') {
      const result = await testDocumentType(item.typeCode)
      if (!result.passed) throw new Error(`测试未通过：${result.errors?.join('；') || '配置不完整'}`)
      ElMessage.success(result.warnings?.length ? `测试通过，提示：${result.warnings.join('；')}` : '文种配置测试通过')
    } else if (action === 'submit') await submitDocumentType(item.typeCode)
    else if (action === 'publish') await publishDocumentType(item.typeCode)
    else await deactivateDocumentType(item.typeCode)
    documentTypes.value = await listDocumentTypes()
    audits.value = await listAuditLogs()
  } catch (error) { ElMessage.error(errorMessage(error)) }
}

async function filterAudits() {
  try { audits.value = await listAuditLogs(auditType.value, auditTarget.value) }
  catch (error) { ElMessage.error(errorMessage(error)) }
}

onMounted(refreshAll)
</script>

<template>
  <AppShell>
    <template #title>模型与配置治理</template>
    <section class="governance-console" v-loading="loading">
      <header class="governance-hero">
        <div class="governance-intro">
          <span class="section-no">MODEL GOVERNANCE / CONTROL LEDGER</span>
          <h2>让每一次智能生成，<br><em>有配置、有边界、有记录。</em></h2>
          <p>统一管理模型端点、提示词模板与文种规则。配置经过测试、审核、发布后生效，全部变更进入审计台账。</p>
        </div>
        <div class="governance-metrics">
          <article><span>01</span><strong>{{ activeModels }}</strong><small>启用模型路由</small></article>
          <article><span>02</span><strong>{{ effectivePrompts }}</strong><small>生效提示词</small></article>
          <article><span>03</span><strong>{{ effectiveDocumentTypes }}</strong><small>生效文种</small></article>
          <article :class="{ alert: riskAudits > 0 }"><span>04</span><strong>{{ riskAudits }}</strong><small>安全关注项</small></article>
        </div>
      </header>

      <nav class="governance-tabs" aria-label="治理模块">
        <button v-for="item in [{key:'models',label:'模型路由'},{key:'prompts',label:'提示词模板'},{key:'documentTypes',label:'文种配置'},{key:'gate',label:'提交门禁'},{key:'audit',label:'审计台账'}]" :key="item.key" type="button" :class="{ active: activePanel === item.key }" @click="activePanel = item.key as Panel">
          {{ item.label }}<i />
        </button>
      </nav>

      <section v-if="activePanel === 'models'" class="governance-panel">
        <header class="panel-heading"><div><span class="section-no">ROUTING MATRIX</span><h3>模型端点与数据边界</h3><p>端点必须命中服务端白名单；凭据字段仅保存环境变量名称，不落库存储密钥。</p></div><button class="primary-button" type="button" @click="modelDialog = true">登记模型路由</button></header>
        <div class="model-grid">
          <article v-for="model in models" :key="model.id" class="model-card">
            <header><div><small>{{ model.provider }}</small><h4>{{ model.configName }}</h4></div><span class="status-chip" :data-tone="model.active ? 'success' : 'muted'">{{ model.active ? '启用' : '停用' }}</span></header>
            <p class="model-id">{{ model.modelName }}<b v-if="model.modelVersion"> / {{ model.modelVersion }}</b></p>
            <dl><div><dt>适用场景</dt><dd>{{ model.scene }}</dd></div><div><dt>最高数据级别</dt><dd>{{ model.dataLevel }}</dd></div><div><dt>上下文</dt><dd>{{ model.maxContextTokens?.toLocaleString() ?? '—' }} tokens</dd></div><div><dt>调用额度</dt><dd>{{ model.qpsLimit ?? '—' }} QPS · {{ model.dailyTokenLimit?.toLocaleString() ?? '—' }}/日</dd></div></dl>
            <footer><span>{{ model.endpointUrl || '未配置端点' }}</span><time>{{ displayTime(model.updatedAt) }}</time></footer>
          </article>
          <div v-if="!models.length" class="governance-empty">尚未登记模型。生产环境启用前，请先配置 HTTPS 端点白名单和密钥环境变量。</div>
        </div>
      </section>

      <section v-else-if="activePanel === 'prompts'" class="governance-panel">
        <header class="panel-heading"><div><span class="section-no">PROMPT RELEASE PIPELINE</span><h3>提示词模板版本库</h3><p>完整提示词按版本固化，测试用于发现未解析变量，审核发布后才参与模型路由。</p></div><button class="primary-button" type="button" @click="promptDialog = true">新建提示词</button></header>
        <div class="config-ledger">
          <article v-for="item in prompts" :key="item.id">
            <div class="ledger-index">P{{ String(item.currentVersion).padStart(2, '0') }}</div>
            <div class="ledger-main"><header><div><small>{{ item.templateCode }} · {{ item.applicableScene }}</small><h4>{{ item.templateName }}</h4></div><span class="status-chip" :data-tone="statusTone(item.lifecycleStatus)">{{ item.lifecycleStatus }}</span></header><p>{{ item.systemRole }}</p><footer><span>文种 {{ item.documentTypeCode || '通用' }}</span><span>模型 {{ item.applicableModels.join('、') || '全部' }}</span><time>{{ displayTime(item.updatedAt) }}</time></footer></div>
            <div class="ledger-actions"><button v-if="item.lifecycleStatus === '草稿'" type="button" @click="advancePrompt(item, 'test')">测试</button><button v-if="item.lifecycleStatus === '测试'" type="button" @click="advancePrompt(item, 'submit')">送审</button><button v-if="item.lifecycleStatus === '审核'" class="emphasis" type="button" @click="advancePrompt(item, 'publish')">发布</button><button v-if="item.lifecycleStatus === '生效'" type="button" @click="advancePrompt(item, 'deactivate')">停用</button></div>
          </article>
          <div v-if="!prompts.length" class="governance-empty">暂无提示词模板。创建草稿后，依次完成测试、送审和发布。</div>
        </div>
      </section>

      <section v-else-if="activePanel === 'documentTypes'" class="governance-panel">
        <header class="panel-heading"><div><span class="section-no">DOCUMENT METHOD LIBRARY</span><h3>文种方法与结构约束</h3><p>文种配置直接驱动提纲建议，并定义必填字段、推荐结构、禁用模式与规范结语。</p></div><button class="primary-button" type="button" @click="documentTypeDialog = true">新建文种</button></header>
        <div class="document-type-grid">
          <article v-for="item in documentTypes" :key="item.id"><header><div class="type-monogram">{{ item.typeName.slice(0, 1) }}</div><div><small>{{ item.documentCategory }} · {{ item.direction || '未分类' }}</small><h4>{{ item.typeName }}</h4></div><span class="status-chip" :data-tone="statusTone(item.lifecycleStatus)">{{ item.lifecycleStatus }}</span></header><p>{{ item.applicableScenario || '暂无适用场景说明' }}</p><dl><div><dt>必填要素</dt><dd>{{ item.requiredFields.join('、') }}</dd></div><div><dt>推荐结构</dt><dd>{{ item.recommendedStructure.join(' / ') }}</dd></div></dl><footer><span>{{ item.id.startsWith('builtin-') ? '平台内置 · 可创建同编码配置覆盖' : `版本 V${item.currentVersion}` }}</span><div v-if="!item.id.startsWith('builtin-')" class="ledger-actions"><button v-if="item.lifecycleStatus === '草稿'" type="button" @click="advanceDocumentType(item, 'test')">测试</button><button v-if="item.lifecycleStatus === '测试'" type="button" @click="advanceDocumentType(item, 'submit')">送审</button><button v-if="item.lifecycleStatus === '审核'" class="emphasis" type="button" @click="advanceDocumentType(item, 'publish')">发布</button><button v-if="item.lifecycleStatus === '生效'" type="button" @click="advanceDocumentType(item, 'deactivate')">停用</button></div></footer></article>
          <div v-if="!documentTypes.length" class="governance-empty">尚未配置文种方法。</div>
        </div>
      </section>

      <section v-else-if="activePanel === 'gate'" class="governance-panel">
        <div class="panel-heading"><div><span class="section-no">SUBMISSION GATE / §31.3</span><h3>提交门禁</h3><p>文稿提交 OA 前须通过以下检查。“涉密文稿禁止调用外部模型”由模型网关统一拒绝，不在此配置。</p></div><button class="primary-button" type="button" @click="saveGate">保存配置</button></div>
        <div v-if="gate" class="gate-config">
          <label v-for="rule in GATE_RULES" :key="rule.key"><input v-model="gate[rule.key]" type="checkbox"><div><strong>{{ rule.label }}</strong><small>{{ rule.hint }}</small></div></label>
        </div>
      </section>
      <section v-else class="governance-panel">
        <header class="panel-heading"><div><span class="section-no">IMMUTABLE AUDIT TRAIL</span><h3>全链路审计台账</h3><p>记录登录访问、内容操作、模型调用、导出下载、规则配置和安全事件。</p></div><div class="audit-filters"><input v-model="auditType" aria-label="审计类型" placeholder="审计类型"><input v-model="auditTarget" aria-label="目标编号" placeholder="目标编号"><button type="button" @click="filterAudits">筛选</button></div></header>
        <div class="audit-ledger"><div class="audit-head"><span>时间 / 类型</span><span>操作与对象</span><span>操作人</span><span>数据级别</span></div><article v-for="item in audits" :key="item.id"><div><time>{{ displayTime(item.occurredAt) }}</time><b>{{ item.auditType }}</b></div><div><strong>{{ item.action }}</strong><p>{{ item.targetType }} / {{ item.targetId }}</p><small>{{ item.detail || '—' }}</small></div><div><strong>{{ item.operatorId }}</strong><small>{{ item.operatorRole }}</small></div><div><span class="level-mark">{{ item.dataLevel || '内部' }}</span><small>{{ item.ipAddress || '—' }}</small></div></article><div v-if="!audits.length" class="governance-empty">当前筛选条件下没有审计记录。</div></div>
      </section>
    </section>

    <el-dialog v-model="modelDialog" title="登记模型路由" width="720px" class="governance-dialog">
      <div class="form-grid"><label>配置编码<input v-model="modelForm.configCode" placeholder="local-qwen-writing"></label><label>配置名称<input v-model="modelForm.configName" placeholder="内网写作模型"></label><label>服务商<input v-model="modelForm.provider"></label><label>模型名称<input v-model="modelForm.modelName" placeholder="qwen3-32b"></label><label>模型版本<input v-model="modelForm.modelVersion" placeholder="2026-06"></label><label>调用场景<select v-model="modelForm.scene"><option>段落生成</option><option>文本改写</option></select></label><label>最高数据级别<select v-model="modelForm.dataLevel"><option>普通</option><option>内部</option><option>敏感</option></select></label><label>密钥环境变量<input v-model="modelForm.credentialRef" placeholder="MODEL_API_KEY"></label><label class="span-2">HTTPS 端点<input v-model="modelForm.endpointUrl" placeholder="https://model.example.gov/v1/chat/completions"></label><label>上下文 tokens<input v-model.number="modelForm.maxContextTokens" type="number"></label><label>超时毫秒<input v-model.number="modelForm.timeoutMs" type="number"></label><label>QPS<input v-model.number="modelForm.qpsLimit" type="number"></label><label>每日 token<input v-model.number="modelForm.dailyTokenLimit" type="number"></label></div>
      <template #footer><button class="secondary-button" type="button" @click="modelDialog = false">取消</button><button class="primary-button" type="button" @click="saveModel">保存路由</button></template>
    </el-dialog>

    <el-dialog v-model="promptDialog" title="新建提示词模板" width="760px" class="governance-dialog">
      <div class="form-grid"><label>模板编码<input v-model="promptForm.templateCode" placeholder="official-writing-v1"></label><label>模板名称<input v-model="promptForm.templateName" placeholder="政务正文生成"></label><label>应用场景<input v-model="promptForm.applicableScene"></label><label>文种编码<input v-model="promptForm.documentTypeCode" placeholder="留空表示通用"></label><label class="span-2">适用模型<input v-model="promptForm.applicableModelsText" placeholder="多个模型用逗号分隔"></label><label class="span-2">系统角色<textarea v-model="promptForm.systemRole" rows="3" /></label><label class="span-2">文种方法<textarea v-model="promptForm.documentTypeMethod" rows="2" placeholder="可使用 {{topic}} 等变量" /></label><label class="span-2">防编造规则<textarea v-model="promptForm.antiFabricationRule" rows="2" /></label><label class="span-2">输出格式<textarea v-model="promptForm.outputFormat" rows="2" /></label><label class="span-2">变更说明<input v-model="promptForm.changeDescription"></label></div>
      <template #footer><button class="secondary-button" type="button" @click="promptDialog = false">取消</button><button class="primary-button" type="button" @click="savePrompt">创建草稿</button></template>
    </el-dialog>

    <el-dialog v-model="documentTypeDialog" title="新建文种配置" width="720px" class="governance-dialog">
      <div class="form-grid"><label>文种编码<input v-model="documentTypeForm.typeCode" placeholder="请示"></label><label>文种名称<input v-model="documentTypeForm.typeName" placeholder="请示"></label><label>文种分类<select v-model="documentTypeForm.documentCategory"><option>法定公文</option><option>事务文书</option></select></label><label>行文方向<select v-model="documentTypeForm.direction"><option>上行</option><option>下行</option><option>平行</option><option>内部</option></select></label><label class="span-2">适用场景<textarea v-model="documentTypeForm.applicableScenario" rows="2" /></label><label class="span-2">必填要素<input v-model="documentTypeForm.requiredFieldsText"></label><label class="span-2">推荐结构<input v-model="documentTypeForm.recommendedStructureText"></label><label class="span-2">禁用模式<input v-model="documentTypeForm.forbiddenPatternsText" placeholder="多个规则用逗号分隔"></label><label class="span-2">规范结语<input v-model="documentTypeForm.closingPatternsText"></label></div>
      <template #footer><button class="secondary-button" type="button" @click="documentTypeDialog = false">取消</button><button class="primary-button" type="button" @click="saveDocumentType">创建草稿</button></template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.gate-config{display:grid;gap:10px;max-width:720px}.gate-config label{display:grid;grid-template-columns:22px 1fr;gap:12px;align-items:start;padding:14px 16px;background:#fff;border:1px solid #e5e7eb;border-radius:10px;cursor:pointer}.gate-config input{width:18px;height:18px;margin-top:2px;accent-color:#c4302b}.gate-config div{display:grid;gap:3px}.gate-config strong{font-size:15px}.gate-config small{color:#646a73;font-size:13px}
.governance-console{padding:34px 42px 70px}.governance-hero{display:grid;grid-template-columns:1.25fr 1fr;gap:56px;padding:38px 42px;background:#17231e;color:#f4f0e8;position:relative;overflow:hidden}.governance-hero:after{content:"治";position:absolute;right:-24px;bottom:-92px;font:220px/1 sans-serif;color:rgba(255,255,255,.035)}.governance-intro h2{margin:18px 0 14px;font:500 38px/1.24 "Noto Sans SC",sans-serif;letter-spacing:-1px}.governance-intro h2 em{color:#d8b36a;font-style:normal}.governance-intro p{max-width:600px;color:#aebbb5;line-height:1.8}.governance-metrics{display:grid;grid-template-columns:repeat(2,1fr);border:1px solid rgba(255,255,255,.13);z-index:1}.governance-metrics article{padding:22px;border-right:1px solid rgba(255,255,255,.13);border-bottom:1px solid rgba(255,255,255,.13);display:grid;grid-template-columns:1fr auto;align-items:end}.governance-metrics article:nth-child(even){border-right:0}.governance-metrics article:nth-child(n+3){border-bottom:0}.governance-metrics span{color:#8fa097;font-size:13px}.governance-metrics strong{font:500 34px/1 sans-serif}.governance-metrics small{grid-column:1/-1;color:#aebbb5;margin-top:12px}.governance-metrics .alert strong{color:#d8b36a}.governance-intro>.section-no{color:#f09a90}.governance-tabs{display:flex;border-bottom:1px solid #d8d3c8;margin:26px 0 0}.governance-tabs button{border:0;background:none;padding:18px 26px;color:#625f58;position:relative;cursor:pointer;font-size:14px}.governance-tabs button.active{color:#17231e;font-weight:700}.governance-tabs button i{position:absolute;height:2px;left:50%;right:50%;bottom:-1px;background:#a33c32;transition:.2s}.governance-tabs button.active i{left:20px;right:20px}.governance-panel{padding-top:32px}.panel-heading{display:flex;justify-content:space-between;gap:28px;align-items:flex-end;margin-bottom:24px}.panel-heading h3{font:500 28px/1.2 "Noto Sans SC",sans-serif;margin:9px 0}.panel-heading p{color:#625f58;margin:0;max-width:720px;line-height:1.7}.model-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.model-card{background:#fff;border:1px solid #ded9ce;padding:23px;min-height:280px;display:flex;flex-direction:column}.model-card header,.config-ledger article header,.document-type-grid article>header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.model-card small,.config-ledger small,.document-type-grid small{color:#908b82}.model-card h4,.config-ledger h4,.document-type-grid h4{font:600 18px/1.3 "Noto Sans SC",sans-serif;margin:5px 0}.model-id{font-family:ui-monospace,monospace;font-size:14px;color:#a33c32;border-bottom:1px solid #ece8df;padding-bottom:18px}.model-card dl{display:grid;gap:10px;margin:4px 0 18px}.model-card dl div{display:flex;justify-content:space-between;gap:12px}.model-card dt,.document-type-grid dt{font-size:14px;color:#908b82}.model-card dd{font-size:14px;margin:0;text-align:right}.model-card footer{border-top:1px solid #ece8df;padding-top:13px;margin-top:auto;display:grid;gap:7px;font-size:13px;color:#908b82;word-break:break-all}.status-chip{font-size:13px;border:1px solid;padding:4px 9px;white-space:nowrap}.status-chip[data-tone=success]{color:#3e7159;border-color:#9ab6a8;background:#edf5f0}.status-chip[data-tone=warning]{color:#936b1f;border-color:#d7c18d;background:#fbf5e7}.status-chip[data-tone=muted]{color:#777;border-color:#ccc;background:#f3f2ef}.status-chip[data-tone=draft]{color:#7b5a55;border-color:#d5c0bb;background:#faf1ef}.config-ledger{border-top:1px solid #ccc6ba}.config-ledger>article{display:grid;grid-template-columns:70px 1fr 100px;gap:22px;padding:24px 0;border-bottom:1px solid #ded9ce}.ledger-index{font:500 25px/1 sans-serif;color:#b8b2a8}.ledger-main p{color:#595751;line-height:1.65;margin:10px 0}.ledger-main footer{display:flex;gap:22px;color:#908b82;font-size:13px}.ledger-main footer time{margin-left:auto}.ledger-actions{display:flex;gap:7px;align-items:center;justify-content:flex-end}.ledger-actions button,.audit-filters button{border:1px solid #bdb7ab;background:#fff;padding:8px 11px;cursor:pointer;color:#56534c}.ledger-actions button.emphasis{background:#17231e;color:#fff;border-color:#17231e}.document-type-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.document-type-grid>article{background:#fff;border:1px solid #ded9ce;padding:24px}.type-monogram{width:45px;height:45px;background:#a33c32;color:#fff;display:grid;place-items:center;font:500 22px sans-serif;margin-right:12px}.document-type-grid article>header>div:nth-child(2){margin-right:auto}.document-type-grid article>p{color:#77736b;min-height:43px;line-height:1.6}.document-type-grid dl{display:grid;gap:13px;border-top:1px solid #ece8df;border-bottom:1px solid #ece8df;padding:16px 0}.document-type-grid dl div{display:grid;grid-template-columns:80px 1fr}.document-type-grid dd{margin:0;font-size:14px;line-height:1.6}.document-type-grid footer{display:flex;justify-content:space-between;align-items:center;padding-top:15px;color:#908b82;font-size:13px}.audit-filters{display:flex;gap:8px}.audit-filters input{border:1px solid #cfc9bd;background:#fff;padding:10px 12px;width:150px}.audit-ledger{border-top:2px solid #17231e}.audit-head,.audit-ledger article{display:grid;grid-template-columns:190px 1fr 190px 130px;gap:24px}.audit-head{padding:10px 16px;background:#ece8df;color:#77736b;font-size:13px;letter-spacing:1px}.audit-ledger article{padding:18px 16px;border-bottom:1px solid #ded9ce;align-items:start}.audit-ledger article>div{display:grid;gap:5px}.audit-ledger time,.audit-ledger small{font-size:13px;color:#908b82}.audit-ledger b{font-size:14px;color:#a33c32}.audit-ledger strong{font-size:14px}.audit-ledger p{margin:0;color:#595751;font-size:14px}.level-mark{font-size:13px;width:max-content;border-bottom:2px solid #d8b36a;padding-bottom:3px}.governance-empty{grid-column:1/-1;padding:48px;text-align:center;color:#66635c;border:1px dashed #cfc9bd;background:rgba(255,255,255,.45)}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:17px}.form-grid label{display:grid;gap:7px;font-size:14px;color:#69665f}.form-grid .span-2{grid-column:1/-1}.form-grid input,.form-grid select,.form-grid textarea{border:1px solid #cbc5b9;background:#fbfaf7;padding:10px 12px;color:#272d29;font:inherit;resize:vertical}.form-grid input:focus,.form-grid select:focus,.form-grid textarea:focus{outline:2px solid rgba(163,60,50,.16);border-color:#a33c32}@media(max-width:1100px){.governance-hero{grid-template-columns:1fr}.model-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.governance-console{padding:20px}.governance-hero{padding:26px}.model-grid,.document-type-grid{grid-template-columns:1fr}.panel-heading{align-items:flex-start;flex-direction:column}.config-ledger>article{grid-template-columns:45px 1fr}.ledger-actions{grid-column:2;justify-content:flex-start}.audit-head{display:none}.audit-ledger article{grid-template-columns:1fr 1fr}.form-grid{grid-template-columns:1fr}.form-grid .span-2{grid-column:auto}}
</style>
