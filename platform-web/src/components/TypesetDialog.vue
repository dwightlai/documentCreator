<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTypeset, downloadTypeset, latestTypeset, onlyOfficeStatus, typesetEditorConfig, type DocumentTypeset, type OnlyOfficeEditorConfig } from '@/api/writing'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import { listOutputTemplates, type OutputTemplate } from '@/api/templates'

/**
 * A2 一键套红：填写版头、版记要素后按输出模板排版生成定稿稿，并展示 L4 格式检查结果。
 * 文种没有已发布模板时服务端使用内置标准版式（GB/T 9704—2012）。
 */
const props = defineProps<{ modelValue: boolean; taskId: string; documentTypeCode: string; currentVersion: number }>()
const emit = defineEmits<{ 'update:modelValue': [open: boolean] }>()

const UPWARD = ['请示', '报告', '议案']
const FIELDS: { key: string; label: string; placeholder: string; wide?: boolean }[] = [
  { key: 'sender', label: '发文机关标志', placeholder: '如：示范市人民政府文件' },
  { key: 'doc_number', label: '发文字号', placeholder: '如：示政〔2026〕18号（六角括号）' },
  { key: 'signer', label: '签发人', placeholder: '上行文必填' },
  { key: 'recipient', label: '主送机关', placeholder: '如：省人民政府：' },
  { key: 'closing', label: '结束语', placeholder: '如：以上请示，妥否，请批示。', wide: true },
  { key: 'attachment_desc', label: '附件说明', placeholder: '如：1. 项目建设方案', wide: true },
  { key: 'date', label: '成文日期', placeholder: '默认当天，如 2026年10月23日' },
  { key: 'copy_to', label: '抄送机关', placeholder: '可不填' },
  { key: 'issue_unit', label: '印发机关', placeholder: '如：示范市人民政府办公室' },
  { key: 'issue_date', label: '印发日期', placeholder: '默认当天' },
]
const values = reactive<Record<string, string>>({})
const templates = ref<OutputTemplate[]>([])
const templateId = ref('')
const latest = ref<DocumentTypeset>()
const loading = ref(false)
const generating = ref(false)
const upward = computed(() => UPWARD.includes(props.documentTypeCode))
const stale = computed(() => !!latest.value && latest.value.documentVersion !== props.currentVersion)
/** 在线编辑（ONLYOFFICE）：服务端启用后才显示；编辑器关闭后文档服务器回调保存，形成新的定稿稿。 */
const onlineEnabled = ref(false)
const editing = ref<OnlyOfficeEditorConfig>()
const awaitingSave = ref(false)

function message(error: unknown) { return error instanceof Error ? error.message : '操作失败，请稍后重试' }

async function open() {
  loading.value = true
  try {
    const [typeset, all, office] = await Promise.all([latestTypeset(props.taskId), listOutputTemplates(), onlyOfficeStatus().catch(() => ({ enabled: false }))])
    onlineEnabled.value = office.enabled
    latest.value = typeset ?? undefined
    templates.value = all.filter((item) => item.documentTypeCode === props.documentTypeCode && item.lifecycleStatus === '生效')
    // 沿用上次填写的要素；日期每次默认取当天，不沿用
    for (const field of FIELDS) values[field.key] = field.key.endsWith('date') ? '' : typeset?.fieldValues[field.key] ?? ''
    templateId.value = typeset?.templateId && templates.value.some((item) => item.id === typeset.templateId) ? typeset.templateId : ''
  } catch (error) { ElMessage.error(message(error)) }
  finally { loading.value = false }
}
watch(() => props.modelValue, (visible) => { if (visible) open() }, { immediate: true })

async function generate() {
  if (upward.value && !values.signer?.trim()) { ElMessage.warning('上行文应填写签发人'); return }
  generating.value = true
  try {
    const filled = Object.fromEntries(Object.entries(values).filter(([, value]) => value.trim()))
    latest.value = await createTypeset(props.taskId, filled, templateId.value || undefined)
    ElMessage.success(latest.value.deviationCount ? `定稿稿已生成，格式检查发现 ${latest.value.deviationCount} 处问题` : '定稿稿已生成，格式检查通过')
  } catch (error) { ElMessage.error(message(error)) }
  finally { generating.value = false }
}

async function download() {
  if (!latest.value) return
  try {
    const blob = await downloadTypeset(props.taskId, latest.value.id)
    const url = URL.createObjectURL(blob)
    const link = window.document.createElement('a')
    link.href = url
    link.download = `定稿稿-V${latest.value.documentVersion}.docx`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) { ElMessage.error(message(error)) }
}
async function editOnline() {
  if (!latest.value) return
  try { editing.value = await typesetEditorConfig(props.taskId, latest.value.id) } catch (error) { ElMessage.error(message(error)) }
}
/** 文档服务器在所有人关闭编辑器后约 10 秒回调保存：轮询最新定稿稿，出现新记录即更新。 */
async function closeEditor() {
  const before = latest.value?.id
  editing.value = undefined
  awaitingSave.value = true
  try {
    for (let attempt = 0; attempt < 10; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 3000))
      const current = await latestTypeset(props.taskId)
      if (current && current.id !== before) {
        latest.value = current
        ElMessage.success('在线编辑已保存为新的定稿稿')
        return
      }
    }
    ElMessage.info('未检测到修改；如有修改，文档服务器保存后重新打开本窗口即可看到')
  } catch (error) { ElMessage.error(message(error)) }
  finally { awaitingSave.value = false }
}
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
</script>

