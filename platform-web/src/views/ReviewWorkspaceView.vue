<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, type CSSProperties } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { listWritingTasks, type WritingTask } from '@/api/writing'
import { listOutputTemplates, type OutputTemplate } from '@/api/templates'
import {
  batchProcessIssues, deleteReviewTask, downloadEditedReviewDocument, downloadReviewReport, generateReviewReport, getReviewContent, initiateReview, reUploadReviewDocument, saveReviewContent, uploadReviewDocument, type ReviewContent, type ReviewContentBlock,
  detectReviewDocumentType, listReviewIssues, listReviewTasks, processIssue, reReview, type ReviewAction,
  type ReviewIssue, type ReviewTask,
} from '@/api/review'

const writingTasks = ref<WritingTask[]>([])
const route = useRoute()
const tasks = ref<ReviewTask[]>([])
const issues = ref<ReviewIssue[]>([])
const content = ref<ReviewContent>()
const activeIssueNumber = ref('')
const editingAnchor = ref('')
const editingText = ref('')
const editingHint = ref('')
const editingCells = ref<string[]>([])
const savingContent = ref(false)
const selectedId = ref('')
const selectedWritingId = ref('')
const launchMode = ref<'writing' | 'upload'>('writing')
const uploadFile = ref<File>()
const uploadType = ref('报告')
const outputTemplateId = ref('')
const outputTemplates = ref<OutputTemplate[]>([])
const replaceFileInput = ref<HTMLInputElement>()
const loading = ref(false)
const selectedIssueNumbers = ref<string[]>([])
const BUSINESS_CATEGORIES = ['政治与重要表述', '政策法规与事实依据', '公文业务规范', '内容与逻辑', '语言文字', '格式与版式']
const selected = computed(() => tasks.value.find(item => item.id === selectedId.value))
const reviewGroups = computed(() => {
  const groups = new Map<string, ReviewTask[]>()
  for (const task of [...tasks.value].sort((a, b) => b.createdAt.localeCompare(a.createdAt))) {
    const key = task.writingTaskId || task.businessId || task.documentId || task.id
    const entries = groups.get(key) ?? []
    entries.push(task)
    groups.set(key, entries)
  }
  return [...groups.entries()].map(([key, entries]) => ({ key, latest: entries[0]!, entries }))
})
const selectedGroup = computed(() => reviewGroups.value.find(group => group.entries.some(item => item.id === selectedId.value)))
const eligibleWritingTasks = computed(() => writingTasks.value.filter(item => item.currentDocumentId))
const eligibleTemplates = computed(() => outputTemplates.value.filter(item => item.lifecycleStatus === '生效' && (!uploadType.value || item.documentTypeCode === uploadType.value)))
const pending = computed(() => issues.value.filter(item => item.processingStatus === '待处理').length)

const activeLevel = ref('')
function sourceOf(issue: ReviewIssue) {
  const method = issue.detectionMethod ?? ''
  if (method.startsWith('配置规则:')) return `审校规则 · ${method.slice('配置规则:'.length).replace(/-L\d$/, '')}`
  if (method.startsWith('标准词库:')) return `标准词库 · ${method.slice('标准词库:'.length).replace(/-L\d$/, '')}`
  if (method.startsWith('标准词库-')) return '标准词库'
  if (method.startsWith('内置检查-')) return '系统内置检查'
  if (method.startsWith('格式解析-')) return '版式解析'
  if (method.startsWith('模型-')) return '模型辅助'
  return '旧记录未保存具体来源'
}
const hasLegacySource = computed(() => issues.value.some(issue => issue.detectionMethod?.startsWith('规则-')))
const levelTabs = computed(() => BUSINESS_CATEGORIES
  .map(level => ({ level, label: level, count: issues.value.filter(issue => issue.issueCategory === level).length }))
  .filter(tab => tab.count > 0))
