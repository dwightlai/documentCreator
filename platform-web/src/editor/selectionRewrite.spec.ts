import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RichParagraphEditor from '@/components/RichParagraphEditor.vue'
import SelectionRewriteDialog from '@/components/SelectionRewriteDialog.vue'
import { diffText } from './textDiff'

// jsdom 未实现布局 API，ProseMirror 聚焦后滚动选区时会调用
const emptyRects = () => ({ length: 0, item: () => null, [Symbol.iterator]: [][Symbol.iterator] }) as unknown as DOMRectList
const zeroRect = () => new DOMRect(0, 0, 0, 0)
Element.prototype.getClientRects ??= emptyRects
Range.prototype.getClientRects ??= emptyRects
Range.prototype.getBoundingClientRect ??= zeroRect
// TipTap 的 Vue 编辑器状态在两帧之后才触发重新渲染（工具栏按钮的可用状态依赖它）
const rendered = async () => { await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))); await flushPromises() }

const doc = {
  type: 'doc',
  content: [
    { type: 'paragraph', content: [{ type: 'text', text: '要' }, { type: 'text', text: '马上搞好', marks: [{ type: 'bold' }] }, { type: 'text', text: '资金保障。' }] },
    { type: 'paragraph', content: [{ type: 'text', text: '第二段' }] },
  ],
}

describe('diffText', () => {
  it('marks removed and added characters against the original', () => {
    expect(diffText('马上搞好工作', '及时做好工作')).toEqual([
      { kind: 'removed', text: '马上搞' },
      { kind: 'added', text: '及时做' },
      { kind: 'same', text: '好工作' },
    ])
    expect(diffText('', '新增')).toEqual([{ kind: 'added', text: '新增' }])
    expect(diffText('相同', '相同')).toEqual([{ kind: 'same', text: '相同' }])
  })
})

describe('RichParagraphEditor selection rewrite', () => {
  async function mounted() {
    const wrapper = mount(RichParagraphEditor, { props: { content: doc, editable: true, label: '段落' }, attachTo: document.body })
    await flushPromises()
    return { wrapper, editor: wrapper.vm.editor! }
  }

  it('emits a rewrite request for a selection inside one paragraph only', async () => {
    const { wrapper, editor } = await mounted()
    await wrapper.get('[role="textbox"]').trigger('focus')
    editor.commands.setTextSelection({ from: 2, to: 6 })
    await rendered()
    await wrapper.get('button[aria-label="AI润色选中文字"]').trigger('click')
    expect(wrapper.emitted('rewrite-selection')!.at(-1)).toEqual([{ operation: '润色', from: 2, to: 6, text: '马上搞好' }])

    // 跨段落的选区不能划词改写
    editor.commands.setTextSelection({ from: 2, to: 16 })
    await rendered()
    expect(wrapper.get('button[aria-label="AI改写选中文字"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('replaces the original selection keeping its formatting, and refuses when the text changed', async () => {
    const { wrapper, editor } = await mounted()
    expect(wrapper.vm.replaceRange(2, 6, '马上搞好', '及时做好')).toBe(true)
    const first = editor.getJSON().content![0].content!
    expect(first[1]).toEqual({ type: 'text', text: '及时做好', marks: [{ type: 'bold' }] })
    expect(wrapper.vm.replaceRange(2, 6, '马上搞好', '再次替换')).toBe(false)
    wrapper.unmount()
  })
})

describe('SelectionRewriteDialog', () => {
  it('shows each candidate diffed against the original and emits the adopted one', async () => {
    const candidates = [
      { generationId: 'g1', style: '规范庄重', text: '及时做好', modelVersion: '1.0' },
      { generationId: 'g2', style: '简洁凝练', text: '做好' },
    ]
    const wrapper = mount(SelectionRewriteDialog, {
      props: { modelValue: true, operation: '改写', selectedText: '马上搞好', candidates, loading: false },
      attachTo: document.body,
    })
    await flushPromises()
    const cards = document.body.querySelectorAll('.rewrite-candidates article')
    expect(cards).toHaveLength(2)
    expect(cards[0].querySelector('del')!.textContent).toBe('马上搞')
    expect(cards[0].querySelector('ins')!.textContent).toBe('及时做')
    expect(cards[1].textContent).toContain('2 字（-2）')
    ;(cards[1].querySelector('footer button') as HTMLButtonElement).click()
    expect(wrapper.emitted('adopt')![0]).toEqual([candidates[1]])

    ;(document.body.querySelector('[role="radio"]:nth-child(4)') as HTMLButtonElement).click()
    ;(document.body.querySelector('.rewrite-request') as HTMLFormElement).dispatchEvent(new Event('submit'))
    expect(wrapper.emitted('regenerate')![0]).toEqual(['缩写', ''])
    wrapper.unmount()
  })
})
