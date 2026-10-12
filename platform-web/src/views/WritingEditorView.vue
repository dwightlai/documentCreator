<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import { initiateReview } from '@/api/review'
import { isConflict } from '@/api/http'
import {
  acquireEditLease,
  addOutlineNode,
  checkOutline,
  confirmOutline,
  createWritingMaterial,
  deleteOutlineNode,
  deleteWritingMaterial,
  downloadWritingDocument,
  generateOutline,
  getCurrentOutline,
  getAiDisclosure,
  listDocumentVersions,
  protectDocumentVersion,
  restoreDocumentVersion,
  type DocumentVersion,
  getWritingDocument,
  getWritingTask,
  listWritingMaterials,
  releaseEditLease,
  setParagraphLock,
  snapshotWritingMaterials,
  streamDocumentSection,
  updateDocumentParagraph,
  updateOutlineNode,
  uploadWritingMaterial,
  type DocumentParagraph,
  type OutlineNode,
  type WritingDocument,
  type WritingMaterial,
  type WritingOutline,
  type WritingTask,
} from '@/api/writing'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => String(route.params.taskId))
const task = ref<WritingTask>()
const materials = ref<WritingMaterial[]>([])
const outline = ref<WritingOutline>()
const document = ref<WritingDocument>()
const selectedNodeId = ref('')
const selectedParagraphId = ref('')
const loading = ref(false)
const generatingId = ref('')
const materialDialog = ref(false)
const materialFile = ref<File>()
const instruction = ref('突出事实依据，避免空泛表述')
const streamText = ref('')
/** 当前持有的段落编辑租约（§34B.1），编辑期间每 4 分钟续期一次。 */
const LEASE_RENEW_MS = 4 * 60 * 1000
const lease = ref<{ paragraphId: string; timer: ReturnType<typeof setInterval> }>()
const leaseBlocked = reactive<Record<string, string>>({})
const versionDialog = ref(false)
const versions = ref<DocumentVersion[]>([])
async function openVersions() {
  try { versions.value = await listDocumentVersions(taskId.value); versionDialog.value = true } catch (error) { ElMessage.error(message(error)) }
}
async function restoreVersion(version:DocumentVersion) {
  try {
    await ElMessageBox.confirm(`将以 V${version.versionNumber} 的内容生成新版本，当前内容仍保留在历史版本中。`, '恢复历史版本')
    document.value = await restoreDocumentVersion(taskId.value, version.versionNumber)
    selectedParagraphId.value = document.value.paragraphs[0]?.id ?? ''
    versions.value = await listDocumentVersions(taskId.value)
    ElMessage.success(`已从 V${version.versionNumber} 恢复为 V${document.value.currentVersion}`)
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(message(error)) }
}
async function toggleProtection(version:DocumentVersion) {
  try {
    const changed = await protectDocumentVersion(taskId.value, version.versionNumber, !version.protectedVersion)
    versions.value = versions.value.map(item => item.versionNumber === changed.versionNumber ? changed : item)
    ElMessage.success(changed.protectedVersion ? '已保护，该版本不会被保留策略清理' : '已取消保护')
  } catch (error) { ElMessage.error(message(error)) }
}
function versionState(version:DocumentVersion) {
  if (version.contentPurged) return { label: '正文已清理', cls: 'purged' }
  if (version.protectedVersion) return { label: '已保护', cls: 'protected' }
  if (!version.retained) return { label: '待清理', cls: 'pending' }
  return null
}
const disclosure = ref<{ aiGeneratedRatio: number; modelVersions: string[] }>()
const includeDisclosure = ref(false)
/** §33 编辑界面标注：蓝色=AI生成/改写/推荐，绿色=引用原文，灰色=人工录入。 */
const ORIGIN_CLASS: Record<string, string> = { AI生成: 'ai', AI改写: 'ai', AI推荐: 'ai', 引用原文: 'quote', 人工录入: 'manual' }
const aiPercent = computed(() => disclosure.value ? Math.round(disclosure.value.aiGeneratedRatio * 100) : 0)
async function refreshDisclosure() { try { disclosure.value = await getAiDisclosure(taskId.value) } catch { disclosure.value = undefined } }
const conflict = ref<{ mine: string; latest: DocumentParagraph; latestDocument: WritingDocument }>()
const materialForm = reactive({ materialName:'', sourceType:'上传', authorityLevel:'正式公文', usageMode:'全文参考', citationPurpose:'事实依据', permissionStatus:'允许', selectedFragment:'', locked:true })

