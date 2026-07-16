<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import { askWritingQuestion, confirmWritingIdea, generateOutline, generateTitleCandidates, generateWritingIdea, insertWritingAnswer,
  type CreateWritingTask, type TitleCandidate, type WritingIdea, type WritingQa } from '@/api/writing'
import { useWritingStore } from '@/stores/writing'

const store = useWritingStore()
const dialogOpen = ref(false)
const creating = ref(false)
const outlineLoading = ref(false)
const planningLoading = ref(false)
const planningOpen = ref(false)
const planningTab = ref<'idea' | 'titles' | 'qa'>('idea')
const idea = ref<WritingIdea>()
const titles = ref<TitleCandidate[]>([])
const qaQuestion = ref('')
const qa = ref<WritingQa>()
const form = reactive<CreateWritingTask>({
  taskName: '', documentTypeCode: '请示', documentCategory: '法定公文', writingTopic: '', writingPurpose: '',
  expectedLengthMin: 1200, expectedLengthMax: 2400, allowExternalKnowledge: false, securityLevel: '内部',
})
const statusSteps = ['草稿', '材料准备', '提纲生成', '正文生成', '审校中', '已完成']
const activeStep = computed(() => Math.max(0, statusSteps.indexOf(store.selectedTask?.status ?? '草稿')))

async function submit() {
  if (!form.taskName.trim() || !form.writingTopic.trim() || !form.writingPurpose.trim()) {
    ElMessage.warning('请完整填写任务名称、写作主题和写作目的')
    return
  }
  creating.value = true
  try {
    await store.create({ ...form })
    dialogOpen.value = false
    ElMessage.success('写作任务已创建')
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '创建失败')
  } finally {
    creating.value = false
  }
}

async function recommend() {
  try { await store.recommend() } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '文种推荐失败')
  }
}

async function buildOutline() {
  if (!store.selectedId) return
  outlineLoading.value = true
  try {
    await generateOutline(store.selectedId)
    await store.load()
    ElMessage.success('提纲已生成，可进入提纲编辑')
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '提纲生成失败')
  } finally { outlineLoading.value = false }
}

async function buildIdea() {
  if (!store.selectedId) return
  planningLoading.value = true; planningTab.value = 'idea'; planningOpen.value = true
  try { idea.value = await generateWritingIdea(store.selectedId) }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '写作思路生成失败') }
  finally { planningLoading.value = false }
}

async function buildTitles() {
  if (!store.selectedId) return
  planningLoading.value = true; planningTab.value = 'titles'; planningOpen.value = true
  try { titles.value = await generateTitleCandidates(store.selectedId) }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '标题候选生成失败') }
  finally { planningLoading.value = false }
}

async function confirmIdea() {
  if (!store.selectedId || !idea.value) return
  try { idea.value = await confirmWritingIdea(store.selectedId, idea.value); ElMessage.success('写作思路已确认并锁定') }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '写作思路确认失败') }
}

async function ask() {
  if (!store.selectedId || qaQuestion.value.trim().length < 2) { ElMessage.warning('请输入完整问题'); return }
  planningLoading.value = true
  try { qa.value = await askWritingQuestion(store.selectedId, qaQuestion.value) }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '问答失败') }
  finally { planningLoading.value = false }
}

async function insertAnswer() {
  if (!store.selectedId || !qa.value) return
  if (!store.selectedTask?.currentDocumentId) { ElMessage.warning('请先生成正文，再插入问答结果'); return }
  try { await insertWritingAnswer(store.selectedId, qa.value.id); qa.value.inserted = true; await store.load(); ElMessage.success('问答结果已确认插入，并生成文稿新版本') }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '插入失败') }
}

onMounted(store.load)
</script>

