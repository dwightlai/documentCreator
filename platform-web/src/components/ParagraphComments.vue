<script setup lang="ts">
import type { DocumentComment } from '@/api/writing'

/** 段落下方的批注列表：引用文字可点击定位到正文，未解决的批注可标记为已解决。 */
defineProps<{ comments: DocumentComment[] }>()
const emit = defineEmits<{ resolve: [comment: DocumentComment]; reveal: [text: string] }>()
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <ul v-if="comments.length" class="paragraph-comments" aria-label="段落批注">
    <li v-for="comment in comments" :key="comment.id" :class="{ resolved: comment.resolved }">
      <header>
        <span class="comment-type">{{ comment.commentType }}</span>
        <b>{{ comment.commenterId }}</b>
        <small>{{ time(comment.createdAt) }}</small>
        <small v-if="comment.resolved" class="comment-state">已解决{{ comment.resolvedBy ? ' · ' + comment.resolvedBy : '' }}</small>
        <button v-else type="button" @click.stop="emit('resolve', comment)">解决</button>
      </header>
      <button v-if="comment.quotedText" type="button" class="comment-quote" title="在正文中定位" @click.stop="emit('reveal', comment.quotedText)">“{{ comment.quotedText }}”</button>
      <p>{{ comment.commentText }}</p>
    </li>
  </ul>
</template>

<style scoped>
.paragraph-comments{list-style:none;margin:10px 0 0;padding:0;display:grid;gap:8px}
.paragraph-comments li{padding:8px 12px;border-left:3px solid #d8a43a;background:#fdf8ec}
.paragraph-comments li.resolved{border-left-color:#aaa397;background:#f6f4ef}
.paragraph-comments header{display:flex;align-items:center;gap:8px;font-size:12px;color:#5f5b54}
.paragraph-comments header b{color:#2f3430;font-weight:600}
.comment-type{padding:0 6px;border:1px solid #d8a43a;color:#7a5612;border-radius:999px}
.paragraph-comments header button{margin-left:auto;border:1px solid #aaa397;background:#fff;color:#2f3430;padding:2px 10px;cursor:pointer;font-size:12px}
.comment-state{margin-left:auto}
.comment-quote{display:block;margin:6px 0 0;padding:0;border:0;background:none;color:#7a5612;font-size:13px;text-align:left;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%}
.comment-quote:hover{text-decoration:underline}
.paragraph-comments p{margin:4px 0 0;font-size:14px;line-height:1.7;color:#2f3430;white-space:pre-wrap}
</style>
