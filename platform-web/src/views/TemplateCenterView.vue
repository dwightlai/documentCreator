<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import { templateExample } from '@/data/templateExample'
import { listDocumentTypes, type DocumentTypeConfig } from '@/api/governance'
import { useDict } from '@/composables/useDict'
import { createOutputTemplate, deleteOutputTemplate, downloadCompleteExample, downloadOutputTemplate, downloadStandardTemplate, listOutputTemplates, listTemplateUsages, listTemplateVersions, previewOutputTemplate, publishOutputTemplate, replaceOutputTemplateFile, testOutputTemplate, updateOutputTemplate, type OutputTemplate, type TemplatePreviewBlock, type TemplateUsage, type TemplateVersion } from '@/api/templates'
const { options: directionOptions } = useDict('document_direction', ['上行', '下行', '平行', '内部'])

const templates = ref<OutputTemplate[]>([])
const versions = ref<TemplateVersion[]>([])
const usages = ref<TemplateUsage[]>([])
const documentTypes = ref<DocumentTypeConfig[]>([])
const documentTypeOptions = computed(() => {
  const codes = documentTypes.value.map(item => item.typeCode).filter(Boolean)
  const fallback = ['请示', '报告', '通知', '函', '纪要', '通报']
  const current = [form.documentTypeCode, editForm.documentTypeCode].filter(Boolean)
  return [...new Set([...codes, ...fallback, ...current])]
})
const selectedId = ref('')
const loading = ref(false)
const dialog = ref(false)
const editDialog = ref(false)
const replaceDialog = ref(false)
const replacementFile = ref<File>()
const changeNote = ref('更新模板文件')
const exampleDialog = ref(false)
const previewDialog = ref(false)
const previewLoading = ref(false)
const previewBlocks = ref<TemplatePreviewBlock[]>([])
const file = ref<File>()
const selected = computed(() => templates.value.find((item) => item.id === selectedId.value))
const form = reactive({ templateCode: '', templateName: '', documentTypeCode: '请示', hasRedHeader: true, hasFooterMark: true, direction: '上行', description: '' })
const editForm = reactive({ templateName: '', documentTypeCode: '', hasRedHeader: true, hasFooterMark: true, direction: '', description: '' })

function openEdit() {
  if (!selected.value) return
  Object.assign(editForm, { templateName: selected.value.templateName, documentTypeCode: selected.value.documentTypeCode,
    hasRedHeader: selected.value.hasRedHeader, hasFooterMark: selected.value.hasFooterMark,
    direction: selected.value.direction ?? '', description: selected.value.description ?? '' })
  editDialog.value = true
}
async function saveEdit() {
  const item = selected.value
  if (!item) return
  try {
    const updated = await updateOutputTemplate(item.id, { ...editForm, lockVersion: item.lockVersion })
    templates.value = templates.value.map(value => value.id === updated.id ? updated : value)
    editDialog.value = false; ElMessage.success('模板信息已更新')
  } catch (error) { fail(error, '模板信息保存失败') }
}
async function replaceFile() {
  const item = selected.value
  if (!item || !replacementFile.value) { ElMessage.warning('请选择 DOCX 文件'); return }
  try {
    const updated = await replaceOutputTemplateFile(item.id, replacementFile.value, changeNote.value)
    templates.value = templates.value.map(value => value.id === updated.id ? updated : value)
    await loadVersions(); replaceDialog.value = false; replacementFile.value = undefined
    ElMessage.success('模板文件已更新，请重新测试后发布')
  } catch (error) { fail(error, '模板文件更新失败') }
}

