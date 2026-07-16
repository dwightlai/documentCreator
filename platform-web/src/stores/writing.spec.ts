import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWritingStore } from './writing'

vi.mock('@/api/writing', () => ({
  listWritingTasks: vi.fn(async () => ({ items: [{ id: 'task-1', taskName: '测试请示', status: '草稿' }], page: 1, size: 20, total: 1 })),
  createWritingTask: vi.fn(),
  recommendDocumentType: vi.fn(),
}))

describe('writing store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('loads and selects the first writing task', async () => {
    const store = useWritingStore()
    await store.load()
    expect(store.tasks).toHaveLength(1)
    expect(store.selectedId).toBe('task-1')
    expect(store.selectedTask?.taskName).toBe('测试请示')
  })
})