<template>
  <AppShell>
    <template #title>智能写作工作台</template>
    <section class="workspace-grid">
      <aside class="task-panel">
        <div class="panel-head">
          <div><span class="section-no">01</span><h2>写作任务</h2></div>
          <button class="square-action" type="button" aria-label="新建写作任务" @click="dialogOpen = true">＋</button>
        </div>
        <label class="search-box">
          <span>⌕</span><input placeholder="检索任务名称" aria-label="检索任务名称" />
        </label>
        <div v-if="store.loading && !store.tasks.length" class="empty-state">正在读取任务…</div>
        <div v-else-if="store.error" class="empty-state error">{{ store.error }}<button type="button" @click="store.load">重试</button></div>
        <div v-else-if="!store.tasks.length" class="empty-state">
          <strong>尚无写作任务</strong><p>从一份明确的写作意图开始。</p>
          <button type="button" @click="dialogOpen = true">创建第一项任务</button>
        </div>
        <button v-for="task in store.tasks" :key="task.id" type="button" class="task-card"
          :class="{ selected: task.id === store.selectedId }" @click="store.selectedId = task.id">
          <span class="task-type">{{ task.documentTypeCode }}</span>
          <strong>{{ task.taskName }}</strong>
          <small>{{ task.status }} · {{ task.securityLevel }}</small>
          <time>{{ new Date(task.updatedAt).toLocaleDateString('zh-CN') }}</time>
        </button>
      </aside>

      <div class="work-canvas">
        <template v-if="store.selectedTask">
          <div class="document-banner">
            <div>
              <span class="classification">{{ store.selectedTask.securityLevel }}</span>
              <p>{{ store.selectedTask.documentCategory }} / {{ store.selectedTask.documentTypeCode }}</p>
              <h2>{{ store.selectedTask.taskName }}</h2>
              <span>{{ store.selectedTask.writingTopic }}</span>
            </div>
            <RouterLink class="ghost-button" :to="`/writing/${store.selectedTask.id}/editor`">进入材料 / 提纲 / 正文工作区</RouterLink>
          </div>

          <div class="process-strip" aria-label="写作流程">
            <div v-for="(step, index) in statusSteps" :key="step" :class="{ done: index < activeStep, current: index === activeStep }">
              <span>{{ index + 1 }}</span><small>{{ step }}</small>
            </div>
          </div>

          <div class="canvas-columns">
            <section class="brief-card paper-card">
              <header><span class="section-no">02</span><h3>任务简报</h3><span class="rule" /></header>
              <dl>
                <div><dt>写作目的</dt><dd>{{ store.selectedTask.writingPurpose }}</dd></div>
                <div><dt>篇幅区间</dt><dd>{{ store.selectedTask.expectedLengthMin }}—{{ store.selectedTask.expectedLengthMax }} 字</dd></div>
                <div><dt>外部知识</dt><dd>{{ store.selectedTask.allowExternalKnowledge ? '允许引用' : '仅限内部材料' }}</dd></div>
              </dl>
              <div class="action-row">
                <button class="primary-button" type="button" @click="recommend">智能推荐文种</button>
                <button class="secondary-button" type="button" @click="buildIdea">生成写作思路</button>
                <button class="secondary-button" type="button" @click="buildTitles">标题候选</button>
                <button class="secondary-button" type="button" :disabled="outlineLoading" @click="buildOutline">
                  {{ outlineLoading ? '正在生成…' : '生成写作提纲' }}
                </button>
              </div>
            </section>

            <section class="assistant-card">
              <header><span class="live-dot" /><h3>智能参谋</h3><small>规则增强模式</small></header>
              <p v-if="!store.suggestions.length" class="assistant-intro">系统将综合写作目的、行文关系与材料内容，给出可追溯的文种及结构建议。</p>
              <article v-for="suggestion in store.suggestions" :key="suggestion.documentType">
                <div><strong>{{ suggestion.documentType }}</strong><span>{{ Math.round(suggestion.confidence * 100) }}%</span></div>
                <p>{{ suggestion.reason }}</p>
              </article>
              <footer>AI 生成内容须经人工复核后使用</footer>
            </section>
          </div>
        </template>
        <div v-else class="canvas-empty"><span>文</span><h2>选择或创建一项写作任务</h2><p>材料、提纲、正文和审校结果将在同一工作区连续呈现。</p></div>
      </div>
    </section>

    <el-dialog v-model="dialogOpen" title="新建写作任务" width="620px" class="task-dialog" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="任务名称"><el-input v-model="form.taskName" maxlength="80" show-word-limit placeholder="例如：关于申请专项建设资金的请示" /></el-form-item>
        <div class="form-pair">
          <el-form-item label="文种"><el-select v-model="form.documentTypeCode"><el-option v-for="item in ['请示','报告','通知','函','纪要','通报']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
          <el-form-item label="密级"><el-select v-model="form.securityLevel"><el-option v-for="item in ['公开','内部','秘密','机密']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        </div>
        <el-form-item label="写作主题"><el-input v-model="form.writingTopic" maxlength="200" placeholder="说明公文要解决的核心事项" /></el-form-item>
        <el-form-item label="写作目的"><el-input v-model="form.writingPurpose" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明行文对象、预期结果和关键要求" /></el-form-item>
        <div class="form-pair">
          <el-form-item label="最少字数"><el-input-number v-model="form.expectedLengthMin" :min="300" :max="20000" /></el-form-item>
          <el-form-item label="最多字数"><el-input-number v-model="form.expectedLengthMax" :min="form.expectedLengthMin" :max="30000" /></el-form-item>
        </div>
      </el-form>
      <template #footer><button class="secondary-button" type="button" @click="dialogOpen = false">取消</button><button class="primary-button" type="button" :disabled="creating" @click="submit">{{ creating ? '正在创建…' : '创建任务' }}</button></template>
    </el-dialog>

    <el-drawer v-model="planningOpen" size="560px" class="planning-drawer" :with-header="false">
      <header class="planning-head"><div><span class="section-no">WRITING PLAN</span><h2>写作规划台</h2></div><button type="button" aria-label="关闭" @click="planningOpen = false">×</button></header>
      <div class="planning-tabs"><button type="button" :class="{ active: planningTab === 'idea' }" @click="planningTab = 'idea'">写作思路</button><button type="button" :class="{ active: planningTab === 'titles' }" @click="planningTab = 'titles'">标题候选</button><button type="button" :class="{ active: planningTab === 'qa' }" @click="planningTab = 'qa'">依据问答</button></div>
      <div v-if="planningLoading" class="planning-empty">正在形成规划建议…</div>
      <section v-else-if="planningTab === 'idea' && idea" class="idea-editor">
        <div class="idea-version"><span>思路版本 V{{ idea.version }}</span><b :class="{ confirmed: idea.confirmed }">{{ idea.confirmed ? '已确认' : '待确认' }}</b></div>
        <label>文章主旨<textarea v-model="idea.mainTheme" :disabled="idea.confirmed" /></label>
        <label>核心观点（每行一项）<textarea :value="idea.coreViewpoints.join('\n')" :disabled="idea.confirmed" @input="idea.coreViewpoints = ($event.target as HTMLTextAreaElement).value.split(/\n/).filter(Boolean)" /></label>
        <label>行文顺序（每行一步）<textarea :value="idea.writingSequence.join('\n')" :disabled="idea.confirmed" @input="idea.writingSequence = ($event.target as HTMLTextAreaElement).value.split(/\n/).filter(Boolean)" /></label>
        <div class="idea-notes"><article><small>必须表达</small><p>{{ idea.mustExpressFacts }}</p></article><article><small>材料缺口</small><p>{{ idea.missingMaterials }}</p></article><article class="warning"><small>风险提示</small><p>{{ idea.riskWarnings }}</p></article></div>
        <button v-if="!idea.confirmed" class="primary-button confirm-plan" type="button" @click="confirmIdea">确认并锁定本版思路</button>
      </section>
      <section v-else-if="planningTab === 'titles' && titles.length" class="title-candidates">
        <article v-for="(title, index) in titles" :key="title.titleText"><header><span>0{{ index + 1 }}</span><b>{{ Math.round(title.confidence * 100) }}%</b></header><h3>{{ title.titleText }}</h3><p>{{ title.titleStructure }} · {{ title.applicableScenario }}</p><small v-if="title.riskWarnings.length">{{ title.riskWarnings.join('；') }}</small></article>
      </section>
      <section v-else-if="planningTab === 'qa'" class="qa-workbench">
        <form @submit.prevent="ask"><label>围绕任务和权限内知识提问</label><textarea v-model="qaQuestion" placeholder="例如：材料中对资金测算有哪些明确要求？" /><button class="primary-button" type="submit">检索并回答</button></form>
        <article v-if="qa" class="qa-answer"><header><span>{{ qa.answerType }}</span><b>{{ qa.sources.length }} 个来源</b></header><p>{{ qa.answer }}</p><div v-if="qa.sources.length" class="qa-sources"><section v-for="source in qa.sources" :key="source.chunkId"><strong>{{ source.documentTitle }}</strong><small>{{ source.sourceName }} · {{ Math.round(source.score * 100) }}%</small><p>{{ source.snippet }}</p></section></div><button v-if="!qa.inserted" class="secondary-button" type="button" @click="insertAnswer">确认后插入正文</button><span v-else class="qa-inserted">✓ 已插入正文</span></article>
      </section>
      <div v-else class="planning-empty"><span>策</span><p>从任务简报生成写作思路或标题候选。</p></div>
    </el-drawer>
  </AppShell>
</template>
