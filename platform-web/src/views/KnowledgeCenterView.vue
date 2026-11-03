<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import {
  createKnowledgeSource, deleteKnowledgeDocument, deleteKnowledgeSource, listKnowledgeChunks, listKnowledgeDocuments,
  listKnowledgeSources, parseKnowledgeDocument, searchKnowledge, updateKnowledgeChunk, reclassifyKnowledgeSource, listKnowledgeRevisions,
  updateKnowledgeDocument, updateKnowledgeSource, uploadKnowledgeDocument,
  type KnowledgeChunk, type KnowledgeDocument, type KnowledgeHit, type KnowledgeSource, type KnowledgeRevision,
} from '@/api/knowledge'
import { useDict } from '@/composables/useDict'

const { options: permissionOptions } = useDict('security_level', ['公开', '内部', '秘密', '机密'])
const { options: knowledgeUsageOptions } = useDict('knowledge_usage_type', ['待分类', '规范依据', '事实依据', '写法参考'])
const { options: completenessOptions } = useDict('knowledge_completeness', ['本地原文可用', '原文摘录', '来源索引'])
const { options: verificationOptions } = useDict('knowledge_verification', ['待整理', '待审核', '已核验', '退回'])
const { options: authorityOptions } = useDict('material_authority', ['正式公文', '权威资料', '内部资料', '参考资料', '官方公开资料'])

const sources = ref<KnowledgeSource[]>([])
const documents = ref<KnowledgeDocument[]>([])
const chunks = ref<KnowledgeChunk[]>([])
const hits = ref<KnowledgeHit[]>([])
const selectedId = ref('')
const selectedDocument = ref<KnowledgeDocument>()
const selectedDocumentSourceId = ref('')
const query = ref('')
const loading = ref(false)
const sourceDialog = ref(false)
const categoryDialog = ref(false)
const newCategory = ref('')
const editingSourceId = ref('')
const uploadDialog = ref(false)
const metadataDialog = ref(false)
const chunksDialog = ref(false)
const revisionDialog = ref(false)
const revisions = ref<KnowledgeRevision[]>([])
const activeRevision = ref<KnowledgeRevision>()
const revisionChunks = computed(() => {
  try { return (JSON.parse(activeRevision.value?.snapshotJson || '{}') as {chunks?:KnowledgeChunk[]}).chunks || [] }
  catch { return [] }
})
const editingChunkId = ref('')
const publicDetailDialog = ref(false)
const publicDetailDocument = ref<KnowledgeDocument>()
const publicDetailText = ref('')
const chunkText = ref('')
const uploadFile = ref<File>()
const selected = computed(() => sources.value.find(item => item.id === selectedId.value))
const existingCategories = computed(() => [...new Set(sources.value.map(item => item.category).filter(Boolean))])
const publicSources = computed(() => sources.value.filter(item => item.sourceCode.startsWith('PUBLIC-')))
const publicEntryCount = computed(() => publicSources.value.reduce((total, item) => total + item.entryCount, 0))
const publicMetadata = computed(() => {
  try { return JSON.parse(publicDetailDocument.value?.metadata || '{}') as Record<string, string> }
  catch { return {} as Record<string, string> }
})
const publicSummary = computed(() => publicDetailText.value.match(/内容摘要：([^\n]*)/)?.[1] ?? '')
function documentMetadata(doc: KnowledgeDocument): Record<string, string> {
  try { return JSON.parse(doc.metadata || '{}') as Record<string, string> }
  catch { return {} }
}
function offlineContentType(doc: KnowledgeDocument) {
  return documentMetadata(doc).offlineContentType || '来源索引'
}
const sourceForm = reactive({ sourceCode: '', sourceName: '', category: '单位制度', storageType: '全文索引', permissionScope: '本单位' })
const uploadForm = reactive({ title: '', authorityLevel: '单位制度', permissionLevel: '内部' })
const metadataForm = reactive({ documentTitle: '', metadata: '{}', authorityLevel: '', effectiveDate: '', expiryDate: '', permissionLevel: '内部', usageType: '待分类', completeness: '来源索引', verificationStatus: '待核验', validityStatus: '待核验', publisher: '', documentNumber: '', issuedDate: '', applicableRegion: '', applicableAudience: '', applicableScope: '' })

