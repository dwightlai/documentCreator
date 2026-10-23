import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TypesetDialog from '@/components/TypesetDialog.vue'
import type { DocumentTypeset } from '@/api/writing'

const api = vi.hoisted(() => ({ latestTypeset: vi.fn(), createTypeset: vi.fn(), downloadTypeset: vi.fn(), listOutputTemplates: vi.fn(), onlyOfficeStatus: vi.fn(), typesetEditorConfig: vi.fn() }))
vi.mock('@/api/writing', () => ({ latestTypeset: api.latestTypeset, createTypeset: api.createTypeset, downloadTypeset: api.downloadTypeset, onlyOfficeStatus: api.onlyOfficeStatus, typesetEditorConfig: api.typesetEditorConfig }))
vi.mock('@/api/templates', () => ({ listOutputTemplates: api.listOutputTemplates }))

const previous: DocumentTypeset = {
  id: 't1', documentVersion: 2, stale: false, templateName: '内置标准版式（GB/T 9704—2012）',
  fieldValues: { sender: '示范市人民政府文件', doc_number: '示政〔2026〕18号', signer: '张某', date: '2026年10月1日' },
  deviationCount: 1, source: '套红生成', fileSize: 1000, createdAt: '2026-10-23T03:00:00Z',
  deviations: [{ dimensionCode: 'DOC_NUMBER', riskLevel: '重要', dimensionName: '发文字号', expectedValue: '六角括号', actualValue: '方括号', description: '应使用六角括号' }],
}

async function mountDialog(currentVersion = 3) {
  const wrapper = mount(TypesetDialog, {
    props: { modelValue: true, taskId: 'task', documentTypeCode: '请示', currentVersion },
    attachTo: document.body,
    global: { stubs: { OnlyOfficeEditor: { props: ['documentServerUrl', 'config'], template: '<div class="onlyoffice-stub">{{ documentServerUrl }}</div>' } } },
  })
  await flushPromises()
  return wrapper
}
const input = (label: string) => [...document.body.querySelectorAll('.typeset-fields label')]
  .find((item) => item.textContent?.startsWith(label))!.querySelector('input') as HTMLInputElement

describe('TypesetDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    Object.values(api).forEach((mock) => mock.mockReset())
    api.listOutputTemplates.mockResolvedValue([
      { id: 'tpl', templateName: '本单位请示模板', documentTypeCode: '请示', lifecycleStatus: '生效' },
      { id: 'draft', templateName: '草稿模板', documentTypeCode: '请示', lifecycleStatus: '草稿' },
    ])
    api.onlyOfficeStatus.mockResolvedValue({ enabled: false })
  })

  it('prefills previous fields except dates, flags stale drafts and lists deviations', async () => {
    api.latestTypeset.mockResolvedValue(previous)
    const wrapper = await mountDialog()
    expect(input('发文字号').value).toBe('示政〔2026〕18号')
    expect(input('成文日期').value).toBe('')
    expect(document.body.querySelectorAll('.typeset-template option')).toHaveLength(2)
    expect(document.body.querySelector('.typeset-stale')!.textContent).toContain('V3')
    expect(document.body.querySelector('.typeset-deviations')!.textContent).toContain('发文字号')
    expect([...document.body.querySelectorAll('.typeset-result footer button')].map((b) => b.textContent)).toEqual(['下载定稿 DOCX'])
    wrapper.unmount()
  })

  it('requires a signer for upward documents and submits only filled fields', async () => {
    api.latestTypeset.mockResolvedValue(undefined)
    api.createTypeset.mockResolvedValue({ ...previous, deviationCount: 0, deviations: [], documentVersion: 3 })
    const wrapper = await mountDialog()
    const form = document.body.querySelector('.typeset-form') as HTMLFormElement
    form.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(api.createTypeset).not.toHaveBeenCalled()

    input('签发人').value = '张某'
    input('签发人').dispatchEvent(new Event('input'))
    input('发文字号').value = '示政〔2026〕19号'
    input('发文字号').dispatchEvent(new Event('input'))
    form.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(api.createTypeset).toHaveBeenCalledWith('task', { signer: '张某', doc_number: '示政〔2026〕19号' }, undefined)
    expect(document.body.querySelector('.typeset-pass')).not.toBeNull()
    expect(document.body.querySelector('.typeset-stale')).toBeNull()
    wrapper.unmount()
  })

  it('opens the final draft in ONLYOFFICE when enabled and the draft is current', async () => {
    api.latestTypeset.mockResolvedValue({ ...previous, documentVersion: 3 })
    api.onlyOfficeStatus.mockResolvedValue({ enabled: true, documentServerUrl: 'https://platform.example/office' })
    api.typesetEditorConfig.mockResolvedValue({ documentServerUrl: 'https://platform.example/office', config: { document: { key: 't1' } } })
    const wrapper = await mountDialog(3)
    const button = [...document.body.querySelectorAll('.typeset-result footer button')].find((b) => b.textContent === '在线编辑') as HTMLButtonElement
    expect(button.disabled).toBe(false)
    button.click()
    await flushPromises()
    expect(api.typesetEditorConfig).toHaveBeenCalledWith('task', 't1')
    expect(document.body.querySelector('.onlyoffice-stub')!.textContent).toBe('https://platform.example/office')
    wrapper.unmount()
  })

  it('does not allow editing a stale draft online', async () => {
    api.latestTypeset.mockResolvedValue(previous)
    api.onlyOfficeStatus.mockResolvedValue({ enabled: true, documentServerUrl: 'https://platform.example/office' })
    const wrapper = await mountDialog(3)
    const button = [...document.body.querySelectorAll('.typeset-result footer button')].find((b) => b.textContent === '在线编辑') as HTMLButtonElement
    expect(button.disabled).toBe(true)
    wrapper.unmount()
  })
})
