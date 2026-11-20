<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addOutlineNode, checkOutline, confirmOutline, deleteOutlineNode, updateOutlineNode, type OutlineNode, type WritingOutline } from '@/api/writing'

const props = defineProps<{ taskId: string; outline: WritingOutline; focusId?: string }>()
const open = defineModel<boolean>({ required: true })
const emit = defineEmits<{ updated: [outline: WritingOutline]; confirmed: [outline: WritingOutline] }>()

const MAX_LEVEL = 3
const numerals = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
const numeral = (n: number) => n <= 10 ? numerals[n - 1] : n < 20 ? `十${numerals[n - 11]}` : `${numerals[Math.floor(n / 10) - 1]}十${n % 10 ? numerals[n % 10 - 1] : ''}`

interface Row { node: OutlineNode; level: number; label: string; parentId?: string; children: string[]; lastOrder: number; height: number; prev?: OutlineNode; next?: OutlineNode }

const selectedId = ref('')
const busy = ref(false)
const checkResult = ref('')
const form = reactive({ title: '', purpose: '', targetWordCount: 300, required: true })

const rows = computed(() => {
  const nodes = props.outline.nodes
  const byId = new Map(nodes.map(node => [node.id, node]))
  const groups = new Map<string, OutlineNode[]>()
  for (const node of nodes) {
    const key = node.parentNodeId && byId.has(node.parentNodeId) ? node.parentNodeId : ''
    groups.set(key, [...(groups.get(key) ?? []), node])
  }
  groups.forEach(list => list.sort((a, b) => a.order - b.order))
  const result = new Map<string, Row>()
  const ordered: Row[] = []
  const walk = (parentId: string, level: number): { last: number; height: number } => {
    let last = 0
    let height = 0
    const list = groups.get(parentId) ?? []
    list.forEach((node, index) => {
      const row: Row = {
        node, level, parentId: parentId || undefined, children: (groups.get(node.id) ?? []).map(item => item.id),
        label: level === 1 ? `${numeral(index + 1)}、` : level === 2 ? `（${numeral(index + 1)}）` : `${index + 1}.`,
        lastOrder: node.order, height: 1, prev: list[index - 1], next: list[index + 1],
      }
      result.set(node.id, row)
      ordered.push(row)
      const sub = walk(node.id, level + 1)
      row.lastOrder = Math.max(node.order, sub.last)
      row.height = 1 + sub.height
      last = Math.max(last, row.lastOrder)
      height = Math.max(height, row.height)
    })
    return { last, height }
  }
  walk('', 1)
  return { map: result, ordered }
})

const current = computed(() => rows.value.map.get(selectedId.value))
const layered = computed(() => rows.value.ordered.some(row => row.level > 1))
const dirty = computed(() => {
  const node = current.value?.node
  return Boolean(node) && (form.title !== node!.title || form.purpose !== (node!.purpose ?? '') || form.targetWordCount !== (node!.targetWordCount ?? 300) || form.required !== node!.required)
})
const path = computed(() => {
  const parts: string[] = []
  let row = current.value
  while (row) {
    parts.unshift(row.label + row.node.title)
    row = row.parentId ? rows.value.map.get(row.parentId) : undefined
  }
  return parts.join(' / ')
})
const issues = computed(() => {
  const list: { id: string; text: string }[] = []
  for (const row of rows.value.ordered) {
    if (needsRename(row)) list.push({ id: row.node.id, text: `${row.label}${row.node.title}：标题是占位名，请改成具体事项` })
    if (row.children.length === 1) list.push({ id: row.node.id, text: `${row.label}${row.node.title}：只有一个下级，应至少两项或取消分级` })
  }
  if (rows.value.ordered.filter(row => row.level === 1).length < 2) list.unshift({ id: '', text: '一级章节至少需要两个' })
  return list
})

