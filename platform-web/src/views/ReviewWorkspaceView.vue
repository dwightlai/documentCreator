<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { listWritingTasks, type WritingTask } from '@/api/writing'
import {
  batchProcessIssues, downloadReviewReport, generateReviewReport, initiateReview, listReviewPlans, type ReviewPlan,
  listReviewIssues, listReviewTasks, processIssue, reReview, type ReviewAction,
  type ReviewIssue, type ReviewTask,
} from '@/api/review'

const writingTasks = ref<WritingTask[]>([])
const route = useRoute()
const tasks = ref<ReviewTask[]>([])
const issues = ref<ReviewIssue[]>([])
const selectedId = ref('')
const selectedWritingId = ref('')
const loading = ref(false)
const selectedIssueNumbers = ref<string[]>([])
const plans = ref<ReviewPlan[]>([])
const selectedPlanId = ref('')
const selectedPlan = computed(() => plans.value.find(plan => plan.id === selectedPlanId.value))
const selected = computed(() => tasks.value.find(item => item.id === selectedId.value))
const eligibleWritingTasks = computed(() => writingTasks.value.filter(item => item.currentDocumentId))
const pending = computed(() => issues.value.filter(item => item.processingStatus === '待处理').length)

const LEVEL_LABELS: Record<string, string> = { L1: '文字', L2: '规范用语', L3: '公文规则', L4: '格式版式', L5: '事实数据', L6: '逻辑语义', L7: '内容安全' }
const activeLevel = ref('')
const levelOf = (issue: ReviewIssue) => issue.detectionMethod?.match(/L\d/)?.[0] ?? ''
const levelTabs = computed(() => Object.keys(LEVEL_LABELS)
  .map(level => ({ level, label: LEVEL_LABELS[level], count: issues.value.filter(issue => levelOf(issue) === level).length }))
  .filter(tab => tab.count > 0))
const visibleIssues = computed(() => activeLevel.value ? issues.value.filter(issue => levelOf(issue) === activeLevel.value) : issues.value)
const isModelFinding = (issue: ReviewIssue) => issue.detectionMethod?.startsWith('模型')
function pendingGroup(issue: ReviewIssue) {
  if (!issue.mergedGroupId) return []
  return issues.value.filter(item => item.mergedGroupId === issue.mergedGroupId && item.processingStatus === '待处理')
}

async function acceptGroup(issue: ReviewIssue) {
  if (!selected.value) return
  const group = pendingGroup(issue)
  try {
    await ElMessageBox.confirm(`将对同组 ${group.length} 处“${issue.originalText}”统一采纳为“${issue.suggestedFix}”。`, '采纳同组问题')
    const changed = await batchProcessIssues(selected.value.id, 'accept', group.map(item => item.issueNumber))
    const changedById = new Map(changed.map(item => [item.id, item]))
    issues.value = issues.value.map(item => changedById.get(item.id) ?? item)
    ElMessage.success(`已采纳同组 ${changed.length} 项`)
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '同组采纳失败')
  }
}

async function load() {
  loading.value = true
  try {
    const [writingPage, reviewTasks, reviewPlans] = await Promise.all([listWritingTasks(), listReviewTasks(), listReviewPlans()])
    plans.value = reviewPlans
    selectedPlanId.value ||= reviewPlans.find(plan => plan.planCode === 'OFFICIAL')?.id ?? reviewPlans.at(0)?.id ?? ''
    writingTasks.value = writingPage.items
    tasks.value = reviewTasks
    const requested = typeof route.query.taskId === 'string' ? route.query.taskId : ''
    selectedId.value ||= reviewTasks.some((item) => item.id === requested) ? requested : reviewTasks.at(0)?.id ?? ''
    selectedWritingId.value ||= eligibleWritingTasks.value.at(0)?.id ?? ''
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '审校任务加载失败') }
  finally { loading.value = false }
}

async function startReview() {
  if (!selectedWritingId.value) { ElMessage.warning('请先在智能写作中生成文稿正文'); return }
  loading.value = true
  try {
    const task = await initiateReview(selectedWritingId.value, selectedPlanId.value || undefined)
    tasks.value.unshift(task); selectedId.value = task.id
    ElMessage.success(`审校完成，发现 ${task.totalIssues} 项问题`)
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '审校发起失败') }
  finally { loading.value = false }
}

async function requestNote(issue: ReviewIssue, action: ReviewAction) {
  if (action === 'accept') return undefined
  const actionLabel = action === 'ignore' ? '忽略' : action === 'modify' ? '修改完成' : '转人工'
  const required = action === 'ignore' && issue.riskLevel === '严重'
  const result = await ElMessageBox.prompt(
    required ? '严重问题必须填写处理说明。' : `可填写${actionLabel}说明，便于审计追溯。`,
    actionLabel,
    { inputType: 'textarea', inputPlaceholder: required ? '请输入处理依据（必填）' : '处理说明（选填）',
      inputValidator: value => !required || Boolean(value?.trim()) || '严重问题必须填写处理说明' },
  )
  return result.value?.trim()
}

