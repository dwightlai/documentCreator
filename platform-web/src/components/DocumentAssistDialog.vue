<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { applyAssist, applyFormat, insertSummary, requestAssistPreview, requestFormatPreview, requestSummary, type AssistProposal, type DocumentSummary, type FormatChange, type WritingDocument } from '@/api/writing'

const open = defineModel<boolean>({ required: true })
const props = defineProps<{ taskId: string; mode: 'summary' | 'polish' | 'expand' | 'format'; outlineNodeId?: string; outlineConfirmed: boolean }>()
const emit = defineEmits<{ applied: [document: WritingDocument] }>()
const busy = ref(false)
const scope = ref<'本节' | '全文'>('本节')
const summary = ref<DocumentSummary>()
const proposals = ref<AssistProposal[]>([])
const picked = ref<string[]>([])
const changes = ref<FormatChange[]>([])
const truncated = ref(false)

function fail(error: unknown) {
  const err = error as { response?: { data?: { message?: string } }; message?: string }
  ElMessage.error(err.response?.data?.message || err.message || '请求失败')
}
function summaryText() {
  if (!summary.value) return ''
  const points = summary.value.points.map((item, index) => `${index + 1}. ${item}`).join('\n')
  return `摘要：${summary.value.brief}${points ? `\n要点：\n${points}` : ''}`
}
async function load() {
  busy.value = true
  summary.value = undefined
  proposals.value = []
  changes.value = []
  truncated.value = false
  try {
    if (props.mode === 'summary') summary.value = await requestSummary(props.taskId)
    else if (props.mode === 'format') changes.value = (await requestFormatPreview(props.taskId)).changes
    else await loadProposals()
  } catch (error) { fail(error) }
  finally { busy.value = false }
}
async function loadProposals() {
  if (scope.value === '本节' && !props.outlineNodeId) { ElMessage.warning('请先选择章节'); return }
  if (props.mode === 'expand' && scope.value === '全文' && !props.outlineConfirmed) { ElMessage.warning('全文扩写需要已确认提纲'); return }
  const result = await requestAssistPreview(props.taskId, { operation: props.mode === 'polish' ? '润色' : '扩写', scope: scope.value, outlineNodeId: scope.value === '本节' ? props.outlineNodeId : undefined })
  proposals.value = result.proposals
  picked.value = result.proposals.map(item => item.paragraphId)
  truncated.value = result.truncated
}
async function copySummary() {
  await navigator.clipboard.writeText(summaryText())
  ElMessage.success('已复制')
}
async function insert() {
  busy.value = true
  try {
    emit('applied', await insertSummary(props.taskId, summaryText()))
    open.value = false
  } catch (error) { fail(error) }
  finally { busy.value = false }
}
async function adopt() {
  const items = proposals.value.filter(item => picked.value.includes(item.paragraphId)).map(item => ({ paragraphId: item.paragraphId, text: item.proposed }))
  if (!items.length) { ElMessage.warning('请选择要采用的段落'); return }
  busy.value = true
  try {
    emit('applied', await applyAssist(props.taskId, items))
    open.value = false
  } catch (error) { fail(error) }
  finally { busy.value = false }
}
async function confirmFormat() {
  busy.value = true
  try {
    emit('applied', await applyFormat(props.taskId))
    open.value = false
  } catch (error) { fail(error) }
  finally { busy.value = false }
}
watch(open, value => { if (value) load() })
</script>

<template>
  <el-dialog :model-value="open" :title="mode === 'summary' ? '提取摘要' : mode === 'polish' ? '润色' : mode === 'expand' ? '扩写' : '格式整理'" width="720px" @close="open = false">
    <div v-loading="busy">
      <template v-if="mode === 'summary'">
        <p v-if="summary">{{ summary.brief }}</p>
        <ol v-if="summary?.points.length"><li v-for="item in summary.points" :key="item">{{ item }}</li></ol>
        <p v-else-if="!busy">没有可提取的正文。</p>
      </template>
      <template v-else-if="mode === 'format'">
        <p v-if="!changes.length && !busy">没有需要整理的内容。确认后才会写入新版本，套红仍使用现有排版。</p>
        <article v-for="item in changes" :key="item.paragraphId">
          <small>{{ item.reason }}</small>
          <p>{{ item.original }}</p>
          <p>{{ item.proposed }}</p>
        </article>
      </template>
      <template v-else>
        <label><input v-model="scope" type="radio" value="本节" @change="load"> 本节</label>
        <label><input v-model="scope" type="radio" value="全文" @change="load"> 全文</label>
        <p v-if="truncated">本次只处理前 12 段有差异的内容。</p>
        <p v-if="!proposals.length && !busy">没有可采用的差异。划词润色和扩写仍可在段落中使用。</p>
        <label v-for="item in proposals" :key="item.paragraphId" class="proposal">
          <input v-model="picked" type="checkbox" :value="item.paragraphId">
          <span><b>原文</b> {{ item.original }}</span>
          <span><b>建议</b> {{ item.proposed }}</span>
        </label>
      </template>
    </div>
    <template #footer>
      <template v-if="mode === 'summary' && summary">
        <button type="button" @click="copySummary">复制</button>
        <button type="button" @click="insert">插入文首</button>
      </template>
      <button v-else-if="mode === 'format' && changes.length" type="button" @click="confirmFormat">确认并保存新版本</button>
      <button v-else-if="proposals.length" type="button" @click="adopt">采用所选</button>
    </template>
  </el-dialog>
</template>

<style scoped>
.proposal { display:grid; gap:4px; margin:12px 0; }
label { margin-right:12px; }
article { margin:12px 0; }
</style>