function fail(reason: unknown, fallback: string) { ElMessage.error(reason instanceof Error ? reason.message : fallback) }
function saveDocx(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
async function getCompleteExample() {
  try { saveDocx(await downloadCompleteExample(), '完整请示示例.docx'); ElMessage.success('完整公文示例已开始下载') }
  catch (error) { fail(error, '示例公文下载失败') }
}
async function getStandardTemplate() {
  try { saveDocx(await downloadStandardTemplate(), '请示输出模板.docx'); ElMessage.success('示例模板已开始下载') }
  catch (error) { fail(error, '示例模板下载失败') }
}
async function preview() {
  if (!selected.value) return
  previewLoading.value = true
  previewDialog.value = true
  try { previewBlocks.value = await previewOutputTemplate(selected.value.id) }
  catch (error) { fail(error, '模板预览失败'); previewDialog.value = false }
  finally { previewLoading.value = false }
}
async function downloadOriginal() {
  if (!selected.value) return
  try { saveDocx(await downloadOutputTemplate(selected.value.id), `${selected.value.templateCode}-原模板.docx`); ElMessage.success('原模板已开始下载') }
  catch (error) { fail(error, '原模板下载失败') }
}
async function remove() {
  if (!selected.value) return
  const item = selected.value
  try {
    await loadUsages()
    const blockers = usages.value.filter(u => u.kind !== '套红定稿')
    if (blockers.length) {
      const names = blockers.slice(0, 5).map(u => `${u.kind}「${u.title}」`).join('、')
      ElMessage.warning(`无法删除：已被 ${names}${blockers.length > 5 ? ` 等 ${blockers.length} 项` : ''}引用。请先在对应任务中更换模板。`)
      return
    }
    await ElMessageBox.confirm(`确定删除“${item.templateName}”吗？`, '删除模板', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteOutputTemplate(item.id)
    selectedId.value = ''
    versions.value = []
    usages.value = []
    await load()
    ElMessage.success('模板已删除')
  } catch (error) { if (error !== 'cancel') fail(error, '模板删除失败') }
}
async function loadUsages() {
  usages.value = selectedId.value ? await listTemplateUsages(selectedId.value) : []
}
async function load() {
  loading.value = true
  try {
    const [list, types] = await Promise.all([listOutputTemplates(), listDocumentTypes().catch(() => [] as DocumentTypeConfig[])])
    templates.value = list
    documentTypes.value = types
    if (!templates.value.some((item) => item.id === selectedId.value)) selectedId.value = templates.value[0]?.id ?? ''
  } catch (error) { fail(error, '模板加载失败') }
  finally { loading.value = false }
}
async function loadVersions() { versions.value = selectedId.value ? await listTemplateVersions(selectedId.value) : [] }
async function create() {
  if (!file.value) { ElMessage.warning('请选择 DOCX 模板文件'); return }
  try {
    const value = await createOutputTemplate(file.value, { ...form })
    templates.value.unshift(value)
    selectedId.value = value.id
    dialog.value = false
    file.value = undefined
    ElMessage.success('模板已上传并通过安全检查')
  } catch (error) { fail(error, '模板上传失败') }
}
async function test() {
  if (!selected.value) return
  try {
    const blob = await testOutputTemplate(selected.value.id)
    saveDocx(blob, `${selected.value.templateCode}-示例填充.docx`)
    await load()
    await loadVersions()
    ElMessage.success('已用上方完整示例填充模板并下载 DOCX')
  } catch (error) { fail(error, '模拟填充失败') }
}
async function publish() {
  if (!selected.value) return
  try {
    const updated = await publishOutputTemplate(selected.value.id)
    const index = templates.value.findIndex((item) => item.id === updated.id)
    templates.value[index] = updated
    await loadVersions()
    ElMessage.success('模板已发布生效')
  } catch (error) { fail(error, '模板发布失败') }
}
watch(selectedId, () => {
  loadVersions().catch((error) => fail(error, '版本加载失败'))
  loadUsages().catch((error) => fail(error, '引用加载失败'))
})
onMounted(load)
</script>

<template>
  <AppShell>
    <template #title>输出模板中心</template>
    <section class="template-center" v-loading="loading">
      <header class="template-hero">
        <div><h2>输出模板</h2><p>上传、预览并测试 DOCX 模板，通过模拟填充后发布。</p></div>
        <div class="template-hero-actions"><button class="secondary-button" type="button" @click="exampleDialog = true">查看完整公文示例</button><button class="primary-button" type="button" @click="dialog = true">上传 DOCX 模板</button></div>
      </header>

      <div class="template-grid">
        <aside class="template-list"><header><span class="section-no">已上传模板</span><b>{{ templates.length }}</b></header><button v-for="item in templates" :key="item.id" type="button" :class="{ active: item.id === selectedId }" @click="selectedId = item.id"><span>{{ item.documentTypeCode.slice(0, 1) }}</span><div><strong>{{ item.templateName }}</strong><small>{{ item.templateCode }} · V{{ item.currentVersion }}</small></div><i :data-status="item.lifecycleStatus">{{ item.lifecycleStatus }}</i></button><div v-if="!templates.length" class="governance-empty">尚未上传输出模板。</div></aside>
        <main v-if="selected" class="template-detail">
          <header><div><span class="section-no">{{ selected.direction || '通用' }} / {{ selected.documentTypeCode }}</span><h3>{{ selected.templateName }}</h3><p>{{ selected.description }}</p></div><b :data-status="selected.lifecycleStatus">{{ selected.lifecycleStatus }}</b></header>
          <div class="template-quick-actions"><button class="primary-button" type="button" @click="preview">预览原模板</button><button class="secondary-button" type="button" @click="downloadOriginal">下载原模板</button><button class="secondary-button" type="button" @click="openEdit">编辑信息</button><button class="secondary-button" type="button" @click="replaceDialog = true">更新 DOCX</button><button class="secondary-button" type="button" @click="test">模拟填充并下载</button><button class="template-delete" type="button" @click="remove">删除模板</button></div>
          <dl class="template-meta"><div><dt>当前版本</dt><dd>V{{ selected.currentVersion }}</dd></div><div><dt>红色版头</dt><dd>{{ selected.hasRedHeader ? '包含' : '不包含' }}</dd></div><div><dt>版记区域</dt><dd>{{ selected.hasFooterMark ? '包含' : '不包含' }}</dd></div><div><dt>文件指纹</dt><dd><code>{{ selected.templateFileHash.slice(0, 12) }}…</code></dd></div></dl>
          <section class="placeholder-sheet"><header><span class="section-no">模板字段</span><h4>占位符映射</h4></header><div><article v-for="(meaning, key) in selected.placeholderMap" :key="key"><code>{{ key }}</code><span>{{ meaning }}</span></article></div></section>
          <p class="template-field-hint">上方只列出当前 DOCX 实际包含的字段。缺少发文机关、文号等字段时，模拟填充也不会自动增加这些内容。</p>
          <section class="template-versions"><header><span class="section-no">引用情况</span><h4>使用此模板的任务与定稿{{ usages.length ? `（${usages.length}）` : '' }}</h4></header>
            <article v-for="usage in usages" :key="usage.kind + usage.refId"><b>{{ usage.kind }}</b><div><strong>{{ usage.title }}</strong><small>{{ usage.status }} · {{ usage.refId }}</small></div></article>
            <div v-if="!usages.length" class="governance-empty">当前没有写作/审校任务或套红定稿引用此模板，可以删除。</div>
          </section>
          <section class="template-versions"><header><span class="section-no">版本记录</span><h4>文件与发布记录</h4></header><article v-for="version in versions" :key="version.id"><b>V{{ version.versionNumber }}</b><div><strong>{{ version.changeNote }}</strong><small>{{ version.lifecycleStatus }} · {{ new Date(version.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</small></div><span>{{ version.approvedBy || '待审批' }}</span></article></section>
          <footer><button v-if="selected.lifecycleStatus === '测试'" class="primary-button" type="button" @click="publish">发布生效</button><span v-else-if="selected.lifecycleStatus === '草稿'">请先下载并检查模拟填充结果</span></footer>
        </main>
        <main v-else class="template-detail template-empty">上传一份 DOCX 模板后，可预览原文件并用完整公文示例进行模拟填充。</main>
      </div>
    </section>
    <el-dialog v-model="exampleDialog" title="完整公文示例 · 请示" width="min(960px, 94vw)" class="template-example-dialog" destroy-on-close>
      <div class="template-dialog-scroll">
      <section class="template-example" aria-labelledby="template-example-title">
        <header><div><span class="section-no">完整公文示例 · 请示</span><h3 id="template-example-title">模拟填充使用的成文内容</h3><p>以下机关、文号、事项和日期均为演示数据，不代表真实发文。最终 DOCX 的版式由所选模板决定。</p></div><span class="template-example-badge">演示文本</span></header>
        <div class="template-example-actions"><button class="secondary-button" type="button" @click="getCompleteExample">下载完整示例 DOCX</button><button class="secondary-button" type="button" @click="getStandardTemplate">下载可上传的示例模板</button></div>
        <article class="example-paper">
          <div class="example-sender">{{ templateExample.sender }}</div>
          <div class="example-doc-number">{{ templateExample.docNumber }}<span>签发人：{{ templateExample.signer }}</span></div>
          <h4>{{ templateExample.title }}</h4>
          <p class="example-recipient">{{ templateExample.recipient }}</p>
          <p v-for="paragraph in templateExample.paragraphs" :key="paragraph" class="example-body">{{ paragraph }}</p>
          <p class="example-body">{{ templateExample.closing }}</p>
          <p class="example-attachment">附件：1. {{ templateExample.attachment }}</p>
          <p class="example-date">{{ templateExample.sender }}<br>{{ templateExample.date }}</p>
          <div class="example-imprint"><p>抄送：{{ templateExample.copyTo }}。</p><p>{{ templateExample.issueUnit }}<span>{{ templateExample.issueDate }}印发</span></p></div>
        </article>
        <p class="template-example-note">对应关系：<code>{title}</code> 填入公文标题，<code>{content}</code> 填入五段正文；发文机关、文号、主送机关、附件、日期和版记须在 DOCX 中设置各自的占位符，才能出现在下载文件中。</p>
      </section>
      </div>
    </el-dialog>
    <el-dialog v-model="previewDialog" :title="`原模板预览 · ${selected?.templateName || ''}`" width="min(900px, 94vw)" class="template-preview-dialog" destroy-on-close>
      <div v-loading="previewLoading" class="template-preview-content template-dialog-scroll"><p class="template-preview-note">这里展示已上传 DOCX 的正文内容及主要文字格式。页眉、页脚和精确分页请下载原模板核对。</p><article class="template-preview-paper"><template v-for="(block, index) in previewBlocks" :key="index"><p v-if="block.type === 'paragraph'" :style="{ textAlign: block.align === 'CENTER' ? 'center' : block.align === 'RIGHT' ? 'right' : 'left', fontSize: block.fontSize && block.fontSize > 0 ? `${block.fontSize}pt` : undefined, fontWeight: block.bold ? 700 : 400, color: block.color ? `#${block.color}` : undefined }">{{ block.text }}</p><table v-else><tbody><tr v-for="(row, rowIndex) in block.rows" :key="rowIndex"><td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td></tr></tbody></table></template><p v-if="!previewBlocks.length && !previewLoading">模板正文为空，请下载原文件核对页眉和页脚。</p></article></div>
      <template #footer><button class="secondary-button" type="button" @click="downloadOriginal">下载原模板</button><button class="primary-button" type="button" @click="previewDialog = false">关闭</button></template>
    </el-dialog>
    <el-dialog v-model="dialog" title="上传输出模板" width="620px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="模板代码"><el-input v-model="form.templateCode" /></el-form-item><el-form-item label="模板名称"><el-input v-model="form.templateName" /></el-form-item></div><div class="form-pair"><el-form-item label="适用文种"><el-select v-model="form.documentTypeCode"><el-option v-for="value in documentTypeOptions" :key="value" :label="value" :value="value" /></el-select></el-form-item><el-form-item label="行文方向"><el-select v-model="form.direction"><el-option v-for="value in directionOptions" :key="value" :value="value" /></el-select></el-form-item></div><el-form-item label="DOCX 文件"><input type="file" accept=".docx" @change="file = ($event.target as HTMLInputElement).files?.[0]" /></el-form-item><el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item><div class="form-pair"><el-checkbox v-model="form.hasRedHeader">包含红色版头</el-checkbox><el-checkbox v-model="form.hasFooterMark">包含版记区域</el-checkbox></div></el-form><template #footer><button class="secondary-button" type="button" @click="dialog = false">取消</button><button class="primary-button" type="button" @click="create">上传并检查</button></template></el-dialog>
    <el-dialog v-model="editDialog" title="编辑输出模板信息" width="560px" class="task-dialog"><el-form label-position="top"><div class="form-pair"><el-form-item label="模板名称"><el-input v-model="editForm.templateName" /></el-form-item><el-form-item label="适用文种"><el-select v-model="editForm.documentTypeCode"><el-option v-for="value in documentTypeOptions" :key="value" :label="value" :value="value" /></el-select></el-form-item></div><el-form-item label="行文方向"><el-select v-model="editForm.direction"><el-option v-for="value in directionOptions" :key="value" :value="value" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="editForm.description" type="textarea" /></el-form-item><el-checkbox v-model="editForm.hasRedHeader">包含红色版头</el-checkbox><el-checkbox v-model="editForm.hasFooterMark">包含版记区域</el-checkbox></el-form><template #footer><button class="secondary-button" type="button" @click="editDialog = false">取消</button><button class="primary-button" type="button" @click="saveEdit">保存修改</button></template></el-dialog>
    <el-dialog v-model="replaceDialog" title="更新 DOCX 模板文件" width="520px" class="task-dialog"><p>上传新文件将形成新版本，发布前需要重新模拟填充并核对。</p><input type="file" accept=".docx" @change="replacementFile = ($event.target as HTMLInputElement).files?.[0]" /><el-form-item label="变更说明"><el-input v-model="changeNote" /></el-form-item><template #footer><button class="secondary-button" type="button" @click="replaceDialog = false">取消</button><button class="primary-button" type="button" @click="replaceFile">更新文件</button></template></el-dialog>
  </AppShell>
</template>

<style scoped>
.template-hero{min-height:170px}.template-guide{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:18px 0}.template-guide>div{display:grid;grid-template-columns:auto 1fr;gap:4px 10px;padding:16px;background:#fff;border:1px solid var(--line);border-radius:10px}.template-guide b{grid-row:1/3;color:var(--red);font-size:20px}.template-guide strong{font-size:14px}.template-guide span{font-size:12px;color:var(--muted);line-height:1.55}.template-guide code{color:var(--red)}
.template-example{padding:24px;background:#fff;border:1px solid var(--line);border-radius:var(--radius);box-shadow:var(--shadow)}.template-example>header{display:flex;justify-content:space-between;gap:20px;align-items:start}.template-example h3{margin:6px 0;font-size:20px}.template-example>header p,.template-example-note{margin:0;color:var(--muted);font-size:13px;line-height:1.7}.template-example-badge{padding:5px 10px;background:var(--red-soft);color:var(--red);font-size:12px;white-space:nowrap}
.template-example-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:16px}
.template-hero-actions,.template-quick-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.template-grid{margin-top:12px}.template-quick-actions{margin:12px 0 16px}.template-delete{margin-left:auto;border:1px solid #e7b4ae;background:#fff;color:var(--red);border-radius:7px;padding:8px 14px;cursor:pointer}.template-delete:hover{background:var(--red-soft)}
.template-dialog-scroll{max-height:calc(100vh - 230px);overflow-y:auto}.template-example-dialog .template-example{border:0;box-shadow:none;padding:0}.template-preview-note{color:var(--muted);font-size:13px;margin:0 0 12px}.template-preview-paper{max-width:740px;min-height:380px;margin:auto;padding:48px 56px;border:1px solid #dad6ce;background:#fffdf9;box-shadow:0 8px 24px rgba(35,40,50,.08);font-family:'Noto Serif SC','SimSun',serif;line-height:1.8;white-space:pre-wrap}.template-preview-paper p{margin:0 0 12px}.template-preview-paper table{width:100%;border-collapse:collapse;margin:16px 0}.template-preview-paper td{border:1px solid #aaa;padding:7px}
.example-paper{max-width:780px;margin:20px auto 14px;padding:42px 55px 32px;background:#fffdf9;border:1px solid #d9d4cb;box-shadow:0 8px 24px rgba(35,40,50,.08);font-family:'Noto Serif SC','SimSun',serif;color:#222;line-height:1.85}.example-sender{text-align:center;color:#c4322d;font-size:26px;font-weight:700;letter-spacing:5px}.example-doc-number{margin:22px 0 28px;padding-bottom:8px;border-bottom:2px solid #c4322d;text-align:center;font-size:14px}.example-doc-number span{margin-left:34px}.example-paper h4{margin:0 0 22px;text-align:center;font-size:22px;line-height:1.55}.example-recipient{margin:0 0 10px}.example-body{margin:0 0 12px;text-indent:2em}.example-attachment{margin:20px 0 28px}.example-date{text-align:right;margin:0 28px 30px 0}.example-imprint{border-top:1px solid #222;border-bottom:1px solid #222;font-size:13px}.example-imprint p{margin:0;padding:4px 0}.example-imprint p+p{border-top:1px solid #888;display:flex;justify-content:space-between}.template-example-note{max-width:900px;margin:auto}.template-example-note code{padding:1px 4px;background:#f5eee9;color:var(--red)}.template-field-hint{font-size:12px;line-height:1.7;color:var(--muted)}
@media(max-width:1000px){.template-guide{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:700px){.template-guide{grid-template-columns:1fr}.template-example{padding:18px}.example-paper{padding:28px 20px}.example-sender{font-size:20px}.example-paper h4{font-size:18px}.example-doc-number span{margin-left:12px}}
</style>
