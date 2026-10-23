import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RichParagraphEditor from '@/components/RichParagraphEditor.vue'
import ParagraphComments from '@/components/ParagraphComments.vue'
import type { DocumentComment } from '@/api/writing'

// jsdom 未实现布局 API，ProseMirror 聚焦后滚动选区时会调用
Element.prototype.getClientRects ??= (() => []) as never
Range.prototype.getClientRects ??= (() => []) as never
Range.prototype.getBoundingClientRect ??= (() => new DOMRect()) as never

const doc = {
  type: 'doc',
  content: [
    { type: 'paragraph', content: [{ type: 'text', text: '第一段' }, { type: 'hardBreak' }, { type: 'text', text: '申请专项' }, { type: 'text', text: '建设资金', marks: [{ type: 'bold' }] }] },
  ],
}

describe('RichParagraphEditor comment helpers', () => {
  it('quotes the selection and finds quoted text again across formatting and line breaks', async () => {
    const wrapper = mount(RichParagraphEditor, { props: { content: doc, editable: false, label: '段落' }, attachTo: document.body })
    await flushPromises()
    const editor = wrapper.vm.editor!
    expect(wrapper.vm.selectedText()).toBe('')

    expect(wrapper.vm.revealText('专项建设')).toBe(true)
    expect(wrapper.vm.selectedText()).toBe('专项建设')
    const { from, to } = editor.state.selection
    expect(editor.state.doc.textBetween(from, to)).toBe('专项建设')

    expect(wrapper.vm.revealText('已被删除的文字')).toBe(false)
    wrapper.unmount()
  })
})

describe('ParagraphComments', () => {
  const comment = (patch: Partial<DocumentComment>): DocumentComment => ({
    id: 'c1', documentId: 'd1', commenterId: 'reviewer', commentType: '意见', commentText: '请补充测算依据',
    resolved: false, createdAt: '2026-10-23T03:00:00Z', ...patch,
  })

  it('emits reveal for quotes and resolve for open comments only', async () => {
    const wrapper = mount(ParagraphComments, {
      props: { comments: [comment({ quotedText: '建设资金' }), comment({ id: 'c2', resolved: true, resolvedBy: 'alice' })] },
    })
    await wrapper.get('.comment-quote').trigger('click')
    expect(wrapper.emitted('reveal')![0]).toEqual(['建设资金'])
    const buttons = wrapper.findAll('header button')
    expect(buttons).toHaveLength(1)
    await buttons[0].trigger('click')
    expect((wrapper.emitted('resolve')![0][0] as DocumentComment).id).toBe('c1')
    expect(wrapper.text()).toContain('已解决 · alice')
  })
})
