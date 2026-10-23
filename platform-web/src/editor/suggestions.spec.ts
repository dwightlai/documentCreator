import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ParagraphSuggestions from '@/components/ParagraphSuggestions.vue'
import type { DocumentSuggestion } from '@/api/writing'

const suggestion = (patch: Partial<DocumentSuggestion>): DocumentSuggestion => ({
  id: 's1', documentId: 'd1', baseParagraphId: 'p1', baseVersion: 3, currentParagraphId: 'p1', stale: false, status: '待处理',
  proposerId: 'bob', basePlainText: '要马上搞好资金保障', proposedPlainText: '要及时做好资金保障', aiAssisted: false,
  createdAt: '2026-10-23T03:00:00Z', ...patch,
})

describe('ParagraphSuggestions', () => {
  it('renders tracked changes and lets pending suggestions be accepted or rejected', async () => {
    const wrapper = mount(ParagraphSuggestions, { props: { suggestions: [suggestion({})] } })
    expect(wrapper.find('del').text()).toBe('马上搞')
    expect(wrapper.find('ins').text()).toBe('及时做')
    expect(wrapper.text()).toContain('基于 V3')
    await wrapper.get('button.accept').trigger('click')
    await wrapper.findAll('.suggestion-actions button')[1].trigger('click')
    expect((wrapper.emitted('accept')![0][0] as DocumentSuggestion).id).toBe('s1')
    expect((wrapper.emitted('reject')![0][0] as DocumentSuggestion).id).toBe('s1')
  })

  it('blocks accepting stale suggestions and shows decided ones without actions', () => {
    const wrapper = mount(ParagraphSuggestions, {
      props: {
        suggestions: [
          suggestion({ stale: true, aiAssisted: true }),
          suggestion({ id: 's2', status: '已接受', decidedBy: 'alice', acceptedVersion: 4, proposedPlainText: '要马上搞好资金保障' }),
        ],
      },
    })
    const [stale, decided] = wrapper.findAll('li')
    expect(stale.get('button.accept').attributes('disabled')).toBeDefined()
    expect(stale.text()).toContain('已过期')
    expect(stale.text()).toContain('含 AI 改写')
    expect(decided.find('button').exists()).toBe(false)
    expect(decided.text()).toContain('已接受 · alice · V4')
    expect(decided.text()).toContain('仅调整格式')
  })
})