const visibleIssues = computed(() => activeLevel.value ? issues.value.filter(issue => issue.issueCategory === activeLevel.value) : issues.value)
const activeIssue = computed(() => issues.value.find(item => item.issueNumber === activeIssueNumber.value))
function locate(issue: ReviewIssue, blocks = content.value?.blocks) {
  if (!blocks || issue.issueCategory === '格式与版式' || issue.detectionMethod?.startsWith('格式解析-')) return null
  const candidates = issue.paragraphId
    ? blocks.filter(block => block.kind === 'paragraph' && block.paragraphNumber === issue.paragraphNumber)
    : blocks.filter(block => block.paragraphNumber === issue.paragraphNumber || (block.kind === 'title' && block.originalText.includes(issue.originalText || '\u0000')))
  for (const block of candidates) {
    const original = issue.originalText || ''
    if (!original) continue
    let start = issue.charPositionStart
    const end = issue.charPositionEnd
    if (start < 0 || end < start || block.originalText.slice(start, end) !== original) {
      start = block.originalText.indexOf(original)
      if (start < 0 || block.originalText.indexOf(original, start + 1) >= 0) continue
    }
    return { anchor: block.anchor, start, end: start + original.length }
  }
  return null
}
const activeLocator = computed(() => activeIssue.value ? locate(activeIssue.value) : null)
function highlightParts(block: ReviewContentBlock) {
  const locator = activeLocator.value
  const value = block.currentText
  if (!locator || locator.anchor !== block.anchor || value.slice(locator.start, locator.end) !== activeIssue.value?.originalText)
    return { before: value, match: '', after: '' }
  return { before: value.slice(0, locator.start), match: value.slice(locator.start, locator.end), after: value.slice(locator.end) }
}
function originalParagraphStyle(block:ReviewContentBlock):CSSProperties | undefined {
  if (content.value?.sourceSystem !== 'upload' || !block.style) return undefined
  const source=block.style
  const alignment:Record<string,CSSProperties['textAlign']>={LEFT:'left',CENTER:'center',RIGHT:'right',BOTH:'justify',DISTRIBUTE:'justify'}
  const twips=(value:number) => `${Math.max(-600,Math.min(1200,Number(value)||0))/15}px`
  const style:CSSProperties={textAlign:alignment[source.alignment]||'left',textIndent:twips(source.firstLineTwips),paddingLeft:twips(source.leftTwips),fontWeight:source.bold?'700':'400',fontStyle:source.italic?'italic':'normal'}
  if (source.fontPt>=8 && source.fontPt<=48) style.fontSize=`${source.fontPt}pt`
  if (source.fontFamily && source.fontFamily.length<=80) style.fontFamily=source.fontFamily
  if (source.color && /^[0-9a-fA-F]{6}$/.test(source.color)) style.color=`#${source.color}`
  if (source.beforeTwips>0) style.marginTop=twips(source.beforeTwips)
  if (source.afterTwips>0) style.marginBottom=twips(source.afterTwips)
  return style
}
function suggestionOf(issue: ReviewIssue) {
  if (issue.suggestedFix?.trim()) return issue.suggestedFix.trim()
  const text = issue.issueExplanation || ''
  const mark = '建议修改方向：'
  const at = text.indexOf(mark)
  if (at < 0) return ''
  return text.slice(at + mark.length).replace(/。?\s*（模型提出[\s\S]*$/, '').trim()
}
async function focusIssue(issue: ReviewIssue, edit = false) {
  activeIssueNumber.value = issue.issueNumber
  const target = locate(issue)
  if (!target) { ElMessage.info('此问题涉及版式或缺少可靠的文字定位，请对照整篇原文人工核查'); return }
  const block = content.value?.blocks.find(item => item.anchor === target.anchor)
  if (edit && block) {
    beginEdit(block)
    const advice = suggestionOf(issue)
    const canReplace = Boolean(issue.suggestedFix?.trim() && issue.originalText && editingText.value.includes(issue.originalText))
    if (canReplace) editingText.value = editingText.value.replace(issue.originalText, issue.suggestedFix)
    editingHint.value = canReplace ? '已按建议填入，确认后点保存新版本' : advice ? `建议：${advice}` : ''
  }
  await nextTick()
  document.getElementById(`review-block-${target.anchor}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
function beginEdit(block: ReviewContentBlock) {
  if (block.locked) { ElMessage.warning('这一段已锁定，无法修改'); return }
  editingAnchor.value = block.anchor
  editingText.value = block.currentText
  editingHint.value = ''
  editingCells.value = block.kind === 'tableRow' ? block.currentText.split('\t') : []
}
async function saveEdit() {
  if (!selected.value || !content.value || !editingAnchor.value) return
  savingContent.value = true
  try {
    const value = editingCells.value.length ? editingCells.value.join('\t') : editingText.value
    content.value = await saveReviewContent(selected.value.id, editingAnchor.value, value, content.value.currentVersion)
    editingAnchor.value = ''
    ElMessage.success('已保存为新版本。请重新审校，确认问题是否消失')
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '原文保存失败') }
  finally { savingContent.value = false }
}
async function downloadEdited() {
  if (!selected.value) return
  try { await downloadEditedReviewDocument(selected.value.id, selected.value.sourceFileName || '审校文稿.docx') }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '下载失败') }
}
async function reloadContent() {
  if (!selected.value) return
  try { content.value = await getReviewContent(selected.value.id) }
  catch (reason) { content.value = undefined; ElMessage.error(reason instanceof Error ? reason.message : '原文加载失败') }
}
function pendingGroup(issue: ReviewIssue) {
  if (!issue.mergedGroupId) return []
  return issues.value.filter(item => item.mergedGroupId === issue.mergedGroupId && item.processingStatus === '待处理')
}
function reviewLabel(task: ReviewTask) {
  const version = task.documentVersion ? `文稿 V${task.documentVersion} · ` : ''
  return `${version}${new Date(task.createdAt).toLocaleString('zh-CN')} · ${task.totalIssues} 项`
}
async function chooseUploadFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  uploadFile.value = file
  outputTemplateId.value = ''
  uploadType.value = ''
  if (!file) return
  try {
    uploadType.value = (await detectReviewDocumentType(file)) || ''
  } catch { uploadType.value = '' }
}

async function acceptGroup(issue: ReviewIssue) {
  if (!selected.value) return
  const group = pendingGroup(issue)
  try {
    await ElMessageBox.confirm(`将对同组 ${group.length} 处“${issue.originalText}”统一采纳为“${issue.suggestedFix}”。`, '采纳同组问题')
    const changed = await batchProcessIssues(selected.value.id, 'accept', group.map(item => item.issueNumber))
    const changedById = new Map(changed.map(item => [item.id, item]))
    issues.value = issues.value.map(item => changedById.get(item.id) ?? item)
    await reloadContent()
    ElMessage.success(`已采纳同组 ${changed.length} 项`)
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '同组采纳失败')
  }
}

async function load() {
  loading.value = true
  try {
    const [writingPage, reviewTasks, templates] = await Promise.all([listWritingTasks(), listReviewTasks(), listOutputTemplates()])
    writingTasks.value = writingPage.items
    outputTemplates.value = templates
    tasks.value = reviewTasks
    const requested = typeof route.query.taskId === 'string' ? route.query.taskId : ''
    if (!reviewTasks.some(item => item.id === selectedId.value)) selectedId.value = reviewTasks.some(item => item.id === requested) ? requested : reviewTasks.at(0)?.id ?? ''
    selectedWritingId.value ||= eligibleWritingTasks.value.at(0)?.id ?? ''
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '审校任务加载失败') }
  finally { loading.value = false }
}

async function removeReviewDocument() {
  const group = selectedGroup.value
  if (!group) return
  const title = group.latest.sourceFileName || writingTasks.value.find(item => item.id === group.latest.writingTaskId)?.taskName || '这份公文'
  try {
    await ElMessageBox.confirm(`确定删除“${title}”的 ${group.entries.length} 次审校记录？`, '删除审校公文',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    loading.value = true
    for (const task of group.entries) await deleteReviewTask(task.id)
    selectedId.value = ''
    issues.value = []
    await load()
    ElMessage.success('审校记录已删除')
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') {
      await load()
      ElMessage.error(reason instanceof Error ? reason.message : '删除失败')
    }
  } finally { loading.value = false }
}

async function startUploadReview() {
  if (!uploadFile.value) { ElMessage.warning('请选择本地 DOCX 文件'); return }
  if (!uploadFile.value.name.toLowerCase().endsWith('.docx')) { ElMessage.warning('当前仅支持 DOCX 文件'); return }
  loading.value = true
  try {
    const task = await uploadReviewDocument(uploadFile.value, outputTemplateId.value || undefined)
    tasks.value.unshift(task)
    selectedId.value = task.id
    uploadFile.value = undefined
    ElMessage.success(`上传审校完成，发现 ${task.totalIssues} 项问题`)
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '上传审校失败') }
  finally { loading.value = false }
}

async function replaceUploadFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !selected.value) return
  loading.value = true
  try {
    const task = await reUploadReviewDocument(selected.value.id, file)
    tasks.value.unshift(task)
    selectedId.value = task.id
    ElMessage.success(`新文件审校完成，发现 ${task.totalIssues} 项问题`)
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '更新文件审校失败') }
  finally { loading.value = false }
}

async function startReview() {
  if (!selectedWritingId.value) { ElMessage.warning('请先在智能写作中生成文稿正文'); return }
  loading.value = true
  try {
    const task = await initiateReview(selectedWritingId.value)
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
    if (action === 'accept') await reloadContent()
    selectedIssueNumbers.value = selectedIssueNumbers.value.filter(number => number !== issue.issueNumber)
    ElMessage.success(action === 'accept' ? '建议已采纳并生成文稿新版本' : '处理结果已留痕')
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '问题处理失败')
  }
}

async function handleBatch(action: 'accept' | 'ignore') {
  if (!selected.value || !selectedIssueNumbers.value.length) { ElMessage.warning('请先勾选待处理问题'); return }
  if (action === 'accept' && content.value?.needsReview) { ElMessage.warning('原文已修改，请先重新审校'); return }
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
    if (action === 'accept') await reloadContent()
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

watch(selectedId, async (id) => {
  selectedIssueNumbers.value = []; activeIssueNumber.value = ''; editingAnchor.value = ''; content.value = undefined
  if (!id) { issues.value = []; return }
  try {
    const [nextIssues, nextContent] = await Promise.all([listReviewIssues(id), getReviewContent(id)])
    if (selectedId.value === id) { issues.value = nextIssues; content.value = nextContent }
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '审校内容加载失败') }
})
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>政务智能审校</template>

    <section class="review-workspace">
      <aside class="review-sidebar">
        <div class="panel-head"><div><h2>审校任务</h2></div></div>
        <div class="launch-review">
          <div class="review-launch-tabs"><button type="button" :class="{active:launchMode==='writing'}" @click="launchMode='writing'">已有文稿</button><button type="button" :class="{active:launchMode==='upload'}" @click="launchMode='upload'">上传本地文件</button></div>
          <template v-if="launchMode === 'writing'"><label>选择已生成文稿</label>
            <el-select v-model="selectedWritingId" aria-label="选择已生成文稿" placeholder="暂无可审文稿">
              <el-option v-for="item in eligibleWritingTasks" :key="item.id" :label="item.taskName" :value="item.id" />
            </el-select>
          </template>
          <template v-else>
            <label>选择 DOCX 文件<input class="review-upload-input" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" @change="chooseUploadFile"></label>
            <small class="review-file-name">{{ uploadFile?.name || '可上传本地起草的 Word 文档，最大 50MB' }}</small>
          </template>
          <template v-if="launchMode === 'upload'"><label>输出模板（选填，用于对照版式）</label><el-select v-model="outputTemplateId" aria-label="输出模板" placeholder="不选择模板"><el-option label="不选择模板" value="" /><el-option v-for="item in eligibleTemplates" :key="item.id" :label="item.templateName" :value="item.id" /></el-select></template>
          <button class="primary-button" type="button" :disabled="loading" @click="launchMode === 'upload' ? startUploadReview() : startReview()">{{ loading ? '正在检查…' : launchMode === 'upload' ? '上传并审校' : '发起智能审校' }}</button>
        </div>
        <button v-for="group in reviewGroups" :key="group.key" type="button" class="review-task" :class="{ active: selectedGroup?.key === group.key }" @click="selectedId = group.latest.id">
          <span>{{ group.latest.documentTypeCode }}</span><div><strong>{{ group.latest.sourceFileName || writingTasks.find(item => item.id === group.latest.writingTaskId)?.taskName || '审校任务' }}</strong><small>{{ group.latest.sourceSystem === 'upload' ? '本地文件 · ' : '' }}最近审校：{{ group.latest.totalIssues }} 项 · 共 {{ group.entries.length }} 次</small></div>
        </button>
      </aside>
      <div class="issue-stage">
        <template v-if="selected">
          <header class="issue-summary">
            <div><h2>审校问题清单</h2><p>{{ selected.sourceFileName || writingTasks.find(item => item.id === selected?.writingTaskId)?.taskName }} · {{ selected.status }}，共发现 {{ selected.totalIssues }} 项，尚有 {{ pending }} 项待处理。</p><label v-if="selectedGroup && selectedGroup.entries.length > 1" class="review-history">审校历史 <select v-model="selectedId" aria-label="选择审校记录"><option v-for="item in selectedGroup.entries" :key="item.id" :value="item.id">{{ reviewLabel(item) }}</option></select></label><p v-else class="review-run-meta">{{ reviewLabel(selected) }}</p></div>
            <div class="score-ring"><strong>{{ Math.max(0, 100 - selected.totalIssues * 4) }}</strong><span>质量评分</span></div>
          </header>
          <div class="review-toolbar">
            <span>已选 {{ selectedIssueNumbers.length }} 项</span>
            <button v-if="selected.sourceSystem !== 'upload'" type="button" :disabled="content?.needsReview" @click="handleBatch('accept')">批量采纳</button>
            <button type="button" @click="handleBatch('ignore')">批量忽略低风险</button>
            <button type="button" @click="rerunReview">重新审校</button>
            <button v-if="selected.sourceSystem === 'upload' && content?.needsReview" type="button" @click="downloadEdited">下载修改后的 DOCX</button>
            <button v-if="selected.sourceSystem === 'upload'" type="button" @click="replaceFileInput?.click()">更新文件并审校</button><input ref="replaceFileInput" class="review-hidden-file" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" @change="replaceUploadFile">
            <button type="button" :disabled="content?.needsReview" @click="exportReport('PDF')">导出 PDF 报告</button>
            <button type="button" :disabled="content?.needsReview" @click="exportReport('DOCX')">导出 DOCX 报告</button>
            <button type="button" @click="removeReviewDocument">删除这份公文</button>
          </div>
          <div v-if="content?.needsReview" class="review-edit-notice">原文已修改，当前问题清单仍对应上次审校版本。请点击“重新审校”复核；仅在新结果中消失的问题才视为已解决。</div>
          <div class="review-compare">
            <section class="review-document-panel" aria-label="公文原文">
              <header class="review-panel-title"><div><h3>公文原文</h3><small v-if="content">{{ content.sourceSystem === 'upload' && !content.currentVersion ? '上传原件' : `当前版本 V${content.currentVersion}` }} · 点击段落可直接修改</small></div><span v-if="content?.needsReview" class="review-pending-badge">待复核</span></header>
              <div v-if="!content" class="review-document-empty">正在读取原文…</div>
              <div v-else class="review-paper" :class="{'review-paper-writing':content.sourceSystem!=='upload'}">
                <div v-for="block in content.blocks" :id="`review-block-${block.anchor}`" :key="block.anchor" class="review-block" :class="{ 'review-block-active': activeLocator?.anchor === block.anchor, 'review-block-title': block.kind === 'title' && content.sourceSystem !== 'upload', 'review-block-table': block.kind === 'tableRow' }">
                  <template v-if="editingAnchor === block.anchor">
                    <div v-if="block.kind === 'tableRow'" class="review-table-edit"><label v-for="(_, cellIndex) in editingCells" :key="cellIndex">第 {{ cellIndex + 1 }} 列<input v-model="editingCells[cellIndex]" :aria-label="`编辑表格第 ${cellIndex + 1} 列`" /></label></div>
                    <template v-else>
                      <p v-if="editingHint" class="review-advice">{{ editingHint }}</p>
                      <textarea v-model="editingText" class="review-edit-area" :aria-label="`编辑${block.kind === 'title' ? '标题或首段' : '正文段落'}`" rows="4" />
                    </template>
                    <div class="review-edit-actions"><button type="button" :disabled="savingContent" @click="saveEdit">保存新版本</button><button type="button" @click="editingAnchor = ''">取消</button></div>
                  </template>
                  <template v-else>
                    <div class="review-block-line"><span>{{ block.kind === 'title' && content.sourceSystem !== 'upload' ? '标题' : block.kind === 'tableRow' ? `表格 · 第 ${block.paragraphNumber} 段` : `第 ${block.paragraphNumber} 段` }}</span><button type="button" :disabled="block.locked" @click="beginEdit(block)">修改</button></div>
                    <p :style="originalParagraphStyle(block)">{{ highlightParts(block).before }}<mark v-if="highlightParts(block).match">{{ highlightParts(block).match }}</mark>{{ highlightParts(block).after }}</p>
                  </template>
                </div>
              </div>
            </section>
            <section class="review-issues-panel" aria-label="审校问题清单">
              <header class="review-panel-title"><div><h3>问题清单</h3><small>点击问题定位左侧原文</small></div><strong>{{ issues.length }} 项</strong></header>
          <div class="review-filter-help">按问题类型筛选。系统会结合内置检查、已发布的审校规则和标准词库给出结果；每条问题下方标明检查来源。<span v-if="hasLegacySource">旧审校记录未保存具体来源，重新审校后可查看。</span><RouterLink to="/rules">查看规则与词库</RouterLink></div>
          <div class="level-tabs" role="tablist"><button type="button" role="tab" :class="{ active: !activeLevel }" :aria-selected="!activeLevel" @click="activeLevel = ''">全部问题 {{ issues.length }}</button><button v-for="tab in levelTabs" :key="tab.level" type="button" role="tab" :class="{ active: activeLevel === tab.level }" :aria-selected="activeLevel === tab.level" @click="activeLevel = tab.level">{{ tab.label }} {{ tab.count }}</button></div>
          <div v-if="!issues.length" class="review-clean"><span>✓</span><h3>未发现内置规则问题</h3><p>仍建议由业务人员完成事实与政策依据复核。</p></div>
          <article v-for="issue in visibleIssues" :key="issue.id" class="issue-card" :class="{ resolved: issue.processingStatus !== '待处理', 'issue-card-active': activeIssueNumber === issue.issueNumber }" @click="focusIssue(issue)">
            <label v-if="issue.processingStatus === '待处理'" class="issue-select" :aria-label="`选择问题 ${issue.issueNumber}`">
              <input v-model="selectedIssueNumbers" type="checkbox" :value="issue.issueNumber" />
            </label>
            <div class="risk-flag" :data-risk="issue.riskLevel">{{ issue.riskLevel }}</div>
            <div class="issue-body">
              <header><span>{{ issue.issueNumber }} · {{ issue.issueCategory }}<b v-if="pendingGroup(issue).length > 1" class="level-chip group">同组 {{ pendingGroup(issue).length }} 处</b><b v-if="content?.needsReview && locate(issue)" class="level-chip">待复核</b></span><small>{{ locate(issue) ? `第 ${issue.paragraphNumber} 段` : '整篇核查' }} · 置信度 {{ Math.round(issue.confidenceScore * 100) }}%</small></header>
              <div v-if="issue.suggestedFix" class="text-change"><del>{{ issue.originalText }}</del><span>→</span><ins>{{ issue.suggestedFix }}</ins></div><div v-else class="text-change manual"><mark>{{ issue.originalText }}</mark><small>{{ suggestionOf(issue) ? `建议：${suggestionOf(issue)}` : '需人工修改' }}</small></div>
              <p>{{ issue.issueExplanation }}</p><small class="basis">检查来源：{{ sourceOf(issue) }}<template v-if="issue.ruleBasis"> · 依据：{{ issue.ruleBasis }}</template></small>
            </div>
            <div v-if="issue.processingStatus === '待处理'" class="issue-actions" @click.stop><button v-if="selected.sourceSystem !== 'upload' && issue.autoFixAllowed && pendingGroup(issue).length > 1 && !content?.needsReview" type="button" @click="acceptGroup(issue)">采纳同组</button><button v-if="selected.sourceSystem !== 'upload' && issue.autoFixAllowed && !content?.needsReview" type="button" @click="handle(issue, 'accept')">采纳</button><button type="button" @click="handle(issue, 'ignore')">忽略</button><button type="button" @click="focusIssue(issue, true)">定位修改</button><button type="button" @click="handle(issue, 'transfer')">转人工</button></div>
            <div v-else class="processed-mark">{{ issue.processingStatus }}<small v-if="issue.processingNote">{{ issue.processingNote }}</small></div>
          </article>
            </section>
          </div>
        </template>
        <div v-else class="canvas-empty"><span>审</span><h2>选择文稿并发起审校</h2><p>审校结论按风险、层级和规则依据逐项呈现。</p></div>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
.review-launch-tabs{display:grid;grid-template-columns:1fr 1fr;gap:5px}.review-launch-tabs button{padding:7px 3px;border:1px solid #d5d9e0;border-radius:5px;background:#fff;color:#646a73;font-size:12px;cursor:pointer}.review-launch-tabs button.active{border-color:#c8322c;background:#fff4f2;color:#b72e28;font-weight:600}.review-upload-input{display:block;box-sizing:border-box;width:100%;margin-top:6px;font-size:11px}.review-file-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#6b7280;font-size:11px}.review-hidden-file{display:none}
.plan-levels{margin-top:-4px;color:#6b7280;font-size:12px}
.review-filter-help{display:flex;align-items:center;flex-wrap:wrap;gap:4px;padding:12px 16px;color:#626b77;font-size:12px;line-height:1.6}
.review-filter-help a{margin-left:6px;color:var(--red);text-decoration:none;white-space:nowrap}
.review-filter-help a:hover{text-decoration:underline}
.review-plan-label,.review-plan-actions{display:flex;align-items:center;justify-content:space-between;gap:8px}.review-plan-label button,.review-plan-actions button{border:0;background:transparent;color:var(--red);font-size:12px;cursor:pointer}.review-plan-options{display:flex;flex-wrap:wrap;gap:8px}
.level-tabs button{padding:11px 16px;color:#646a73;background:transparent;border:0;border-bottom:2px solid transparent;font-size:14px;cursor:pointer}
.level-tabs button.active{color:var(--red);border-bottom-color:var(--red);font-weight:600}
.level-chip{margin-left:8px;padding:1px 8px;border-radius:999px;background:#eef0f4;color:#4b5563;font-size:12px;font-weight:500}
.level-chip.model{background:#efe9ff;color:#5b3fc4}
.level-chip.group{background:#e8f5ee;color:#18794e}
.text-change.manual mark{padding:3px 6px;border-radius:4px;background:#fff4e5;color:#9a5b13}
.text-change.manual small{color:#6b7280;font-size:13px;font-weight:400}
.issue-stage{min-width:0}
.review-edit-notice{margin:12px 0;padding:10px 14px;border:1px solid #ead4a1;border-radius:8px;background:#fff9ec;color:#785516;font-size:13px}
.review-compare{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(350px,1fr);gap:16px;align-items:start;margin-top:14px}
.review-document-panel,.review-issues-panel{min-width:0;max-height:calc(100vh - 225px);overflow:auto;background:#fff;border:1px solid #dfe3e8;border-radius:10px;box-shadow:0 2px 10px #17212e0d}
.review-panel-title{position:sticky;top:0;z-index:2;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 16px;background:#fff;border-bottom:1px solid #e4e7eb}
.review-panel-title h3{margin:0 0 2px;font-size:16px}.review-panel-title small{color:#737b86;font-size:12px}.review-panel-title strong{color:var(--red)}
.review-pending-badge{padding:3px 8px;border-radius:12px;background:#fff3d9;color:#8b6417;font-size:12px}
.review-document-empty{padding:40px 20px;text-align:center;color:#737b86}
.review-paper{max-width:760px;margin:0 auto;padding:24px 30px 48px;background:#fff}
.review-block{margin:0 0 12px;padding:10px 12px;border:1px solid transparent;border-radius:6px;scroll-margin-top:90px}
.review-block:hover{background:#f8fafc}.review-block-active{border-color:#e5a09a;background:#fff7f5!important}
.review-block-title p{text-align:center;font-size:19px;font-weight:700}.review-block-table{border-left:3px solid #dce2e9;background:#f8fafc}
.review-block-line{display:flex;align-items:center;justify-content:space-between;color:#8a929d;font-size:11px}.review-block-line button{border:0;background:transparent;color:var(--red);cursor:pointer;font-size:12px}.review-block-line button:disabled{color:#aeb5bf;cursor:not-allowed}
.review-block p{margin:7px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;font-size:14px;color:#29313b}
.review-paper-writing .review-block:not(.review-block-title):not(.review-block-table) p{font-family:'SimSun','Songti SC',serif;text-indent:2em;line-height:2;letter-spacing:.02em}
.review-paper-writing .review-block-title p{font-family:'SimHei','Microsoft YaHei',sans-serif;font-size:22px;line-height:1.6}
.review-block mark{padding:2px 0;background:#ffe38a;box-shadow:0 0 0 2px #ffe38a;color:#222}
.review-advice{margin:0 0 8px;padding:8px 10px;border-radius:5px;background:#f3f8ef;color:#2d5a1e;font-size:13px;line-height:1.6}.review-edit-area{box-sizing:border-box;width:100%;min-height:100px;padding:10px;border:1px solid #d5dce5;border-radius:5px;background:#fff;color:#29313b;line-height:1.7;resize:vertical}
.review-table-edit{display:grid;gap:8px}.review-table-edit label{display:grid;gap:3px;color:#6b7280;font-size:12px}.review-table-edit input{box-sizing:border-box;width:100%;padding:8px;border:1px solid #d5dce5;border-radius:5px;background:#fff;color:#29313b}
.review-edit-actions{display:flex;gap:8px;margin-top:7px}.review-edit-actions button{padding:6px 11px;border:1px solid #d0d6de;border-radius:5px;background:#fff;color:#505a66;cursor:pointer}.review-edit-actions button:first-child{border-color:var(--red);background:var(--red);color:#fff}
.review-issues-panel .review-filter-help{padding:10px 14px}.review-issues-panel .level-tabs{margin:4px 12px 12px;overflow:auto;white-space:nowrap}.review-issues-panel .level-tabs button{padding:8px 9px;font-size:12px}
.review-issues-panel .issue-card{display:block;min-height:0;margin:0 12px 10px;padding:14px 14px 14px 50px;border-radius:8px;cursor:pointer}.review-issues-panel .issue-card:has(.issue-select){padding-left:50px}
.review-issues-panel .issue-card-active{border-color:#d13a34;box-shadow:0 0 0 1px #d13a34}.review-issues-panel .risk-flag{left:10px;top:14px;width:30px;height:30px;border-radius:5px;font-size:11px}
.review-issues-panel .issue-select{left:2px;top:3px}.review-issues-panel .issue-body header{display:block}.review-issues-panel .issue-body header small{display:block;margin-top:4px}.review-issues-panel .text-change{flex-wrap:wrap;margin:12px 0 8px;font-size:14px}.review-issues-panel .issue-actions,.review-issues-panel .processed-mark{position:static;transform:none;margin-top:10px;max-width:none;text-align:left}.review-issues-panel .issue-actions{flex-wrap:wrap}.review-issues-panel .issue-actions button{padding:5px 8px;border-radius:5px;font-size:12px}
@media(max-width:1150px){.review-compare{grid-template-columns:1fr}.review-document-panel,.review-issues-panel{max-height:none}.review-paper{padding:16px}.review-issues-panel .issue-card{margin-bottom:10px}}
</style>