const selectedNode = computed(() => outline.value?.nodes.find((item) => item.id === selectedNodeId.value))
const selectedParagraph = computed(() => document.value?.paragraphs.find((item) => item.id === selectedParagraphId.value))
const allowedMaterials = computed(() => materials.value.filter((item) => item.permissionStatus === '允许').length)

function message(error:unknown) { return error instanceof Error ? error.message : '操作失败，请稍后重试' }
function isNotFound(error:unknown) { return typeof error === 'object' && error !== null && 'response' in error && (error as {response?:{status?:number}}).response?.status === 404 }

async function load() {
  loading.value = true
  try {
    task.value = await getWritingTask(taskId.value)
    materials.value = await listWritingMaterials(taskId.value)
    try { outline.value = await getCurrentOutline(taskId.value); selectedNodeId.value ||= outline.value.nodes[0]?.id ?? '' }
    catch (error) { if (!isNotFound(error)) throw error }
    try { document.value = await getWritingDocument(taskId.value); selectedParagraphId.value ||= document.value.paragraphs[0]?.id ?? '' }
    catch (error) { if (!isNotFound(error)) throw error }
  } catch (error) { ElMessage.error(message(error)) }
  finally { loading.value = false }
}

async function addMaterial() {
  if (!materialFile.value && !materialForm.materialName.trim()) { ElMessage.warning('请选择文件或填写材料名称'); return }
  try {
    if(materialFile.value) await uploadWritingMaterial(taskId.value,materialFile.value,{authorityLevel:materialForm.authorityLevel,usageMode:materialForm.usageMode,citationPurpose:materialForm.citationPurpose,permissionStatus:materialForm.permissionStatus})
    else await createWritingMaterial(taskId.value,{...materialForm})
    materialDialog.value=false; materialFile.value=undefined; materials.value=await listWritingMaterials(taskId.value); ElMessage.success('材料已加入清单并完成文本提取')
  }
  catch(error){ElMessage.error(message(error))}
}
async function removeMaterial(item:WritingMaterial) { try { await deleteWritingMaterial(taskId.value,item.id); materials.value=await listWritingMaterials(taskId.value) } catch(error){ElMessage.error(message(error))} }
async function snapshotMaterials() { try { const result=await snapshotWritingMaterials(taskId.value); ElMessage.success(`材料快照 V${result.snapshotVersion} 已固化：允许 ${result.allowedCount}，拒绝 ${result.deniedCount}`) } catch(error){ElMessage.error(message(error))} }

async function createOutline() {
  try { outline.value=await generateOutline(taskId.value) as WritingOutline; selectedNodeId.value=outline.value.nodes[0]?.id??''; task.value=await getWritingTask(taskId.value); ElMessage.success('已按文种配置生成新提纲版本') }
  catch(error){ElMessage.error(message(error))}
}
async function saveNode(node:OutlineNode) { try { outline.value=await updateOutlineNode(taskId.value,outline.value!.version,node); ElMessage.success('章节已保存') } catch(error){ElMessage.error(message(error))} }
async function appendNode() { if(!outline.value)return; try { outline.value=await addOutlineNode(taskId.value,outline.value.version,outline.value.nodes.length+1); selectedNodeId.value=outline.value.nodes.at(-1)?.id??'' } catch(error){ElMessage.error(message(error))} }
async function removeNode(node:OutlineNode) { if(!outline.value)return; try { outline.value=await deleteOutlineNode(taskId.value,outline.value.version,node.id); selectedNodeId.value=outline.value.nodes[0]?.id??'' } catch(error){ElMessage.error(message(error))} }
async function validateOutline() { if(!outline.value)return; try { outline.value=await checkOutline(taskId.value,outline.value.version); ElMessage.success(outline.value.structureCheckResult) } catch(error){ElMessage.error(message(error))} }
async function lockOutline() { if(!outline.value)return; try { outline.value=await confirmOutline(taskId.value,outline.value.version); task.value=await getWritingTask(taskId.value); ElMessage.success('提纲已人工确认，正文生成门禁已开启') } catch(error){ElMessage.error(message(error))} }

