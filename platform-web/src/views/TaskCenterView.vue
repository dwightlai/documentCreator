<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import { cancelJob, getJobEvents, listJobs, retryJob, submitHealthCheck, type AsyncJob, type JobEvent } from '@/api/jobs'

const jobs = ref<AsyncJob[]>([])
const events = ref<JobEvent[]>([])
const selectedId = ref('')
const statusFilter = ref('')
const loading = ref(false)
let refreshTimer: number | undefined
const selected = computed(() => jobs.value.find((job) => job.id === selectedId.value))
const runningCount = computed(() => jobs.value.filter((job) => ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(job.status)).length)
const failedCount = computed(() => jobs.value.filter((job) => ['FAILED', 'DEAD_LETTER'].includes(job.status)).length)
const completedCount = computed(() => jobs.value.filter((job) => job.status === 'COMPLETED').length)
const statusText: Record<AsyncJob['status'], string> = { QUEUED: '等待执行', RUNNING: '执行中', RETRY_WAIT: '等待重试', COMPLETED: '已完成', FAILED: '执行失败', DEAD_LETTER: '死信', CANCELLED: '已取消' }
const typeText: Record<string, string> = { SYSTEM_HEALTH_CHECK: '系统健康检查', KNOWLEDGE_PARSE: '知识文档解析' }

async function refresh(silent = false) {
  if (!silent) loading.value = true
  try {
    jobs.value = await listJobs(statusFilter.value)
    if (!selectedId.value && jobs.value[0]) selectedId.value = jobs.value[0].id
    if (selectedId.value && !jobs.value.some((job) => job.id === selectedId.value)) selectedId.value = jobs.value[0]?.id ?? ''
    await loadEvents()
  } catch (error) {
    if (!silent) ElMessage.error(error instanceof Error ? error.message : '任务列表加载失败')
  } finally { loading.value = false }
}
async function loadEvents() { events.value = selectedId.value ? await getJobEvents(selectedId.value) : [] }
async function selectJob(job: AsyncJob) { selectedId.value = job.id; await loadEvents() }
async function healthCheck() { const job = await submitHealthCheck(); selectedId.value = job.id; ElMessage.success('健康检查任务已提交'); await refresh(true) }
async function cancelSelected() { if (!selected.value) return; await cancelJob(selected.value.id); ElMessage.success('已提交取消请求'); await refresh(true) }
async function retrySelected() { if (!selected.value) return; await retryJob(selected.value.id); ElMessage.success('任务已重新入队'); await refresh(true) }
function eventMessage(event: JobEvent) { try { const value = JSON.parse(event.data) as Record<string, unknown>; return String(value.message ?? value.status ?? `进度 ${value.progress ?? '—'}%`) } catch { return event.data } }
function displayTime(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
onMounted(async () => { await refresh(); refreshTimer = window.setInterval(() => refresh(true), 5_000) })
onBeforeUnmount(() => window.clearInterval(refreshTimer))
</script>

<template>
  <AppShell><template #title>任务与运行中心</template>
    <section class="operations-center" v-loading="loading">
      <header class="operations-hero"><div><span class="section-no">ASYNCHRONOUS OPERATIONS</span><h2>任务在后台运行，<br><em>每一步都有记录。</em></h2><p>持久化进度、自动重试、断点事件回放与人工补偿统一在此管理。</p></div><dl><div><dt>运行中</dt><dd>{{ runningCount }}</dd></div><div><dt>已完成</dt><dd>{{ completedCount }}</dd></div><div><dt>需处理</dt><dd>{{ failedCount }}</dd></div></dl></header>
      <div class="operations-toolbar"><div><button v-for="item in [{v:'',t:'全部'},{v:'RUNNING',t:'运行中'},{v:'COMPLETED',t:'已完成'},{v:'DEAD_LETTER',t:'死信'}]" :key="item.v" type="button" :class="{ active:statusFilter===item.v }" @click="statusFilter=item.v;refresh()">{{ item.t }}</button></div><button class="primary-button" type="button" @click="healthCheck">发起健康检查</button></div>
      <div class="operations-grid">
        <aside class="job-ledger"><button v-for="job in jobs" :key="job.id" type="button" :class="{ active:job.id===selectedId }" @click="selectJob(job)"><span class="job-state" :data-status="job.status">{{ statusText[job.status].slice(0,1) }}</span><div><strong>{{ typeText[job.jobType] ?? job.jobType }}</strong><small>{{ job.businessId || job.id.slice(0,12) }}</small></div><b>{{ job.progress }}%</b><time>{{ displayTime(job.createdAt) }}</time></button><div v-if="!jobs.length" class="governance-empty">当前筛选条件下没有任务。</div></aside>
        <main v-if="selected" class="job-detail"><header><div><span class="section-no">JOB / {{ selected.id.slice(0,12) }}</span><h3>{{ typeText[selected.jobType] ?? selected.jobType }}</h3></div><b :data-status="selected.status">{{ statusText[selected.status] }}</b></header><div class="job-progress"><i :style="{ width:`${selected.progress}%` }"/><span>{{ selected.progress }}%</span></div><dl class="job-metadata"><div><dt>创建时间</dt><dd>{{ displayTime(selected.createdAt) }}</dd></div><div><dt>开始时间</dt><dd>{{ displayTime(selected.startedAt) }}</dd></div><div><dt>完成时间</dt><dd>{{ displayTime(selected.finishedAt) }}</dd></div><div><dt>重试次数</dt><dd>{{ selected.retryCount }} / {{ selected.maxRetries }}</dd></div></dl><section class="event-ledger"><header><span class="section-no">EVENT STREAM</span><h4>执行轨迹</h4></header><article v-for="event in events" :key="event.id" :data-event="event.event"><span>{{ String(event.id).padStart(2,'0') }}</span><div><strong>{{ event.event }}</strong><p>{{ eventMessage(event) }}</p></div><time>{{ displayTime(event.occurredAt) }}</time></article></section><p v-if="selected.errorMessage" class="job-error">{{ selected.errorMessage }}</p><footer><button v-if="['QUEUED','RUNNING','RETRY_WAIT'].includes(selected.status)" class="secondary-button" type="button" @click="cancelSelected">取消任务</button><button v-if="['FAILED','DEAD_LETTER','CANCELLED'].includes(selected.status)" class="primary-button" type="button" @click="retrySelected">人工补偿重试</button></footer></main>
        <main v-else class="job-detail job-detail-empty">选择一项任务查看持久化进度与事件。</main>
      </div>
    </section>
  </AppShell>
</template>
