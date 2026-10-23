<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SelectionCandidate, SelectionOperation } from '@/api/writing'
import { diffText } from '@/editor/textDiff'

/** A4 多候选对比：原文与各候选并排展示，逐字高亮增删；采用后由父组件替换编辑器中的选区。 */
const props = defineProps<{
  modelValue: boolean
  operation: SelectionOperation
  selectedText: string
  candidates: SelectionCandidate[]
  loading: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  regenerate: [operation: SelectionOperation, instruction: string]
  adopt: [candidate: SelectionCandidate]
}>()

const OPERATIONS: SelectionOperation[] = ['改写', '润色', '扩写', '缩写']
const operation = ref<SelectionOperation>(props.operation)
const instruction = ref('')
watch(() => [props.modelValue, props.operation] as const, ([open, value]) => { if (open) operation.value = value })

const length = (text: string) => Array.from(text).length
const originalLength = computed(() => length(props.selectedText))
const compared = computed(() => props.candidates.map((candidate) => {
  const delta = length(candidate.text) - originalLength.value
  return { candidate, parts: diffText(props.selectedText, candidate.text), delta: delta > 0 ? `+${delta}` : String(delta) }
}))
function regenerate() { emit('regenerate', operation.value, instruction.value.trim()) }
</script>

<template>
  <el-dialog :model-value="modelValue" title="划词改写 · 候选对比" width="min(1080px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <section class="rewrite-original">
      <header><b>原文</b><small>{{ originalLength }} 字</small></header>
      <p>{{ selectedText }}</p>
    </section>
    <form class="rewrite-request" @submit.prevent="regenerate">
      <div class="rewrite-operations" role="radiogroup" aria-label="改写方式">
        <button v-for="item in OPERATIONS" :key="item" type="button" role="radio" :aria-checked="operation === item" :class="{ active: operation === item }" @click="operation = item">{{ item }}</button>
      </div>
      <input v-model="instruction" maxlength="500" aria-label="补充要求" placeholder="补充要求（可选），如：语气更正式、突出资金保障">
      <button type="submit" class="primary-button" :disabled="loading">{{ loading ? '生成中…' : '重新生成' }}</button>
    </form>
    <p v-if="loading" class="rewrite-status">正在生成候选…</p>
    <p v-else-if="!candidates.length" class="rewrite-status">没有得到与原文不同的候选，可换一种方式或补充要求后重新生成。</p>
    <div v-else class="rewrite-candidates">
      <article v-for="item in compared" :key="item.candidate.generationId">
        <header><b>{{ item.candidate.style }}</b><small>{{ length(item.candidate.text) }} 字（{{ item.delta }}）</small></header>
        <p class="rewrite-diff" aria-label="与原文的差异">
          <template v-for="(part, index) in item.parts" :key="index">
            <del v-if="part.kind === 'removed'">{{ part.text }}</del>
            <ins v-else-if="part.kind === 'added'">{{ part.text }}</ins>
            <span v-else>{{ part.text }}</span>
          </template>
        </p>
        <footer>
          <small>{{ item.candidate.modelVersion ? '模型 ' + item.candidate.modelVersion : '' }}</small>
          <button type="button" class="primary-button" @click="emit('adopt', item.candidate)">采用此候选</button>
        </footer>
      </article>
    </div>
    <template #footer>
      <p class="rewrite-note">采用后替换选中文字，保存新版本时该段落将标记为“AI改写”，请人工复核。</p>
    </template>
  </el-dialog>
</template>

<style scoped>
.rewrite-original{padding:12px 16px;background:#f6f4ef;border-left:3px solid #8c877e}
.rewrite-original header,.rewrite-candidates header{display:flex;justify-content:space-between;align-items:baseline;gap:8px}
.rewrite-original small,.rewrite-candidates small{color:#5f5b54;font-size:12px}
.rewrite-original p{margin:6px 0 0;font-size:15px;line-height:1.9;white-space:pre-wrap}
.rewrite-request{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:14px 0}
.rewrite-operations{display:flex;border:1px solid #c9c3b7}
.rewrite-operations button{border:0;background:#fff;padding:7px 14px;cursor:pointer;color:#2f3430}
.rewrite-operations button+button{border-left:1px solid #c9c3b7}
.rewrite-operations button.active{background:#17231e;color:#fff}
.rewrite-request input{flex:1;min-width:220px;border:1px solid #c9c3b7;padding:8px 10px;font:inherit}
.rewrite-status{color:#5f5b54;padding:28px 0;text-align:center}
.rewrite-candidates{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}
.rewrite-candidates article{display:flex;flex-direction:column;border:1px solid #ddd7cc;padding:12px 14px;background:#fff}
.rewrite-diff{flex:1;margin:8px 0 12px;font-size:15px;line-height:1.9;white-space:pre-wrap}
.rewrite-diff del{color:#a33c32;background:#fdf0ef;text-decoration:line-through}
.rewrite-diff ins{color:#18794e;background:#e8f5ee;text-decoration:none;border-bottom:1px solid #18794e}
.rewrite-candidates footer{display:flex;justify-content:space-between;align-items:center;gap:8px}
.rewrite-note{margin:0;color:#5f5b54;font-size:13px;text-align:left}
</style>