async function generate(node:OutlineNode) {
  generatingId.value=node.id;streamText.value=''
  try { await streamDocumentSection(taskId.value,node.id,instruction.value,(event,data)=>{if(event==='delta')streamText.value+=String(data.text??'')});document.value=await getWritingDocument(taskId.value); selectedParagraphId.value=document.value.paragraphs.at(-1)?.id??''; task.value=await getWritingTask(taskId.value); ElMessage.success('章节已生成，请人工复核') }
  catch(error){ElMessage.error(message(error))}
  finally{generatingId.value=''}
}
async function beginEditing(paragraph:DocumentParagraph) {
  if (paragraph.locked || lease.value?.paragraphId === paragraph.id) return
  await endEditing()
  try {
    await acquireEditLease(taskId.value, paragraph.id)
    delete leaseBlocked[paragraph.id]
    lease.value = { paragraphId: paragraph.id, timer: setInterval(() => renewLease(paragraph.id), LEASE_RENEW_MS) }
  } catch (error) {
    if (isConflict(error)) { leaseBlocked[paragraph.id] = message(error); ElMessage.warning(message(error)) }
    else ElMessage.error(message(error))
  }
}
async function renewLease(paragraphId:string) {
  try { await acquireEditLease(taskId.value, paragraphId) }
  catch { ElMessage.warning('段落编辑租约续期失败，保存时可能与他人修改冲突') }
}
async function endEditing() {
  const current = lease.value
  if (!current) return
  clearInterval(current.timer)
  lease.value = undefined
  try { await releaseEditLease(taskId.value, current.paragraphId) } catch { /* 租约到期后自动释放 */ }
}
function samePosition(a:DocumentParagraph, b:DocumentParagraph) { return a.outlineNodeId === b.outlineNodeId && a.paragraphOrder === b.paragraphOrder }
async function saveParagraph(paragraph:DocumentParagraph) {
  try {
    document.value = await updateDocumentParagraph(taskId.value, paragraph)
    selectedParagraphId.value = document.value.paragraphs.find((item) => samePosition(item, paragraph))?.id ?? ''
    await endEditing()
    ElMessage.success('人工修改已保存为新版本')
  } catch (error) {
    if (!isConflict(error)) { ElMessage.error(message(error)); return }
    try {
      const latestDocument = await getWritingDocument(taskId.value)
      const latest = latestDocument.paragraphs.find((item) => samePosition(item, paragraph))
      if (!latest || latest.plainText === paragraph.plainText) { ElMessage.warning(message(error)); return }
      conflict.value = { mine: paragraph.plainText, latest, latestDocument }
    } catch (reloadError) { ElMessage.error(message(reloadError)) }
  }
}
async function overwriteWithMine() {
  const current = conflict.value
  if (!current) return
  try {
    document.value = await updateDocumentParagraph(taskId.value, { ...current.latest, plainText: current.mine })
    selectedParagraphId.value = document.value.paragraphs.find((item) => samePosition(item, current.latest))?.id ?? ''
    conflict.value = undefined
    await endEditing()
    ElMessage.success('已用你的修改覆盖最新版本')
  } catch (error) { ElMessage.error(message(error)) }
}
async function discardMine() {
  const current = conflict.value
  if (!current) return
  document.value = current.latestDocument
  selectedParagraphId.value = current.latest.id
  conflict.value = undefined
  await endEditing()
  ElMessage.info('已放弃本次修改并载入最新内容')
}
async function toggleLock(paragraph:DocumentParagraph) { try { document.value=await setParagraphLock(taskId.value,paragraph.id,!paragraph.locked); ElMessage.success(paragraph.locked?'段落已解锁':'段落已锁定') } catch(error){ElMessage.error(message(error))} }
async function download(format:'DOCX'|'PDF') { try { const blob=await downloadWritingDocument(taskId.value,format,includeDisclosure.value); const url=URL.createObjectURL(blob); const link=window.document.createElement('a');link.href=url;link.download=`${document.value?.title??'公文'}.${format.toLowerCase()}`;link.click();URL.revokeObjectURL(url) } catch(error){ElMessage.error(message(error))} }
async function startReview(){try{const review=await initiateReview(taskId.value);ElMessage.success('审校任务已创建，正在进入问题工作台');await router.push({path:'/review',query:{taskId:review.id}})}catch(error){ElMessage.error(message(error))}}
function chooseMaterialFile(event:Event){materialFile.value=(event.target as HTMLInputElement).files?.[0]}