async function handle(issue: ReviewIssue, action: ReviewAction) {
  if (!selected.value) return
  try {
    const note = await requestNote(issue, action)
    const changed = await processIssue(selected.value.id, issue.issueNumber, action, note)
    issues.value = issues.value.map(item => item.id === changed.id ? changed : item)
    selectedIssueNumbers.value = selectedIssueNumbers.value.filter(number => number !== issue.issueNumber)
    ElMessage.success(action === 'accept' ? '建议已采纳并生成文稿新版本' : '处理结果已留痕')
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '问题处理失败')
  }
}

async function handleBatch(action: 'accept' | 'ignore') {
  if (!selected.value || !selectedIssueNumbers.value.length) { ElMessage.warning('请先勾选待处理问题'); return }
  try {
    let note: string | undefined
    if (action === 'accept') {
      const manual = issues.value.filter(issue => selectedIssueNumbers.value.includes(issue.issueNumber) && !issue.autoFixAllowed)
      if (manual.length) { ElMessage.warning(`已勾选的 ${manual.length} 项需人工修改，不能批量采纳（事实、逻辑、内容安全类问题禁止自动修改）`); return }
    }
    if (action === 'ignore') {
      const invalid = issues.value.some(issue => selectedIssueNumbers.value.includes(issue.issueNumber) && !['提示', '一般'].includes(issue.riskLevel))
      if (invalid) { ElMessage.warning('批量忽略仅允许提示或一般风险问题'); return }
      const result = await ElMessageBox.prompt('批量处理说明会写入每条问题的审计记录。', '批量忽略', { inputType: 'textarea' })
      note = result.value?.trim()
    }
    const changed = await batchProcessIssues(selected.value.id, action, selectedIssueNumbers.value, note)
    const changedById = new Map(changed.map(issue => [issue.id, issue]))
    issues.value = issues.value.map(issue => changedById.get(issue.id) ?? issue)
    selectedIssueNumbers.value = []
    ElMessage.success(`已批量${action === 'accept' ? '采纳' : '忽略'} ${changed.length} 项`)
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '批量处理失败')
  }
}

async function rerunReview() {
  if (!selected.value) return
  loading.value = true
  try {
    const task = await reReview(selected.value.id)
    tasks.value.unshift(task); selectedId.value = task.id
    ElMessage.success('已基于当前文稿生成新的审校任务，历史处理记录保留')
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '重新审校失败') }
  finally { loading.value = false }
}

async function exportReport(format: 'PDF' | 'DOCX') {
  if (!selected.value) return
  try {
    const report = await generateReviewReport(selected.value.id)
    await downloadReviewReport(selected.value.id, report.reportVersion, format)
    ElMessage.success(`审校报告 V${report.reportVersion} 已生成`)
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '报告导出失败') }
}