const can = computed(() => {
  const row = current.value
  if (!row || props.outline.confirmed) return { up: false, down: false, indent: false, outdent: false, child: false, sibling: false, remove: false }
  return {
    up: Boolean(row.prev),
    down: Boolean(row.next),
    indent: Boolean(row.prev) && (rows.value.map.get(row.prev!.id)!.level + row.height) <= MAX_LEVEL,
    outdent: row.level > 1,
    child: row.level < MAX_LEVEL,
    sibling: true,
    remove: true,
  }
})

function needsRename(row: Row) {
  const title = row.node.title.trim()
  if (!title || title === '新增章节' || title === '新增下级章节') return true
  const parent = row.parentId ? rows.value.map.get(row.parentId) : undefined
  return Boolean(parent && /^.+[一二三四五六七八九十]$/.test(title) && title.startsWith(parent.node.title))
}

function loadForm() {
  const node = current.value?.node
  form.title = node?.title ?? ''
  form.purpose = node?.purpose ?? ''
  form.targetWordCount = node?.targetWordCount ?? 300
  form.required = node?.required ?? true
}

watch(open, value => {
  if (value) window.addEventListener('keydown', onKey)
  else window.removeEventListener('keydown', onKey)
  if (!value) return
  selectedId.value = props.focusId && rows.value.map.has(props.focusId) ? props.focusId : rows.value.ordered[0]?.node.id ?? ''
  checkResult.value = ''
  loadForm()
})
watch(selectedId, loadForm)

function message(error: unknown) { return error instanceof Error ? error.message : '操作失败，请稍后重试' }

async function run(action: () => Promise<WritingOutline>, focus?: (outline: WritingOutline) => string | undefined) {
  if (busy.value) return
  busy.value = true
  try {
    const next = await action()
    emit('updated', next)
    await nextTick()
    checkResult.value = ''
    const id = focus?.(next)
    if (id) selectedId.value = id
    else if (!next.nodes.some(node => node.id === selectedId.value)) selectedId.value = next.nodes[0]?.id ?? ''
    loadForm()
  } catch (error) { ElMessage.error(message(error)) }
  finally { busy.value = false }
}

function patch(node: OutlineNode, change: Partial<OutlineNode>) {
  return updateOutlineNode(props.taskId, props.outline.version, { ...node, ...change })
}

async function saveForm() {
  const node = current.value?.node
  if (!node || !dirty.value) return true
  if (!form.title.trim()) { ElMessage.warning('章节标题不能为空'); return false }
  await run(() => patch(node, { title: form.title.trim(), purpose: form.purpose, targetWordCount: Math.max(1, Number(form.targetWordCount) || 1), required: form.required }), () => node.id)
  return true
}

async function select(id: string) {
  if (id === selectedId.value) return
  if (dirty.value && !(await saveForm())) return
  selectedId.value = id
}