onMounted(load)
watch(() => document.value?.currentVersion, (version) => { if (version) refreshDisclosure() })
onBeforeUnmount(endEditing)
</script>

<template>
  <AppShell>
    <template #title>正文协同工作区</template>
    <section class="editor-workspace" v-loading="loading">
      <header class="editor-command" v-if="task">
        <div><RouterLink to="/writing">← 返回任务</RouterLink><span class="classification">{{ task.securityLevel }}</span><h2>{{ task.taskName }}</h2><p>{{ task.documentTypeCode }} · {{ task.writingTopic }}</p></div>
        <div class="editor-status"><span>材料 <b>{{ allowedMaterials }}/{{ materials.length }}</b></span><span>提纲 <b>{{ outline?.confirmed ? '已确认' : outline ? '待确认' : '未生成' }}</b></span><span>正文 <b>V{{ document?.currentVersion ?? 0 }}</b></span><span v-if="disclosure" :title="disclosure.modelVersions.length ? '模型：' + disclosure.modelVersions.join('、') : '平台规则生成'">AI 参与 <b>{{ aiPercent }}%</b></span><label v-if="document" class="disclosure-toggle"><input v-model="includeDisclosure" type="checkbox"> 导出附 AI 说明</label><button v-if="document" type="button" @click="openVersions">版本记录</button><button v-if="document" type="button" @click="startReview">一键发起审校</button><button v-if="document" type="button" @click="download('DOCX')">导出 DOCX</button><button v-if="document" type="button" @click="download('PDF')">PDF</button></div>
      </header>

      <div class="editor-columns" v-if="task">
        <aside class="material-column">
          <header><div><span class="section-no">01 / EVIDENCE</span><h3>材料依据</h3></div><button type="button" @click="materialDialog=true">＋</button></header>
          <p class="column-intro">生成前固化权限和版本，正文只可使用“允许”的材料。</p>
          <article v-for="item in materials" :key="item.id" :data-permission="item.permissionStatus">
            <header><span>{{ item.sourceType }}</span><b>{{ item.permissionStatus }}</b></header><h4>{{ item.materialName }}</h4><p>{{ item.selectedFragment || '未指定材料片段' }}</p><footer><small>{{ item.authorityLevel }} · {{ item.citationPurpose }}</small><button type="button" @click="removeMaterial(item)">移除</button></footer>
          </article>
          <div v-if="!materials.length" class="editor-empty">尚无材料。可先形成提纲，但生成正文前建议加入可靠依据。</div>
          <button v-if="materials.length" class="snapshot-button" type="button" @click="snapshotMaterials">固化材料权限快照</button>
        </aside>

        <aside class="outline-column">
          <header><div><span class="section-no">02 / STRUCTURE</span><h3>写作提纲</h3></div><button type="button" @click="createOutline">{{ outline ? '新版本' : '生成' }}</button></header>
          <p class="column-intro">章节可编辑；确认后锁定当前版本并开放正文生成。</p>
          <template v-if="outline">
            <div class="outline-meta"><span>V{{ outline.version }}</span><b :class="{confirmed:outline.confirmed}">{{ outline.confirmed?'已确认':'编辑中' }}</b></div>
            <article v-for="node in outline.nodes" :key="node.id" :class="{active:selectedNodeId===node.id}" @click="selectedNodeId=node.id">
              <span class="node-order">{{ String(node.order).padStart(2,'0') }}</span><div><input v-model="node.title" :disabled="outline.confirmed" @click.stop><textarea v-model="node.purpose" :disabled="outline.confirmed" rows="2" @click.stop /></div><button v-if="!outline.confirmed" type="button" @click.stop="saveNode(node)">存</button><button v-if="!outline.confirmed" type="button" @click.stop="removeNode(node)">×</button>
            </article>
            <button v-if="!outline.confirmed" class="append-node" type="button" @click="appendNode">＋ 添加章节</button>
            <footer class="outline-actions"><button v-if="!outline.confirmed" type="button" @click="validateOutline">结构检查</button><button v-if="!outline.confirmed" class="confirm" type="button" @click="lockOutline">确认提纲</button></footer>
          </template>
          <div v-else class="editor-empty">生成提纲后可逐节调整写作目的、顺序和篇幅。</div>
        </aside>

        <main class="document-column">
          <header><div><span class="section-no">03 / MANUSCRIPT</span><h3>正文纸面</h3></div><span v-if="document">{{ document.totalWordCount }} 字 · V{{ document.currentVersion }}</span></header>
          <div v-if="selectedNode" class="generation-bar"><div><strong>{{ selectedNode.title }}</strong><input v-model="instruction" aria-label="生成要求"></div><button type="button" :disabled="!outline?.confirmed||generatingId===selectedNode.id" @click="generate(selectedNode)">{{ !outline?.confirmed?'请先确认提纲':generatingId===selectedNode.id?'生成中…':'生成本节' }}</button></div>
          <div v-if="generatingId && streamText" class="stream-preview"><span>LIVE / 流式草稿</span><p>{{ streamText }}</p></div>
          <section v-if="document?.paragraphs.length" class="paper-sheet">
            <header><span>{{ task.documentCategory }}</span><h1>{{ document.title }}</h1><small>{{ document.securityLevel }} · AI 辅助内容须人工复核</small></header>
            <article v-for="paragraph in document.paragraphs" :key="paragraph.id" :class="{active:selectedParagraphId===paragraph.id,dimmed:selectedNodeId&&paragraph.outlineNodeId!==selectedNodeId}" @click="selectedParagraphId=paragraph.id">
              <div class="paragraph-meta"><span><i v-if="paragraph.contentOrigin" class="origin-tag" :class="ORIGIN_CLASS[paragraph.contentOrigin] ?? 'manual'" :title="'证据层级：' + paragraph.contentSourceLevel + (paragraph.sourceMaterialIds.length ? '；引用材料 ' + paragraph.sourceMaterialIds.length + ' 项' : '')">{{ paragraph.contentOrigin }}</i>{{ paragraph.contentSourceLevel }}</span><b>{{ paragraph.locked?'已锁定':leaseBlocked[paragraph.id]?'他人编辑中':lease?.paragraphId===paragraph.id?'编辑中':'可编辑' }}</b></div><p v-if="leaseBlocked[paragraph.id]" class="lease-blocked">{{ leaseBlocked[paragraph.id] }}</p><textarea v-model="paragraph.plainText" :disabled="paragraph.locked" :readonly="!!leaseBlocked[paragraph.id]" @focus="beginEditing(paragraph)" rows="8" @click.stop /><footer><span>第 {{ paragraph.paragraphOrder }} 段 · {{ paragraph.plainText.length }} 字</span><div><button type="button" @click.stop="toggleLock(paragraph)">{{ paragraph.locked?'解锁':'锁定' }}</button><button v-if="!paragraph.locked" type="button" @click.stop="saveParagraph(paragraph)">保存新版本</button></div></footer>
            </article>
          </section>
          <div v-else class="document-empty"><span>稿</span><h3>正文尚未形成</h3><p>确认提纲后，在中栏选择章节并逐段生成。每次人工修改都会形成不可变版本。</p></div>
          <aside v-if="selectedParagraph" class="paragraph-inspector"><span>当前段落</span><b>{{ selectedParagraph.contentSourceLevel }}</b><small>{{ selectedParagraph.sourceMaterialIds.length }} 项来源 · lock {{ selectedParagraph.lockVersion }}</small></aside>
        </main>
      </div>
    </section>

    <el-dialog v-model="materialDialog" title="加入写作材料" width="620px">
      <div class="material-form"><label class="file-drop">上传文件（DOCX / PDF / TXT，最大 20MB）<input type="file" accept=".docx,.pdf,.txt" @change="chooseMaterialFile"><span>{{ materialFile?.name || '选择本地文件，系统将提取文本并保存原文件哈希' }}</span></label><div class="manual-divider">或录入经核准的材料片段</div><label>材料名称<input v-model="materialForm.materialName" :disabled="Boolean(materialFile)" placeholder="例如：项目论证报告"></label><div><label>来源<select v-model="materialForm.sourceType" :disabled="Boolean(materialFile)"><option>上传</option><option>知识库</option><option>OA</option></select></label><label>权威级别<select v-model="materialForm.authorityLevel"><option>正式公文</option><option>权威资料</option><option>内部资料</option><option>参考资料</option></select></label></div><div><label>使用方式<select v-model="materialForm.usageMode"><option>全文参考</option><option>指定片段</option><option>仅作背景</option></select></label><label>引用目的<select v-model="materialForm.citationPurpose"><option>事实依据</option><option>政策依据</option><option>案例参考</option><option>表达参考</option></select></label></div><label>选用片段<textarea v-model="materialForm.selectedFragment" :disabled="Boolean(materialFile)" rows="5" placeholder="粘贴经核准、可供生成使用的材料片段" /></label></div>
      <template #footer><button class="secondary-button" type="button" @click="materialDialog=false">取消</button><button class="primary-button" type="button" @click="addMaterial">加入清单</button></template>
    </el-dialog>
    <el-dialog v-model="versionDialog" title="版本记录" width="720px">
      <p class="conflict-intro">AI 生成版本保留 30 天、每篇最多 50 个，到期标记 7 天后清理正文；人工提交、大纲确认、审校前后版本永久保留。重要版本可手动保护。</p>
      <div class="version-list">
        <article v-for="version in versions" :key="version.versionNumber" :class="{ current: version.versionNumber === document?.currentVersion }">
          <b>V{{ version.versionNumber }}</b>
          <div><strong>{{ version.changeDescription || version.changeSource }}</strong><small>{{ version.versionType }} · {{ new Date(version.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</small></div>
          <span v-if="versionState(version)" class="version-state" :class="versionState(version)!.cls">{{ versionState(version)!.label }}</span>
          <span v-if="version.versionNumber === document?.currentVersion" class="version-state current">当前</span>
          <footer>
            <button v-if="!version.contentPurged && version.versionNumber !== document?.currentVersion" type="button" @click="restoreVersion(version)">恢复</button>
            <button v-if="!version.contentPurged" type="button" @click="toggleProtection(version)">{{ version.protectedVersion ? '取消保护' : '保护' }}</button>
          </footer>
        </article>
      </div>
    </el-dialog>
    <el-dialog :model-value="!!conflict" title="段落已被他人修改" width="760px" @close="conflict=undefined">
      <p class="conflict-intro">你编辑期间，该段落已形成新版本。请比对后选择：用你的修改覆盖最新版本，或放弃你的修改。系统不会自动合并。</p>
      <div v-if="conflict" class="conflict-compare">
        <section><h4>你的修改</h4><p>{{ conflict.mine }}</p></section>
        <section><h4>当前最新版本</h4><p>{{ conflict.latest.plainText }}</p></section>
      </div>
      <template #footer><button class="secondary-button" type="button" @click="conflict=undefined">继续编辑</button><button class="secondary-button" type="button" @click="discardMine">放弃我的修改</button><button class="primary-button" type="button" @click="overwriteWithMine">用我的修改覆盖</button></template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.version-list{display:grid;gap:8px;max-height:460px;overflow:auto}
.version-list article{display:grid;grid-template-columns:48px 1fr auto auto auto;gap:10px;align-items:center;padding:10px 12px;border:1px solid #e5e7eb;border-radius:10px}
.version-list article.current{border-color:#f6cfcb;background:#fdf0ef}
.version-list b{color:#c4302b}
.version-list div{display:grid;gap:2px;min-width:0}
.version-list strong{font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.version-list small{color:#9aa0a8;font-size:12px}
.version-list footer{display:flex;gap:6px}
.version-list footer button{padding:4px 10px;border:1px solid #e5e7eb;border-radius:6px;background:#fff;cursor:pointer;font-size:13px}
.version-state{padding:1px 8px;border-radius:999px;font-size:12px;background:#f0f2f5;color:#646a73}
.version-state.protected{background:#e8f5ee;color:#18794e}
.version-state.pending{background:#fff4e5;color:#9a5b13}
.version-state.purged{background:#f0f2f5;color:#9aa0a8}
.version-state.current{background:#fdf0ef;color:#c4302b}
.origin-tag{margin-right:8px;padding:1px 8px;border-radius:999px;font-style:normal;font-size:12px;cursor:help}
.origin-tag.ai{background:#e8f0fe;color:#1a56db}
.origin-tag.quote{background:#e8f5ee;color:#18794e}
.origin-tag.manual{background:#f0f2f5;color:#646a73}
.disclosure-toggle{display:flex;align-items:center;gap:6px;padding:0 10px;color:#c3cadb;font-size:13px;cursor:pointer}
.lease-blocked{margin:6px 0 0;padding:6px 10px;border-radius:6px;background:#fff4e5;color:#9a5b13;font-size:13px}
.conflict-intro{margin:0 0 14px;color:#646a73;font-size:14px;line-height:1.7}
.conflict-compare{display:grid;grid-template-columns:1fr 1fr;gap:14px}
.conflict-compare section{padding:14px;border:1px solid #e5e7eb;border-radius:10px;background:#f8f9fb}
.conflict-compare h4{margin:0 0 8px;font-size:14px}
.conflict-compare p{margin:0;white-space:pre-wrap;font-size:14px;line-height:1.8;max-height:320px;overflow:auto}
.editor-workspace{padding:24px 28px 60px}.editor-command{background:#17231e;color:#f3efe7;padding:20px 26px;display:flex;align-items:flex-end;justify-content:space-between;gap:24px}.editor-command a{color:#9fb0a7;font-size:13px;margin-right:14px}.editor-command h2{font:500 25px/1.2 "Noto Sans SC",sans-serif;margin:10px 0 5px}.editor-command p{margin:0;color:#aebbb5;font-size:14px}.editor-status{display:flex;align-items:center;gap:8px}.editor-status span{display:grid;padding:0 14px;border-right:1px solid #4b5751;color:#9fb0a7;font-size:13px}.editor-status b{color:#fff;margin-top:4px;font-size:14px}.editor-status button{border:1px solid #728078;background:transparent;color:#fff;padding:9px 12px;cursor:pointer}.editor-columns{display:grid;grid-template-columns:280px 340px minmax(500px,1fr);min-height:720px;border:1px solid #d8d3c8;border-top:0;background:#f1eee7}.material-column,.outline-column{border-right:1px solid #d8d3c8;padding:22px 18px;overflow:auto;max-height:calc(100vh - 190px)}.material-column>header,.outline-column>header,.document-column>header{display:flex;justify-content:space-between;align-items:center}.material-column h3,.outline-column h3,.document-column h3{font:600 20px/1 "Noto Sans SC",sans-serif;margin:7px 0}.material-column>header button,.outline-column>header button{width:34px;height:34px;border:1px solid #aaa397;background:transparent;cursor:pointer}.column-intro{color:#858078;font-size:13px;line-height:1.65;border-bottom:1px solid #d8d3c8;padding-bottom:16px}.material-column>article{background:#fff;border-left:3px solid #567363;padding:14px;margin-bottom:10px}.material-column>article[data-permission=拒绝]{border-color:#a33c32}.material-column article header{display:flex;justify-content:space-between;font-size:13px;color:#89837a}.material-column article h4{font-size:14px;margin:9px 0}.material-column article p{font-size:13px;color:#706c65;line-height:1.6;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.material-column article footer{display:flex;justify-content:space-between;align-items:end}.material-column article footer small{font-size:12px;color:#999}.material-column article button{border:0;background:none;color:#a33c32;cursor:pointer;font-size:13px}.snapshot-button,.append-node{width:100%;border:1px dashed #9c9589;background:transparent;padding:11px;cursor:pointer;color:#59564f}.outline-meta{display:flex;justify-content:space-between;margin:13px 0;font-size:13px}.outline-meta b{color:#a33c32}.outline-meta b.confirmed{color:#48705a}.outline-column>article{display:grid;grid-template-columns:30px 1fr 25px 20px;gap:6px;padding:12px 8px;border-top:1px solid #ddd8cf;cursor:pointer}.outline-column>article.active{background:#fff;border-left:2px solid #a33c32}.node-order{font:500 15px sans-serif;color:#a8a197}.outline-column article input,.outline-column article textarea{width:100%;box-sizing:border-box;border:0;background:transparent;resize:none;font:inherit}.outline-column article input{font-weight:700;color:#272d29}.outline-column article textarea{font-size:13px;color:#77736b;margin-top:7px;line-height:1.5}.outline-column article button{border:0;background:none;color:#8e877d;cursor:pointer}.outline-actions{display:flex;gap:8px;padding-top:16px}.outline-actions button{flex:1;border:1px solid #aaa397;background:#fff;padding:10px;cursor:pointer}.outline-actions button.confirm{background:#17231e;color:#fff;border-color:#17231e}.document-column{padding:22px 28px;position:relative;overflow:auto;max-height:calc(100vh - 190px)}.document-column>header>span{font-size:13px;color:#77736b}.generation-bar{display:flex;align-items:end;justify-content:space-between;gap:16px;background:#e5e0d6;padding:12px 16px;margin:18px 0}.generation-bar div{display:grid;gap:5px;flex:1}.generation-bar strong{font-size:14px}.generation-bar input{border:0;border-bottom:1px solid #aaa397;background:transparent;padding:5px 0}.generation-bar button{background:#a33c32;color:#fff;border:0;padding:11px 16px;cursor:pointer}.generation-bar button:disabled{background:#aaa69f}.paper-sheet{background:#fff;max-width:760px;margin:0 auto;padding:58px 72px;box-shadow:0 8px 24px rgba(55,50,40,.08)}.paper-sheet>header{text-align:center;border-bottom:1px solid #eee9df;padding-bottom:30px}.paper-sheet>header span,.paper-sheet>header small{font-size:13px;color:#999}.paper-sheet h1{font:600 26px/1.45 "Noto Sans SC",sans-serif}.paper-sheet>article{padding:24px 0;border-bottom:1px solid #eee9df}.paper-sheet>article.dimmed{opacity:.32}.paper-sheet>article.active{box-shadow:-3px 0 #a33c32;padding-left:18px}.paragraph-meta{display:flex;justify-content:space-between;font-size:12px;color:#999}.paper-sheet textarea{border:0;width:100%;box-sizing:border-box;resize:vertical;font:16px/2 "Noto Sans SC",sans-serif;color:#292d2a;margin-top:8px}.paper-sheet article footer{display:flex;justify-content:space-between;color:#999;font-size:12px}.paper-sheet article footer button{border:0;background:none;color:#6d6962;cursor:pointer;margin-left:10px}.document-empty{text-align:center;padding:130px 30px;color:#817c73}.document-empty>span{display:inline-grid;place-items:center;width:70px;height:70px;border:1px solid #aaa397;font:32px sans-serif}.document-empty h3{font:500 22px sans-serif}.paragraph-inspector{position:sticky;bottom:0;margin:20px 0 0 auto;background:#17231e;color:#fff;padding:10px 16px;width:max-content;display:flex;gap:12px;font-size:13px}.paragraph-inspector small{color:#9fb0a7}.editor-empty{padding:35px 12px;text-align:center;color:#918b82;font-size:13px;line-height:1.7}.material-form{display:grid;gap:15px}.material-form>div{display:grid;grid-template-columns:1fr 1fr;gap:14px}.material-form label{display:grid;gap:6px;color:#6c6861;font-size:14px}.material-form input,.material-form select,.material-form textarea{border:1px solid #c9c3b7;background:#fbfaf7;padding:10px;font:inherit;resize:vertical}.file-drop{border:1px dashed #9b9488;padding:18px;background:#f4f1ea;cursor:pointer}.file-drop span{color:#8a847b}.manual-divider{text-align:center!important;display:block!important;color:#aaa39a;font-size:13px}.manual-divider:before,.manual-divider:after{content:"";display:inline-block;width:32%;border-top:1px solid #ddd7cc;vertical-align:middle;margin:0 10px}@media(max-width:1200px){.editor-columns{grid-template-columns:240px 290px minmax(430px,1fr)}}
.stream-preview{max-width:760px;margin:0 auto 14px;background:#17231e;color:#f5f0e7;padding:18px 24px}.stream-preview span{font-size:12px;letter-spacing:1.5px;color:#d8b36a}.stream-preview p{font:14px/1.9 "Noto Sans SC",sans-serif;white-space:pre-wrap;margin:10px 0 0}</style>