watch(selectedId, async (id) => { selectedIssueNumbers.value = []; issues.value = id ? await listReviewIssues(id) : [] })
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>政务智能审校</template>

    <section class="review-workspace">
      <aside class="review-sidebar">
        <div class="panel-head"><div><span class="section-no">01</span><h2>审校任务</h2></div></div>
        <div class="launch-review">
          <label>选择已生成文稿</label>
          <el-select v-model="selectedWritingId" aria-label="选择已生成文稿" placeholder="暂无可审文稿">
            <el-option v-for="item in eligibleWritingTasks" :key="item.id" :label="item.taskName" :value="item.id" />
          </el-select>
          <label>审校方案</label>
          <el-select v-model="selectedPlanId" aria-label="审校方案" placeholder="默认方案">
            <el-option v-for="plan in plans" :key="plan.id" :label="plan.planName" :value="plan.id" />
          </el-select>
          <small v-if="selectedPlan" class="plan-levels">{{ selectedPlan.applicableScenarios }} · {{ selectedPlan.reviewLevels.join(' / ') }}</small>
          <button class="primary-button" type="button" :disabled="loading" @click="startReview">{{ loading ? '正在检查…' : '发起智能审校' }}</button>
        </div>
        <button v-for="task in tasks" :key="task.id" type="button" class="review-task" :class="{ active: task.id === selectedId }" @click="selectedId = task.id">
          <span>{{ task.documentTypeCode }}</span><div><strong>{{ writingTasks.find(item => item.id === task.writingTaskId)?.taskName || '审校任务' }}</strong><small>{{ task.status }} · {{ task.totalIssues }} 项</small></div>
        </button>
      </aside>
      <div class="issue-stage">
        <template v-if="selected">
          <header class="issue-summary">
            <div><span class="section-no">REVIEW RESULT</span><h2>审校问题清单</h2><p>{{ selected.status }}，共发现 {{ selected.totalIssues }} 项，尚有 {{ pending }} 项待处理。</p></div>
            <div class="score-ring"><strong>{{ Math.max(0, 100 - selected.totalIssues * 4) }}</strong><span>质量评分</span></div>
          </header>
          <div class="review-toolbar">
            <span>已选 {{ selectedIssueNumbers.length }} 项</span>
            <button type="button" @click="handleBatch('accept')">批量采纳</button>
            <button type="button" @click="handleBatch('ignore')">批量忽略低风险</button>
            <button type="button" @click="rerunReview">重新审校</button>
            <button type="button" @click="exportReport('PDF')">导出 PDF 报告</button>
            <button type="button" @click="exportReport('DOCX')">导出 DOCX 报告</button>
          </div>
          <div class="level-tabs" role="tablist"><button type="button" role="tab" :class="{ active: !activeLevel }" :aria-selected="!activeLevel" @click="activeLevel = ''">全部 {{ issues.length }}</button><button v-for="tab in levelTabs" :key="tab.level" type="button" role="tab" :class="{ active: activeLevel === tab.level }" :aria-selected="activeLevel === tab.level" @click="activeLevel = tab.level">{{ tab.level }} {{ tab.label }} {{ tab.count }}</button></div>
          <div v-if="!issues.length" class="review-clean"><span>✓</span><h3>未发现内置规则问题</h3><p>仍建议由业务人员完成事实与政策依据复核。</p></div>
          <article v-for="issue in visibleIssues" :key="issue.id" class="issue-card" :class="issue.processingStatus !== '待处理' ? 'resolved' : ''">
            <label v-if="issue.processingStatus === '待处理'" class="issue-select" :aria-label="`选择问题 ${issue.issueNumber}`">
              <input v-model="selectedIssueNumbers" type="checkbox" :value="issue.issueNumber" />
            </label>
            <div class="risk-flag" :data-risk="issue.riskLevel">{{ issue.riskLevel }}</div>
            <div class="issue-body">
              <header><span>{{ issue.issueNumber }} · {{ issue.issueCategory }}<b v-if="levelOf(issue)" class="level-chip">{{ levelOf(issue) }}</b><b v-if="isModelFinding(issue)" class="level-chip model">模型提出</b><b v-if="pendingGroup(issue).length > 1" class="level-chip group">同组 {{ pendingGroup(issue).length }} 处</b></span><small>第 {{ issue.pageNumber }} 页 / 第 {{ issue.paragraphNumber }} 段 / 字符 {{ issue.charPositionStart }}—{{ issue.charPositionEnd }} · 置信度 {{ Math.round(issue.confidenceScore * 100) }}%</small></header>
              <div v-if="issue.suggestedFix" class="text-change"><del>{{ issue.originalText }}</del><span>→</span><ins>{{ issue.suggestedFix }}</ins></div><div v-else class="text-change manual"><mark>{{ issue.originalText }}</mark><small>需人工修改</small></div>
              <p>{{ issue.issueExplanation }}</p><small class="basis">依据：{{ issue.ruleBasis }}</small>
            </div>
            <div v-if="issue.processingStatus === '待处理'" class="issue-actions"><button v-if="issue.autoFixAllowed && pendingGroup(issue).length > 1" type="button" @click="acceptGroup(issue)">采纳同组</button><button v-if="issue.autoFixAllowed" type="button" @click="handle(issue, 'accept')">采纳</button><button type="button" @click="handle(issue, 'ignore')">忽略</button><button type="button" @click="handle(issue, 'modify')">已修改</button><button type="button" @click="handle(issue, 'transfer')">转人工</button></div>
            <div v-else class="processed-mark">{{ issue.processingStatus }}<small v-if="issue.processingNote">{{ issue.processingNote }}</small></div>
          </article>
        </template>
        <div v-else class="canvas-empty"><span>审</span><h2>选择文稿并发起审校</h2><p>审校结论按风险、层级和规则依据逐项呈现。</p></div>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
.plan-levels{margin-top:-4px;color:#6b7280;font-size:12px}
.level-tabs button{padding:11px 16px;color:#646a73;background:transparent;border:0;border-bottom:2px solid transparent;font-size:14px;cursor:pointer}
.level-tabs button.active{color:var(--red);border-bottom-color:var(--red);font-weight:600}
.level-chip{margin-left:8px;padding:1px 8px;border-radius:999px;background:#eef0f4;color:#4b5563;font-size:12px;font-weight:500}
.level-chip.model{background:#efe9ff;color:#5b3fc4}
.level-chip.group{background:#e8f5ee;color:#18794e}
.text-change.manual mark{padding:3px 6px;border-radius:4px;background:#fff4e5;color:#9a5b13}
.text-change.manual small{color:#6b7280;font-size:13px;font-weight:400}
</style>