async function moveUp() { const row = current.value; if (row?.prev) await run(() => patch(row.node, { order: row.prev!.order }), () => row.node.id) }
async function moveDown() { const row = current.value; if (row?.next) await run(() => patch(row.node, { order: row.next!.order }), () => row.node.id) }
async function indent() { const row = current.value; if (row?.prev) await run(() => patch(row.node, { parentNodeId: row.prev!.id }), () => row.node.id) }
async function outdent() {
  const row = current.value
  const parent = row?.parentId ? rows.value.map.get(row.parentId) : undefined
  if (!row || !parent) return
  await run(() => patch(row.node, { parentNodeId: parent.parentId, order: parent.lastOrder }), () => row.node.id)
}
async function addSibling() {
  const row = current.value
  const order = row ? row.lastOrder + 1 : props.outline.nodes.length + 1
  if (dirty.value && !(await saveForm())) return
  await run(() => addOutlineNode(props.taskId, props.outline.version, order, row?.parentId), next => next.nodes.find(node => node.order === order)?.id)
}
async function addChild() {
  const row = current.value
  if (!row) return
  if (dirty.value && !(await saveForm())) return
  const order = row.lastOrder + 1
  await run(() => addOutlineNode(props.taskId, props.outline.version, order, row.node.id), next => next.nodes.find(node => node.order === order)?.id)
}
async function remove() {
  const row = current.value
  if (!row) return
  const count = countDescendants(row)
  try {
    await ElMessageBox.confirm(count ? `将同时删除「${row.node.title}」下的 ${count} 个下级章节。` : `删除「${row.node.title}」？`, '删除章节', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  const fallback = row.prev?.id ?? row.next?.id ?? row.parentId
  await run(() => deleteOutlineNode(props.taskId, props.outline.version, row.node.id), next => next.nodes.some(node => node.id === fallback) ? fallback : undefined)
}
function countDescendants(row: Row): number {
  return row.children.reduce((sum, id) => sum + 1 + countDescendants(rows.value.map.get(id)!), 0)
}

async function check() {
  if (dirty.value && !(await saveForm())) return
  try { const next = await checkOutline(props.taskId, props.outline.version); emit('updated', next); checkResult.value = next.structureCheckResult }
  catch (error) { ElMessage.error(message(error)) }
}
async function confirm() {
  if (dirty.value && !(await saveForm())) return
  if (issues.value.some(item => item.text.includes('占位名'))) {
    try { await ElMessageBox.confirm('还有章节使用占位标题，生成正文时模型无法判断具体事项。仍要确认提纲吗？', '确认提纲', { type: 'warning', confirmButtonText: '仍然确认', cancelButtonText: '返回修改' }) }
    catch { return }
  }
  try { const next = await confirmOutline(props.taskId, props.outline.version); emit('confirmed', next); open.value = false }
  catch (error) { ElMessage.error(message(error)) }
}
async function close() {
  if (dirty.value && !(await saveForm())) return
  open.value = false
}
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
function onKey(event: KeyboardEvent) {
  if (!event.altKey || props.outline.confirmed || busy.value) return
  const actions: Record<string, () => Promise<void>> = { ArrowUp: moveUp, ArrowDown: moveDown, ArrowRight: indent, ArrowLeft: outdent }
  const action = actions[event.key]
  const allowed = { ArrowUp: can.value.up, ArrowDown: can.value.down, ArrowRight: can.value.indent, ArrowLeft: can.value.outdent }[event.key]
  if (action && allowed) { event.preventDefault(); void action() }
}
</script>

<template>
  <el-dialog v-model="open" :title="`编排提纲 · V${outline.version}`" width="1080px" top="5vh" class="outline-editor-dialog" :before-close="() => void close()">
    <div class="oe-body">
      <section class="oe-tree">
        <div class="oe-toolbar" role="toolbar" aria-label="章节操作">
          <div class="oe-group">
            <button type="button" :disabled="!can.up || busy" title="上移（Alt+↑）" @click="moveUp">↑ 上移</button>
            <button type="button" :disabled="!can.down || busy" title="下移（Alt+↓）" @click="moveDown">↓ 下移</button>
          </div>
          <div class="oe-group">
            <button type="button" :disabled="!can.outdent || busy" title="升为上一级（Alt+←）" @click="outdent">← 升级</button>
            <button type="button" :disabled="!can.indent || busy" title="降为上一节的下级（Alt+→）" @click="indent">降级 →</button>
          </div>
          <div class="oe-group">
            <button type="button" :disabled="!can.sibling || busy" @click="addSibling">＋ 同级</button>
            <button type="button" :disabled="!can.child || busy" @click="addChild">＋ 下级</button>
          </div>
          <button type="button" class="danger" :disabled="!can.remove || busy" @click="remove">删除</button>
        </div>
        <ol class="oe-list">
          <li v-for="row in rows.ordered" :key="row.node.id" :class="['oe-row', `level-${row.level}`, { active: row.node.id === selectedId, warn: needsRename(row) }]" :style="{ paddingLeft: `${12 + (row.level - 1) * 22}px` }" @click="select(row.node.id)">
            <span class="oe-label">{{ row.label }}</span>
            <span class="oe-title">{{ row.node.id === selectedId && form.title ? form.title : row.node.title }}</span>
            <span v-if="needsRename(row)" class="oe-tag warn">待改名</span>
            <span v-else-if="row.children.length" class="oe-tag">{{ row.children.length }} 项</span>
            <span v-else-if="row.node.targetWordCount" class="oe-words">{{ row.node.targetWordCount }} 字</span>
          </li>
        </ol>
        <p class="oe-hint">层次序号依次为 一、（一）1.，最多三级；同一层级拆分时至少两项。上级章节只出标题，正文写在最末一级。快捷键：Alt + 方向键。</p>
      </section>

      <section v-if="current" class="oe-detail">
        <p class="oe-path">{{ path }}</p>
        <label>章节标题<input v-model="form.title" :disabled="outline.confirmed" maxlength="500" placeholder="写成具体事项，例如“压减审批时限”"></label>
        <label>写作要点<textarea v-model="form.purpose" :disabled="outline.confirmed" rows="7" placeholder="本节要写清的事实、措施、责任主体和完成标准" /></label>
        <div class="oe-inline">
          <label>目标字数<input v-model.number="form.targetWordCount" :disabled="outline.confirmed" type="number" min="1" step="50"></label>
          <label class="oe-check"><input v-model="form.required" :disabled="outline.confirmed" type="checkbox">必写章节</label>
        </div>
        <p v-if="current.children.length" class="oe-note">本节有 {{ current.children.length }} 个下级章节，正文只输出“{{ current.label }}{{ form.title }}”这一行标题，内容由下级章节生成。</p>
        <p v-else-if="layered" class="oe-note">正文先输出标题“{{ current.label }}{{ form.title }}”，再按写作要点生成本节内容。</p>
        <div v-if="!outline.confirmed" class="oe-detail-actions">
          <button type="button" :disabled="!dirty || busy" @click="loadForm">撤销修改</button>
          <button type="button" class="primary" :disabled="!dirty || busy" @click="saveForm">{{ dirty ? '保存本节' : '已保存' }}</button>
        </div>
      </section>
      <section v-else class="oe-detail oe-empty">
        <p>提纲还没有章节。</p>
        <button type="button" class="primary" @click="addSibling">＋ 添加第一个章节</button>
      </section>
    </div>

    <template #footer>
      <div class="oe-footer">
        <div class="oe-issues">
          <template v-if="checkResult"><b :class="checkResult.startsWith('通过') ? 'ok' : 'bad'">{{ checkResult }}</b></template>
          <template v-else-if="issues.length">
            <span class="bad">待处理 {{ issues.length }} 项：</span>
            <button v-for="item in issues.slice(0, 3)" :key="item.text" type="button" class="oe-issue" @click="item.id && select(item.id)">{{ item.text }}</button>
          </template>
          <span v-else class="ok">结构完整</span>
        </div>
        <div class="oe-footer-actions">
          <button type="button" @click="close">关闭</button>
          <template v-if="!outline.confirmed">
            <button type="button" @click="check">结构检查</button>
            <button type="button" class="primary" @click="confirm">确认提纲</button>
          </template>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.oe-body{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(0,1fr);gap:18px;height:66vh;min-height:420px}
.oe-tree,.oe-detail{display:flex;flex-direction:column;min-height:0;border:1px solid #e5e7eb;border-radius:8px;background:#f8f9fb}
.oe-toolbar{display:flex;flex-wrap:wrap;gap:8px;padding:10px;border-bottom:1px solid #e5e7eb;background:#fff;border-radius:8px 8px 0 0}
.oe-group{display:flex;border:1px solid #d0d5dd;border-radius:6px;overflow:hidden}
.oe-group button{border:0;border-right:1px solid #e5e7eb;border-radius:0}
.oe-group button:last-child{border-right:0}
.oe-toolbar button,.oe-detail-actions button,.oe-footer-actions button,.oe-empty button{border:1px solid #d0d5dd;border-radius:6px;background:#fff;color:#1f2329;padding:6px 12px;font-size:13px;cursor:pointer;white-space:nowrap}
.oe-toolbar button:hover:not(:disabled),.oe-footer-actions button:hover,.oe-detail-actions button:hover:not(:disabled){color:#c4302b;border-color:#e8aaa6}
.oe-toolbar button:disabled,.oe-detail-actions button:disabled{color:#b0b6bf;cursor:not-allowed;background:#f4f6f9}
.oe-toolbar .danger{margin-left:auto;color:#c4302b}
button.primary{background:#c4302b!important;border-color:#c4302b!important;color:#fff!important}
button.primary:disabled{opacity:.45}
.oe-list{list-style:none;margin:0;padding:6px 0;overflow:auto;flex:1}
.oe-row{display:flex;align-items:center;gap:8px;padding:9px 12px;cursor:pointer;border-left:3px solid transparent;position:relative}
.oe-row:hover{background:#f4f6f9}
.oe-row.active{background:#fff;border-left-color:#c4302b;box-shadow:inset 0 0 0 1px #f6cfcb}
.oe-row.level-1{font-weight:600}
.oe-row.level-1 + .oe-row.level-1{border-top:1px solid #eef0f3}
.oe-row.level-2 .oe-title,.oe-row.level-3 .oe-title{color:#374151}
.oe-row.level-3{font-size:13px}
.oe-label{flex-shrink:0;color:#c4302b;font-variant-numeric:tabular-nums;min-width:34px}
.oe-title{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.oe-tag{flex-shrink:0;font-size:11px;font-weight:400;color:#646a73;background:#eef0f3;border-radius:10px;padding:1px 8px}
.oe-tag.warn{color:#9a5b00;background:#fff1d6}
.oe-words{flex-shrink:0;font-size:11px;font-weight:400;color:#9aa1ab}
.oe-hint{margin:0;padding:10px 12px;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px;line-height:1.6}
.oe-detail{padding:16px;gap:14px;overflow:auto;background:#fff}
.oe-path{margin:0;color:#6b7280;font-size:12px;line-height:1.5}
.oe-detail label{display:flex;flex-direction:column;gap:6px;font-size:13px;color:#4e5561}
.oe-detail input:not([type=checkbox]),.oe-detail textarea{border:1px solid #d0d5dd;border-radius:6px;padding:8px 10px;font:inherit;font-size:14px;color:#222;background:#fff;resize:vertical}
.oe-detail input:focus,.oe-detail textarea:focus{outline:none;border-color:#c4302b;box-shadow:0 0 0 2px rgba(196,48,43,.12)}
.oe-inline{display:flex;gap:16px;align-items:flex-end}
.oe-inline label:first-child{width:140px}
.oe-detail .oe-check{flex-direction:row;align-items:center;gap:6px;padding-bottom:9px}
.oe-note{margin:0;padding:10px 12px;background:#f4f6f9;border-radius:6px;color:#4e5561;font-size:12px;line-height:1.6}
.oe-detail-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:auto}
.oe-empty{align-items:center;justify-content:center;color:#6b7280}
.oe-footer{display:flex;justify-content:space-between;align-items:center;gap:16px}
.oe-issues{display:flex;flex-wrap:wrap;align-items:center;gap:6px;font-size:12px;text-align:left;min-width:0}
.oe-issue{border:0;background:#fff7e6;color:#8a5a00;border-radius:4px;padding:3px 8px;cursor:pointer;font-size:12px;max-width:320px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ok{color:#2f7a4b}
.bad{color:#c4302b}
.oe-footer-actions{display:flex;gap:8px;flex-shrink:0}
</style>
