<script setup lang="ts">
import { computed } from 'vue'
import type { DocumentSuggestion } from '@/api/writing'
import { diffText } from '@/editor/textDiff'

/** 段落下方的修订列表：以修订痕迹样式（删除划线、新增下划线）展示原文到修订内容的差异，逐条接受或拒绝。 */
const props = defineProps<{ suggestions: DocumentSuggestion[]; busyId?: string }>()
const emit = defineEmits<{ accept: [suggestion: DocumentSuggestion]; reject: [suggestion: DocumentSuggestion] }>()
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
const items = computed(() => props.suggestions.map((suggestion) => ({
  suggestion,
  parts: diffText(suggestion.basePlainText, suggestion.proposedPlainText),
  formatOnly: suggestion.basePlainText === suggestion.proposedPlainText,
})))
</script>

<template>
  <ul v-if="suggestions.length" class="paragraph-suggestions" aria-label="段落修订">
    <li v-for="{ suggestion, parts, formatOnly } in items" :key="suggestion.id" :class="{ stale: suggestion.stale, decided: suggestion.status !== '待处理' }">
      <header>
        <span class="suggestion-tag">修订</span>
        <b>{{ suggestion.proposerId }}</b>
        <small>{{ time(suggestion.createdAt) }} · 基于 V{{ suggestion.baseVersion }}</small>
        <span v-if="suggestion.aiAssisted" class="suggestion-ai" title="修订中采用了 AI 划词改写候选，接受后段落标记为 AI 改写">含 AI 改写</span>
        <small v-if="suggestion.status !== '待处理'" class="suggestion-state">{{ suggestion.status }}{{ suggestion.decidedBy ? ' · ' + suggestion.decidedBy : '' }}{{ suggestion.acceptedVersion ? ' · V' + suggestion.acceptedVersion : '' }}</small>
        <span v-else class="suggestion-actions">
          <button type="button" class="accept" :disabled="suggestion.stale || busyId === suggestion.id" :title="suggestion.stale ? '原文已被修改，不能接受' : '接受后形成新版本'" @click.stop="emit('accept', suggestion)">接受</button>
          <button type="button" :disabled="busyId === suggestion.id" @click.stop="emit('reject', suggestion)">拒绝</button>
        </span>
      </header>
      <p v-if="suggestion.stale" class="suggestion-stale">原文在提出修订后已被修改，该修订已过期，只能拒绝。</p>
      <p v-if="formatOnly" class="suggestion-format">仅调整格式（加粗、列表、表格等），文字未变。</p>
      <p v-else class="suggestion-diff" aria-label="修订痕迹">
        <template v-for="(part, index) in parts" :key="index">
          <del v-if="part.kind === 'removed'">{{ part.text }}</del>
          <ins v-else-if="part.kind === 'added'">{{ part.text }}</ins>
          <span v-else>{{ part.text }}</span>
        </template>
      </p>
    </li>
  </ul>
</template>

<style scoped>
.paragraph-suggestions{list-style:none;margin:10px 0 0;padding:0;display:grid;gap:8px}
.paragraph-suggestions li{padding:8px 12px;border-left:3px solid #1a56db;background:#f3f7ff}
.paragraph-suggestions li.stale{border-left-color:#9a5b13;background:#fff8ee}
.paragraph-suggestions li.decided{border-left-color:#aaa397;background:#f6f4ef}
.paragraph-suggestions header{display:flex;flex-wrap:wrap;align-items:center;gap:8px;font-size:12px;color:#5f5b54}
.paragraph-suggestions header b{color:#2f3430;font-weight:600}
.suggestion-tag{padding:0 6px;border:1px solid #1a56db;color:#1a56db;border-radius:999px}
.suggestion-ai{padding:0 6px;background:#e8f0fe;color:#1a56db;border-radius:999px}
.suggestion-state,.suggestion-actions{margin-left:auto}
.suggestion-actions{display:flex;gap:6px}
.suggestion-actions button{border:1px solid #aaa397;background:#fff;color:#2f3430;padding:2px 10px;cursor:pointer;font-size:12px}
.suggestion-actions button.accept:not(:disabled){border-color:#18794e;color:#18794e}
.suggestion-actions button:disabled{color:#8c877e;cursor:not-allowed}
.suggestion-stale{margin:6px 0 0;color:#9a5b13;font-size:12px}
.suggestion-format{margin:6px 0 0;color:#5f5b54;font-size:13px}
.suggestion-diff{margin:6px 0 0;font-size:14px;line-height:1.8;white-space:pre-wrap;color:#2f3430}
.suggestion-diff del{color:#a33c32;background:#fdf0ef;text-decoration:line-through}
.suggestion-diff ins{color:#18794e;background:#e8f5ee;text-decoration:none;border-bottom:1px solid #18794e}
</style>