<template>
  <el-dialog :model-value="modelValue" title="套红排版 · 生成定稿稿" width="min(1040px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading" class="typeset-layout">
      <form class="typeset-form" @submit.prevent="generate">
        <label class="typeset-template">输出模板
          <select v-model="templateId">
            <option value="">自动（任务模板 → 文种默认模板 → 内置标准版式）</option>
            <option v-for="item in templates" :key="item.id" :value="item.id">{{ item.templateName }}</option>
          </select>
        </label>
        <div class="typeset-fields">
          <label v-for="field in FIELDS" :key="field.key" :class="{ wide: field.wide }">
            <span>{{ field.label }}<b v-if="field.key === 'signer' && upward">*</b></span>
            <input v-model="values[field.key]" :placeholder="field.placeholder" maxlength="200">
          </label>
        </div>
        <p class="typeset-hint">标题取自文稿，正文按段排入模板正文位置并保留加粗、列表和表格。成文日期请用阿拉伯数字。</p>
        <button type="submit" class="primary-button" :disabled="generating">{{ generating ? '正在排版…' : latest ? '重新套红' : '生成定稿稿' }}</button>
      </form>

      <section class="typeset-result" aria-live="polite">
        <template v-if="latest">
          <header>
            <b>{{ latest.templateName }}</b>
            <small>{{ latest.source }} · 基于正文 V{{ latest.documentVersion }} · {{ time(latest.createdAt) }}</small>
          </header>
          <p v-if="stale" class="typeset-stale">正文已更新到 V{{ currentVersion }}，定稿稿仍基于 V{{ latest.documentVersion }}，请重新套红。</p>
          <p v-if="!latest.deviationCount" class="typeset-pass">格式检查通过：页面、字体字号、行距缩进与公文要素符合模板及 GB/T 9704 要求。</p>
          <ul v-else class="typeset-deviations" aria-label="格式检查问题">
            <li v-for="(item, index) in latest.deviations" :key="index">
              <span class="risk" :data-risk="item.riskLevel">{{ item.riskLevel }}</span>
              <div><strong>{{ item.dimensionName }}</strong><small v-if="item.expectedValue || item.actualValue">应为 {{ item.expectedValue ?? '—' }}，实际 {{ item.actualValue ?? '—' }}</small><small v-if="item.description">{{ item.description }}</small></div>
            </li>
          </ul>
          <footer>
            <button type="button" class="primary-button" @click="download">下载定稿 DOCX</button>
            <button v-if="onlineEnabled" type="button" class="secondary-button" :disabled="stale || awaitingSave" :title="stale ? '正文已更新，请先重新套红' : '在 ONLYOFFICE 中微调版式，关闭后保存为新的定稿稿'" @click="editOnline">{{ awaitingSave ? '等待保存…' : '在线编辑' }}</button>
          </footer>
        </template>
        <p v-else class="typeset-empty">尚未生成定稿稿。填写左侧要素后生成，可下载 DOCX 并查看格式检查结果。</p>
      </section>
    </div>
  </el-dialog>
  <el-dialog :model-value="!!editing" title="在线编辑定稿稿（ONLYOFFICE）" fullscreen destroy-on-close @close="closeEditor">
    <OnlyOfficeEditor v-if="editing" :document-server-url="editing.documentServerUrl" :config="editing.config" @error="(text) => ElMessage.error(text)" />
  </el-dialog>
</template>

<style scoped>
.typeset-layout{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,1fr);gap:20px}
@media(max-width:860px){.typeset-layout{grid-template-columns:1fr}}
.typeset-form{display:grid;gap:12px;align-content:start}
.typeset-form label{display:grid;gap:4px;font-size:13px;color:#4b4f4c}
.typeset-form label b{color:#a33c32;margin-left:2px}
.typeset-form input,.typeset-form select{border:1px solid #c9c3b7;background:#fbfaf7;padding:8px 10px;font:inherit;font-size:14px}
.typeset-fields{display:grid;grid-template-columns:1fr 1fr;gap:10px 12px}
.typeset-fields .wide{grid-column:1/-1}
.typeset-hint{margin:0;color:#5f5b54;font-size:12px;line-height:1.6}
.typeset-form .primary-button{justify-self:start}
.typeset-result{border:1px solid #ddd7cc;background:#fbfaf7;padding:14px 16px;display:grid;gap:10px;align-content:start;min-height:240px}
.typeset-result header{display:grid;gap:2px}
.typeset-result header small{color:#5f5b54;font-size:12px}
.typeset-stale{margin:0;padding:8px 10px;background:#fff4e5;color:#9a5b13;font-size:13px}
.typeset-pass{margin:0;padding:8px 10px;background:#e8f5ee;color:#18794e;font-size:13px}
.typeset-deviations{list-style:none;margin:0;padding:0;display:grid;gap:6px;max-height:300px;overflow:auto}
.typeset-deviations li{display:grid;grid-template-columns:auto 1fr;gap:8px;align-items:start;padding:8px;background:#fff;border:1px solid #eee9df}
.typeset-deviations div{display:grid;gap:2px}
.typeset-deviations strong{font-size:13px}
.typeset-deviations small{color:#5f5b54;font-size:12px}
.risk{padding:0 6px;border-radius:999px;font-size:12px;background:#f0f2f5;color:#4b4f4c}
.risk[data-risk='严重']{background:#fdf0ef;color:#a33c32}
.risk[data-risk='重要']{background:#fff4e5;color:#9a5b13}
.typeset-result footer{display:flex;flex-wrap:wrap;gap:8px}
.typeset-empty{color:#5f5b54;font-size:13px;line-height:1.7;margin:0}
</style>