function openSource(source?: KnowledgeSource) {
  editingSourceId.value = source?.id ?? ''
  Object.assign(sourceForm, source ? { sourceCode: source.sourceCode, sourceName: source.sourceName,
    category: source.category, storageType: source.storageType, permissionScope: source.permissionScope ?? '' }
    : { sourceCode: '', sourceName: '', category: '单位制度', storageType: '全文索引', permissionScope: '本单位' })
  sourceDialog.value = true
}

function fail(reason: unknown, fallback: string) { ElMessage.error(reason instanceof Error ? reason.message : fallback) }
async function load() {
  loading.value = true
  try {
    sources.value = await listKnowledgeSources()
    if (!sources.value.some(item => item.id === selectedId.value)) selectedId.value = sources.value[0]?.id ?? ''
  } catch (reason) { fail(reason, '知识库加载失败') }
  finally { loading.value = false }
}
async function loadDocuments(id: string) {
  documents.value = []
  if (!id) return
  const loaded = await listKnowledgeDocuments(id)
  if (selectedId.value === id) documents.value = loaded
}
async function refreshDocuments() {
  if (!selected.value) return
  await loadDocuments(selected.value.id)
  sources.value = await listKnowledgeSources()
  if (selectedDocument.value) selectedDocument.value = documents.value.find(item => item.id === selectedDocument.value?.id)
}
async function saveSource() {
  try {
    const existing = sources.value.find(item => item.id === editingSourceId.value)
    const changed = existing
      ? await updateKnowledgeSource(existing.id, { ...sourceForm, lockVersion: existing.lockVersion })
      : await createKnowledgeSource({ ...sourceForm })
    await load(); selectedId.value = changed.id; sourceDialog.value = false
    ElMessage.success(existing ? '知识库已更新' : '知识库已创建')
  } catch (reason) { fail(reason, '知识库保存失败') }
}
async function changeCategory() {
  if (!selected.value || !newCategory.value.trim()) return
  try {
    const source = selected.value
    await ElMessageBox.confirm(`知识库“${source.sourceName}”及其 ${source.entryCount} 份文档将归入“${newCategory.value.trim()}”。`, '调整知识库分类', { type: 'warning', confirmButtonText: '确认调整' })
    await reclassifyKnowledgeSource(source.id, newCategory.value.trim(), source.lockVersion)
    categoryDialog.value = false; await load(); ElMessage.success('分类已调整')
  } catch (reason) { if (reason !== 'cancel' && reason !== 'close') fail(reason, '分类调整失败') }
}
async function removeSource() {
  const source = selected.value
  if (!source) return
  try {
    await ElMessageBox.confirm(`确定删除知识库“${source.sourceName}”及其 ${source.entryCount} 份文档？`, '删除知识库',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteKnowledgeSource(source.id)
    selectedId.value = ''; documents.value = []; await load()
    ElMessage.success('知识库已删除')
  } catch (reason) { if (reason !== 'cancel' && reason !== 'close') fail(reason, '知识库删除失败') }
}
async function upload() {
  if (!selected.value || !uploadFile.value) { ElMessage.warning('请选择文件'); return }
  try {
    await uploadKnowledgeDocument(selected.value.id, uploadFile.value, { ...uploadForm })
    await refreshDocuments(); uploadDialog.value = false; uploadFile.value = undefined
    ElMessage.success('文档已上传，等待解析')
  } catch (reason) { fail(reason, '文档上传失败') }
}
async function parse(item: KnowledgeDocument) {
  if (!selected.value) return
  try {
    await parseKnowledgeDocument(selected.value.id, item.id)
    item.parsedStatus = '解析中'; ElMessage.success('解析任务已提交，可在“任务运行”中查看进度')
  } catch (reason) { fail(reason, '解析任务提交失败') }
}
async function search() {
  if (query.value.trim().length < 2) { ElMessage.warning('请输入至少两个字符'); return }
  try { hits.value = await searchKnowledge(query.value, selectedId.value) }
  catch (reason) { fail(reason, '检索失败') }
}
function openMetadata(doc: KnowledgeDocument) {
  selectedDocument.value = doc
  Object.assign(metadataForm, {
    documentTitle: doc.documentTitle, metadata: doc.metadata || '{}', authorityLevel: doc.authorityLevel,
    effectiveDate: doc.effectiveDate ?? '', expiryDate: doc.expiryDate ?? '', permissionLevel: doc.permissionLevel,
    usageType: doc.usageType ?? '待分类', completeness: doc.completeness ?? '来源索引', verificationStatus: doc.verificationStatus ?? '待核验', validityStatus: doc.validityStatus ?? '待核验', publisher: doc.publisher ?? '', documentNumber: doc.documentNumber ?? '', issuedDate: doc.issuedDate ?? '', applicableRegion: doc.applicableRegion ?? '', applicableAudience: doc.applicableAudience ?? '', applicableScope: doc.applicableScope ?? '',
  })
  metadataDialog.value = true
}
async function saveMetadata() {
  const doc = selectedDocument.value
  if (!doc || !selected.value) return
  if (!metadataForm.documentTitle.trim()) { ElMessage.warning('请填写文档标题'); return }
  try {
    JSON.parse(metadataForm.metadata || '{}')
    await updateKnowledgeDocument(selected.value.id, doc.id, {
      ...metadataForm, effectiveDate: metadataForm.effectiveDate || undefined,
      expiryDate: metadataForm.expiryDate || undefined, lockVersion: doc.lockVersion,
    })
    await refreshDocuments(); metadataDialog.value = false; ElMessage.success('文档元数据已保存')
  } catch (reason) { fail(reason, '元数据保存失败，请检查 JSON 格式') }
}
async function removeDocument(doc: KnowledgeDocument) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确定删除文档“${doc.documentTitle}”及其 ${doc.chunkCount} 个切片？`, '删除知识文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteKnowledgeDocument(selected.value.id, doc.id)
    if (selectedDocument.value?.id === doc.id) { selectedDocument.value = undefined; chunksDialog.value = false }
    await refreshDocuments(); ElMessage.success('文档已删除')
  } catch (reason) { if (reason !== 'cancel' && reason !== 'close') fail(reason, '文档删除失败') }
}
async function openChunks(doc: KnowledgeDocument, sourceId = selected.value?.id) {
  if (!sourceId) return
  try {
    chunks.value = await listKnowledgeChunks(sourceId, doc.id)
    selectedDocument.value = doc; selectedDocumentSourceId.value = sourceId
    editingChunkId.value = ''; chunksDialog.value = true
  } catch (reason) { fail(reason, '切片加载失败') }
}
async function openRevisions(doc:KnowledgeDocument) {
  if(!selected.value) return
  try { revisions.value=await listKnowledgeRevisions(selected.value.id,doc.id); activeRevision.value=revisions.value[0]; revisionDialog.value=true }
  catch(reason) { fail(reason,'历史版本加载失败') }
}
async function openOfflineOriginal(doc: KnowledgeDocument) {
  if (offlineContentType(doc) === '公开页正文') { await openChunks(doc); return }
  const url = documentMetadata(doc).sourceUrl
  if (!url) { ElMessage.warning('这条资料没有登记原文地址'); return }
  try {
    for (const source of publicSources.value) {
      const pool = source.id === selectedId.value ? documents.value : await listKnowledgeDocuments(source.id)
      const original = pool.find(item => offlineContentType(item) === '公开页正文' && documentMetadata(item).sourceUrl === url)
      if (original) { await openChunks(original, source.id); return }
    }
    ElMessage.warning('这条资料仅保存来源索引，暂无可离线查看的正文')
  } catch (reason) { fail(reason, '本地原文加载失败') }
}
async function openPublicDetail(doc: KnowledgeDocument) {
  if (!selected.value) return
  try {
    publicDetailDocument.value = doc
    publicDetailText.value = (await listKnowledgeChunks(selected.value.id, doc.id))[0]?.chunkText ?? ''
    publicDetailDialog.value = true
  } catch (reason) { fail(reason, '公开来源加载失败') }
}
function editChunk(chunk: KnowledgeChunk) { editingChunkId.value = chunk.id; chunkText.value = chunk.chunkText }
async function saveChunk(chunk: KnowledgeChunk) {
  if (!selectedDocumentSourceId.value || !selectedDocument.value) return
  if (!chunkText.value.trim()) { ElMessage.warning('切片内容不能为空'); return }
  try {
    const changed = await updateKnowledgeChunk(selectedDocumentSourceId.value, selectedDocument.value.id, chunk.id,
      { chunkText: chunkText.value, lockVersion: chunk.lockVersion })
    chunks.value = chunks.value.map(item => item.id === changed.id ? changed : item)
    editingChunkId.value = ''; ElMessage.success('切片已更新，检索内容已同步')
  } catch (reason) { fail(reason, '切片保存失败') }
}
watch(selectedId, id => loadDocuments(id).catch(reason => fail(reason, '文档加载失败')))
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>知识与依据中心</template>
    <section class="knowledge-center" v-loading="loading">
      <header class="knowledge-hero"><div><h2>知识库检索</h2></div><form @submit.prevent="search"><input v-model="query" placeholder="检索政策、制度、历史公文…"><button type="submit">检索依据</button></form></header>
      <div class="knowledge-use-guide"><strong>知识如何用于写作</strong><span>① 创建知识库时自定分类　② 上传文档后在“编辑元数据”中标明用途、完整性及核验状态　③ 到写作任务的正文工作区，选定章节后点“选用本节依据”</span><small>检索和查看仅供参考；明确选用的片段才供该章节生成。原文摘录与起草表述需在插入前预览确认，引用仍需人工核对。</small></div>
      <section v-if="publicSources.length" class="public-resource-overview" aria-label="公开数据资源">
        <div class="public-resource-heading"><strong>公开数据资源</strong><span>{{ publicSources.length }} 类 · {{ publicEntryCount }} 份本地资料</span><small>含官方文件正文、原文摘录和来源索引；具体效力与适用范围仍需核对。</small></div>
        <div class="public-resource-links"><button v-for="source in publicSources" :key="source.id" type="button" :class="{ active: selectedId === source.id }" @click="hits = []; selectedId = source.id">{{ source.sourceName }} <span>{{ source.entryCount }}</span></button></div>
      </section>
      <div class="knowledge-grid">
        <aside class="knowledge-sources">
          <header><div><h3>知识库</h3></div><button type="button" @click="openSource()">＋ 新建</button></header>
           <button v-for="item in sources" :key="item.id" type="button" :class="{ active: item.id === selectedId }" @click="selectedId = item.id"><span>{{ item.category.slice(0, 1) }}</span><div><strong>{{ item.sourceName }}</strong><small>{{ item.category }} · {{ item.entryCount }} 份</small></div></button>
          <div v-if="!sources.length" class="governance-empty">尚无知识库。</div>
        </aside>
        <main class="knowledge-stage">
          <template v-if="hits.length">
            <header class="knowledge-title"><div><span class="section-no">检索结果</span><h3>检索结果</h3></div><button type="button" @click="hits = []">返回文档库</button></header>
            <article v-for="hit in hits" :key="hit.chunkId" class="knowledge-hit"><header><span>{{ hit.sourceName }} / {{ hit.documentTitle }}</span><b>{{ Math.round(hit.score * 100) }}%</b></header><p>{{ hit.snippet }}</p><footer><span>{{ hit.authorityLevel }} · {{ hit.permissionLevel }}</span><code>{{ hit.fileHash.slice(0, 12) }}…</code></footer></article>
          </template>
          <template v-else-if="selected">
            <header class="knowledge-title"><div><h3>{{ selected.sourceName }}</h3><p>{{ selected.sourceCode }} · {{ selected.category }}</p></div><div class="knowledge-source-actions"><button class="secondary-button" type="button" @click="openSource(selected)">编辑知识库</button><button class="secondary-button" type="button" @click="newCategory = selected.category; categoryDialog = true">调整分类</button><button class="secondary-button" type="button" @click="removeSource">删除知识库</button><button class="primary-button" type="button" @click="uploadDialog = true">上传知识文档</button></div></header>
            <div class="knowledge-doc-head"><span>文档标题</span><span>权限 / 权威性</span><span>解析状态</span><span>操作</span></div>
            <article v-for="doc in documents" :key="doc.id" class="knowledge-doc">
              <strong>{{ doc.documentTitle }}<small v-if="selected.sourceCode.startsWith('PUBLIC-')" class="offline-content-type">{{ offlineContentType(doc) }}</small><small>V{{ doc.documentVersion }} · {{ doc.originalFileHash?.slice(0, 10) }}</small></strong>
              <span>{{ doc.permissionLevel }} / {{ doc.usageType || '待分类' }} / {{ doc.completeness || '来源索引' }} / {{ doc.verificationStatus || '待核验' }}</span>
              <b :data-status="doc.parsedStatus">{{ doc.parsedStatus }}<small>{{ doc.chunkCount }} 切片</small></b>
              <div class="knowledge-doc-actions"><button v-if="selected.sourceCode.startsWith('PUBLIC-')" type="button" @click="openPublicDetail(doc)">查看来源</button><button v-if="selected.sourceCode.startsWith('PUBLIC-') && offlineContentType(doc) !== '来源索引'" type="button" @click="openOfflineOriginal(doc)">查看本地原文</button><button type="button" @click="openChunks(doc)">查看切片</button><button type="button" @click="openRevisions(doc)">历史版本</button><button type="button" @click="openMetadata(doc)">编辑元数据</button><button v-if="doc.parsedStatus !== '已完成'" type="button" @click="parse(doc)">开始解析</button><button type="button" @click="removeDocument(doc)">删除</button></div>
            </article>
            <div v-if="!documents.length" class="knowledge-empty">上传 TXT、Markdown、CSV、DOCX 或可提取文本的 PDF。</div>
          </template>
        </main>
      </div>
    </section>
    <el-dialog v-model="sourceDialog" :title="editingSourceId ? '编辑知识库' : '创建知识库'" width="520px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="知识库代码"><el-input v-model="sourceForm.sourceCode" :disabled="!!editingSourceId" /></el-form-item><el-form-item label="知识库名称"><el-input v-model="sourceForm.sourceName" /></el-form-item></div><div class="form-pair"><el-form-item label="分类（创建后固定）"><el-input v-model="sourceForm.category" :disabled="!!editingSourceId" list="knowledge-categories" placeholder="可选已有分类或输入新分类" /><datalist id="knowledge-categories"><option v-for="v in existingCategories" :key="v" :value="v" /></datalist></el-form-item><el-form-item label="存储类型"><el-select v-model="sourceForm.storageType" :disabled="!!editingSourceId"><el-option value="全文索引" /><el-option value="混合" disabled /></el-select></el-form-item></div><el-form-item label="权限范围"><el-input v-model="sourceForm.permissionScope" /></el-form-item></el-form><template #footer><button class="secondary-button" type="button" @click="sourceDialog = false">取消</button><button class="primary-button" type="button" @click="saveSource">{{ editingSourceId ? '保存修改' : '创建' }}</button></template></el-dialog>
    <el-dialog v-model="categoryDialog" title="调整知识库分类" width="480px" class="task-dialog"><p>将同时调整该库及其全部文档的管理分类，不改变文档的写作用途。</p><el-input v-model="newCategory" list="knowledge-categories" placeholder="输入或选择分类" /><template #footer><button class="secondary-button" type="button" @click="categoryDialog = false">取消</button><button class="primary-button" type="button" @click="changeCategory">确认调整</button></template></el-dialog>
    <el-dialog v-model="uploadDialog" title="上传知识文档" width="520px" class="task-dialog"><el-form label-position="top"><el-form-item label="选择文件"><input type="file" accept=".txt,.md,.csv,.docx,.pdf" @change="uploadFile = ($event.target as HTMLInputElement).files?.[0]" /></el-form-item><el-form-item label="文档标题"><el-input v-model="uploadForm.title" placeholder="留空时使用文件名" /></el-form-item><div class="form-pair"><el-form-item label="权威等级"><el-select v-model="uploadForm.authorityLevel" allow-create filterable><el-option v-for="v in authorityOptions" :key="v" :value="v" /></el-select></el-form-item><el-form-item label="数据权限"><el-select v-model="uploadForm.permissionLevel"><el-option v-for="v in permissionOptions" :key="v" :value="v" /></el-select></el-form-item></div></el-form><template #footer><button class="secondary-button" type="button" @click="uploadDialog = false">取消</button><button class="primary-button" type="button" @click="upload">上传</button></template></el-dialog>
    <el-dialog v-model="metadataDialog" title="编辑文档元数据" width="680px" class="task-dialog"><el-form label-position="top"><el-form-item label="文档标题"><el-input v-model="metadataForm.documentTitle" /></el-form-item><div class="form-pair"><el-form-item label="写作用途"><el-select v-model="metadataForm.usageType"><el-option v-for="v in knowledgeUsageOptions" :key="v" :value="v" /></el-select></el-form-item><el-form-item label="入库完整性"><el-select v-model="metadataForm.completeness"><el-option v-for="v in completenessOptions" :key="v" :value="v" /></el-select></el-form-item></div><el-form-item label="内容核验"><el-select v-model="metadataForm.verificationStatus"><el-option v-for="v in verificationOptions" :key="v" :value="v" /></el-select></el-form-item><div class="form-pair"><el-form-item label="发布机关或资料责任主体"><el-input v-model="metadataForm.publisher" /></el-form-item><el-form-item label="文号"><el-input v-model="metadataForm.documentNumber" /></el-form-item></div><div class="form-pair"><el-form-item label="发布日期"><el-date-picker v-model="metadataForm.issuedDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" /></el-form-item><el-form-item label="适用地区"><el-input v-model="metadataForm.applicableRegion" /></el-form-item></div><div class="form-pair"><el-form-item label="适用对象"><el-input v-model="metadataForm.applicableAudience" /></el-form-item><el-form-item label="事项范围"><el-input v-model="metadataForm.applicableScope" /></el-form-item></div><div class="form-pair"><el-form-item label="权威等级"><el-select v-model="metadataForm.authorityLevel" allow-create filterable><el-option v-for="v in authorityOptions" :key="v" :value="v" /></el-select></el-form-item><el-form-item label="数据权限"><el-select v-model="metadataForm.permissionLevel"><el-option v-for="v in permissionOptions" :key="v" :value="v" /></el-select></el-form-item></div><div class="form-pair"><el-form-item label="生效日期"><el-date-picker v-model="metadataForm.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" /></el-form-item><el-form-item label="失效日期"><el-date-picker v-model="metadataForm.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" /></el-form-item></div><el-form-item label="扩展元数据（JSON）"><el-input v-model="metadataForm.metadata" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><button class="secondary-button" type="button" @click="metadataDialog = false">取消</button><button class="primary-button" type="button" @click="saveMetadata">保存修改</button></template></el-dialog>
    <el-dialog v-model="chunksDialog" :title="`文档切片 · ${selectedDocument?.documentTitle ?? ''}`" width="760px" class="task-dialog"><p class="chunk-intro">共 {{ chunks.length }} 个切片。{{ selectedDocument?.documentTitle.startsWith('离线原文') ? '下方是已保存在本地的公开页正文，可在断网环境检索和查看。' : '修改切片后，检索将使用新的内容；原始上传文件不会改变。' }} <button v-if="selectedDocument && offlineContentType(selectedDocument) === '原文摘录'" class="offline-original-shortcut" type="button" @click="openOfflineOriginal(selectedDocument)">查看对应的本地原文</button></p><div v-if="!chunks.length" class="governance-empty">暂无切片，请先解析文档。</div><article v-for="chunk in chunks" :key="chunk.id" class="knowledge-chunk"><header><strong>切片 {{ chunk.chunkOrder }}</strong><small>字符 {{ chunk.startPosition }}—{{ chunk.endPosition }}</small><button v-if="editingChunkId !== chunk.id" type="button" @click="editChunk(chunk)">编辑</button></header><template v-if="editingChunkId === chunk.id"><el-input v-model="chunkText" type="textarea" :rows="7" /><footer><button class="secondary-button" type="button" @click="editingChunkId = ''">取消</button><button class="primary-button" type="button" @click="saveChunk(chunk)">保存切片</button></footer></template><p v-else>{{ chunk.chunkText }}</p></article></el-dialog>
    <el-dialog v-model="revisionDialog" title="文档历史版本" width="760px" class="task-dialog"><p>这里保留修改前的元数据和切片快照。当前版本请使用“查看切片”。</p><div v-if="!revisions.length" class="governance-empty">尚无历史版本。</div><div v-else><el-select v-model="activeRevision" value-key="documentVersion" placeholder="选择版本"><el-option v-for="revision in revisions" :key="revision.documentVersion" :label="`V${revision.documentVersion} · ${revision.capturedAt} · ${revision.capturedBy}`" :value="revision" /></el-select><article v-for="chunk in revisionChunks" :key="chunk.id" class="knowledge-chunk"><header><strong>历史切片 {{ chunk.chunkOrder }}</strong><small>字符 {{ chunk.startPosition }}—{{ chunk.endPosition }}</small></header><p>{{ chunk.chunkText }}</p></article></div></el-dialog>
    <el-dialog v-model="publicDetailDialog" :title="publicDetailDocument?.documentTitle || '公开来源'" width="680px" class="task-dialog public-detail-dialog">
      <div class="public-detail-meta"><div><span>发布机构</span><strong>{{ publicMetadata.publisher || '待核验' }}</strong></div><div><span>文号</span><strong>{{ publicMetadata.documentNumber || '未标注' }}</strong></div><div><span>发文或公开日期</span><strong>{{ publicMetadata.issuedOn || '待核验' }}</strong></div><div><span>来源核验日期</span><strong>{{ publicMetadata.sourceCheckedOn || '待核验' }}</strong></div></div>
      <div class="public-detail-summary"><strong>内容摘要</strong><p>{{ publicSummary || (publicMetadata.offlineContentType === '公开页正文' ? '公开页面正文已保存在本地，请点击“查看切片”阅读。' : '暂无摘要，请查看原文。') }}</p></div>
      <p class="public-detail-notice">这是公开页面的来源卡片。具体条款、现行效力与适用范围以发布机构原文为准。</p>
      <template #footer><button v-if="publicDetailDocument && publicMetadata.offlineContentType !== '来源索引'" class="primary-button" type="button" @click="publicDetailDialog = false; openOfflineOriginal(publicDetailDocument)">查看本地原文</button><a v-if="publicMetadata.sourceUrl?.startsWith('https://')" :href="publicMetadata.sourceUrl" target="_blank" rel="noopener noreferrer" class="secondary-button public-original-link">访问原网站（需联网）</a><button class="secondary-button" type="button" @click="publicDetailDialog = false">关闭</button></template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.knowledge-doc-head,.knowledge-doc { grid-template-columns: 1.4fr .9fr .6fr 1.2fr; }
.knowledge-use-guide { display: grid; gap: 5px; margin: 0 0 16px; padding: 12px 16px; border: 1px solid #eadbd6; border-radius: 10px; background: #fffaf8; color: #2c3036; font-size: 13px; line-height: 1.6; }
.knowledge-use-guide strong { color: #b8322b; }
.knowledge-use-guide small { color: #666f7b; font-size: 12px; }
.knowledge-source-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.knowledge-doc-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.knowledge-doc-actions button { white-space: nowrap; }
.chunk-intro { margin: 0 0 16px; color: #777; font-size: 13px; }
.knowledge-chunk { padding: 16px 0; border-top: 1px solid #ddd; }
.knowledge-chunk header { display: flex; align-items: center; gap: 12px; }
.knowledge-chunk header small { color: #888; }
.knowledge-chunk header button { margin-left: auto; color: var(--red); border: 0; background: none; cursor: pointer; }
.knowledge-chunk p { white-space: pre-wrap; line-height: 1.8; }
.knowledge-chunk footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.public-resource-overview { display: grid; gap: 12px; padding: 16px 20px; margin-bottom: 16px; border: 1px solid #e5d9d4; border-radius: 12px; background: #fff; }
.public-resource-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: 10px; }
.public-resource-heading strong { font-size: 16px; }
.public-resource-heading span,.public-resource-heading small { color: #6f7680; font-size: 12px; }
.public-resource-links { display: flex; flex-wrap: wrap; gap: 8px; }
.public-resource-links button { border: 1px solid #e0d5d1; border-radius: 8px; background: #faf8f7; color: #30343b; padding: 8px 12px; cursor: pointer; }
.public-resource-links button.active { border-color: #c8322c; color: #b52c27; background: #fff2f0; }
.public-resource-links span { margin-left: 8px; color: #7d838c; }
.public-detail-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.public-detail-meta div { display: grid; gap: 5px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px 12px; }
.public-detail-meta span { color: #757b84; font-size: 12px; }
.public-detail-meta strong { font-size: 14px; font-weight: 500; }
.public-detail-summary { margin-top: 18px; line-height: 1.7; }
.public-detail-summary p { margin: 7px 0 0; }
.public-detail-notice { margin-top: 18px; color: #737983; font-size: 12px; }
.public-original-link { display: inline-flex; align-items: center; text-decoration: none; }
.offline-content-type { color: #24734a; font-weight: 600; }
.offline-original-shortcut { margin-left: 8px; border: 0; background: none; color: #be312a; cursor: pointer; font-weight: 600; }
</style>
