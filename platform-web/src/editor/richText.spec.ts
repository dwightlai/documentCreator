import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RichParagraphEditor from '@/components/RichParagraphEditor.vue'
import { plainTextOf, toEditorDoc } from './richText'

const rich = {
  type: 'doc',
  content: [
    { type: 'paragraph', content: [{ type: 'text', text: '一、' }, { type: 'text', text: '总体要求', marks: [{ type: 'bold' }] }] },
    { type: 'orderedList', content: [
      { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: '加强组织领导' }] }] },
      { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: '强化资金保障' }] }] },
    ] },
    { type: 'table', content: [
      { type: 'tableRow', content: [
        { type: 'tableHeader', content: [{ type: 'paragraph', content: [{ type: 'text', text: '项目' }] }] },
        { type: 'tableHeader', content: [{ type: 'paragraph', content: [{ type: 'text', text: '金额' }] }] },
      ] },
    ] },
  ],
}

describe('richText', () => {
  it('derives the same plain text as the server', () => {
    expect(plainTextOf(rich)).toBe('一、总体要求\n1. 加强组织领导\n2. 强化资金保障\n项目\t金额')
  })

  it('falls back to plain text paragraphs when there is no valid rich content', () => {
    const doc = toEditorDoc(undefined, '第一行\n\n第三行')
    expect(doc.content).toHaveLength(3)
    expect(plainTextOf(doc)).toBe('第一行\n\n第三行')
    expect(plainTextOf(toEditorDoc('{broken', '原文'))).toBe('原文')
    expect(toEditorDoc(JSON.stringify(rich), '忽略')).toEqual(rich)
  })
})

describe('RichParagraphEditor', () => {
  it('renders lists and tables and emits rich content with plain text on edit', async () => {
    const wrapper = mount(RichParagraphEditor, { props: { content: rich, editable: true, label: '第 1 段正文' }, attachTo: document.body })
    await flushPromises()
    const body = wrapper.find('[role="textbox"]')
    expect(body.attributes('aria-label')).toBe('第 1 段正文')
    expect(body.find('strong').text()).toBe('总体要求')
    expect(body.findAll('ol li')).toHaveLength(2)
    expect(body.findAll('th')).toHaveLength(2)

    const editor = wrapper.vm.editor!
    editor.commands.setTextSelection(3)
    editor.commands.insertContent('新增')
    const [doc, text] = wrapper.emitted('update')!.at(-1) as [unknown, string]
    expect(text.startsWith('一、新增总体要求')).toBe(true)
    expect(JSON.stringify(doc)).toContain('"bold"')
    wrapper.unmount()
  })

  it('only allows tables at the top level, matching the server whitelist', async () => {
    const wrapper = mount(RichParagraphEditor, { props: { content: rich, editable: true, label: '段落' } })
    await flushPromises()
    const editor = wrapper.vm.editor!
    editor.commands.setTextSelection(10) // 第一个编号列表项内
    expect(editor.isActive('orderedList')).toBe(true)
    editor.commands.insertTable({ rows: 2, cols: 2, withHeaderRow: true })
    // 表格不会进入列表项：编辑器拆开列表，把表格放到正文顶层
    const doc = editor.getJSON()
    expect(doc.content!.filter((block) => block.type === 'table')).toHaveLength(2)
    const nested = JSON.stringify(doc.content!.filter((block) => block.type !== 'table'))
    expect(nested).not.toContain('"table"')
    wrapper.unmount()
  })

  it('makes the content read-only when not editable', async () => {
    const wrapper = mount(RichParagraphEditor, { props: { content: toEditorDoc(undefined, '锁定段落'), editable: false, label: '段落' } })
    await flushPromises()
    expect(wrapper.find('[role="textbox"]').attributes('contenteditable')).toBe('false')
    await wrapper.setProps({ editable: true })
    expect(wrapper.find('[role="textbox"]').attributes('contenteditable')).toBe('true')
    wrapper.unmount()
  })
})
