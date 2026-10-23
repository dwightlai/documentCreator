<script setup lang="ts">
import { ref, watch } from 'vue'
import { EditorContent, useEditor, type JSONContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { ListItem } from '@tiptap/extension-list'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import { plainTextOf } from '@/editor/richText'

/**
 * A7 段落富文本编辑器。只启用公文正文需要的格式（加粗、倾斜、下划线、列表、表格），与服务端 RichContent 白名单一致；
 * 粘贴进来的标题、图片、链接等会被编辑器规范为普通段落或丢弃。内容只在挂载时读取：保存后段落形成新版本、ID 变化，组件随之重建。
 */
const props = defineProps<{ content: JSONContent; editable: boolean; label: string }>()
const emit = defineEmits<{ update: [doc: JSONContent, plainText: string]; focus: [] }>()

// 表格只能出现在正文顶层：列表项和单元格内都不允许嵌套表格（服务端白名单同样拒绝）
const LIST_BLOCKS = '(paragraph | bulletList | orderedList)'
const focused = ref(false)

const editor = useEditor({
  content: props.content,
  editable: props.editable,
  extensions: [
    StarterKit.configure({
      heading: false, blockquote: false, code: false, codeBlock: false, horizontalRule: false,
      strike: false, link: false, trailingNode: false, listItem: false,
    }),
    ListItem.extend({ content: `paragraph ${LIST_BLOCKS}*` }),
    Table.configure({ resizable: false }),
    TableRow,
    TableHeader.extend({ content: `${LIST_BLOCKS}+` }),
    TableCell.extend({ content: `${LIST_BLOCKS}+` }),
  ],
  editorProps: { attributes: { role: 'textbox', 'aria-multiline': 'true', 'aria-label': props.label, class: 'rich-paragraph-body' } },
  onUpdate: ({ editor }) => { const doc = editor.getJSON(); emit('update', doc, plainTextOf(doc)) },
  onFocus: () => { focused.value = true; emit('focus') },
  onBlur: () => { focused.value = false },
})

watch(() => props.editable, (editable) => editor.value?.setEditable(editable))
defineExpose({ editor })

type Action = { label: string; text: string; run: () => void; active?: () => boolean; enabled?: () => boolean }
const chain = () => editor.value!.chain().focus()
const can = () => editor.value!.can()
const formatActions: Action[] = [
  { label: '加粗', text: 'B', run: () => chain().toggleBold().run(), active: () => !!editor.value?.isActive('bold') },
  { label: '倾斜', text: 'I', run: () => chain().toggleItalic().run(), active: () => !!editor.value?.isActive('italic') },
  { label: '下划线', text: 'U', run: () => chain().toggleUnderline().run(), active: () => !!editor.value?.isActive('underline') },
  { label: '编号列表', text: '1.', run: () => chain().toggleOrderedList().run(), active: () => !!editor.value?.isActive('orderedList') },
  { label: '项目符号列表', text: '•', run: () => chain().toggleBulletList().run(), active: () => !!editor.value?.isActive('bulletList') },
]
const tableActions: Action[] = [
  { label: '插入表格', text: '插入表格', run: () => chain().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(), enabled: () => !!editor.value && can().insertTable({ rows: 3, cols: 3, withHeaderRow: true }) },
  { label: '在下方插入行', text: '+行', run: () => chain().addRowAfter().run(), enabled: () => !!editor.value && can().addRowAfter() },
  { label: '在右侧插入列', text: '+列', run: () => chain().addColumnAfter().run(), enabled: () => !!editor.value && can().addColumnAfter() },
  { label: '删除当前行', text: '−行', run: () => chain().deleteRow().run(), enabled: () => !!editor.value && can().deleteRow() },
  { label: '删除当前列', text: '−列', run: () => chain().deleteColumn().run(), enabled: () => !!editor.value && can().deleteColumn() },
  { label: '删除表格', text: '删表格', run: () => chain().deleteTable().run(), enabled: () => !!editor.value && can().deleteTable() },
]
const historyActions: Action[] = [
  { label: '撤销', text: '↶', run: () => chain().undo().run(), enabled: () => !!editor.value && can().undo() },
  { label: '重做', text: '↷', run: () => chain().redo().run(), enabled: () => !!editor.value && can().redo() },
]
const groups = [formatActions, tableActions, historyActions]
</script>

<template>
  <div class="rich-paragraph" :class="{ focused, readonly: !editable }" @click.stop>
    <!-- mousedown.prevent：点击工具栏不让编辑器失焦，选区保持不变 -->
    <div v-if="editor && editable && focused" class="rich-toolbar" role="toolbar" :aria-label="label + '格式工具栏'" @mousedown.prevent>
      <span v-for="(group, index) in groups" :key="index" class="rich-toolbar-group">
        <button
          v-for="action in group"
          :key="action.label"
          type="button"
          :title="action.label"
          :aria-label="action.label"
          :aria-pressed="action.active ? action.active() : undefined"
          :class="{ active: action.active?.() }"
          :disabled="action.enabled ? !action.enabled() : false"
          @click="action.run"
        >{{ action.text }}</button>
      </span>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>

<style scoped>
.rich-paragraph{position:relative;margin-top:8px}
.rich-toolbar{position:sticky;top:0;z-index:2;display:flex;flex-wrap:wrap;gap:6px;padding:6px 8px;margin-bottom:6px;background:#f6f4ef;border:1px solid #ddd7cc}
.rich-toolbar-group{display:flex;gap:2px;padding-right:6px;border-right:1px solid #ddd7cc}
.rich-toolbar-group:last-child{border-right:0}
.rich-toolbar button{min-width:30px;height:28px;padding:0 7px;border:1px solid transparent;background:transparent;color:#2f3430;font-size:13px;cursor:pointer}
.rich-toolbar button:hover:not(:disabled){border-color:#aaa397;background:#fff}
.rich-toolbar button.active{background:#17231e;color:#fff}
.rich-toolbar button:disabled{color:#8c877e;cursor:not-allowed}
.rich-toolbar button[aria-label='加粗']{font-weight:700}
.rich-toolbar button[aria-label='倾斜']{font-style:italic}
.rich-toolbar button[aria-label='下划线']{text-decoration:underline}
:deep(.rich-paragraph-body){min-height:64px;outline:0;font:16px/2 "Noto Sans SC",sans-serif;color:#292d2a;white-space:pre-wrap;overflow-wrap:anywhere}
.rich-paragraph.focused :deep(.rich-paragraph-body){box-shadow:inset 0 0 0 1px #d8d3c8}
.rich-paragraph.readonly :deep(.rich-paragraph-body){color:#4b4f4c}
:deep(.rich-paragraph-body p){margin:0}
:deep(.rich-paragraph-body ul),:deep(.rich-paragraph-body ol){margin:0;padding-left:2em}
:deep(.rich-paragraph-body table){width:100%;border-collapse:collapse;table-layout:fixed;margin:8px 0}
:deep(.rich-paragraph-body td),:deep(.rich-paragraph-body th){border:1px solid #8c877e;padding:4px 8px;vertical-align:top;font-size:15px;line-height:1.7}
:deep(.rich-paragraph-body th){background:#f1eee7;font-weight:600;text-align:left}
:deep(.rich-paragraph-body .selectedCell){background:#e8f0fe}
</style>
