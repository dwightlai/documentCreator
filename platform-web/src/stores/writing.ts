import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { createWritingTask, deleteWritingTask, listWritingTasks, recommendDocumentType, renameWritingTitle, updateWritingTask, type CreateWritingTask, type TypeSuggestion, type WritingTask } from '@/api/writing'

export const useWritingStore = defineStore('writing', () => {
  const tasks = ref<WritingTask[]>([])
  const selectedId = ref<string>()
  const suggestions = ref<TypeSuggestion[]>([])
  const loading = ref(false)
  const error = ref('')
  const selectedTask = computed(() => tasks.value.find((task) => task.id === selectedId.value))

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const page = await listWritingTasks()
      tasks.value = page.items
      if (!page.items.some(item => item.id === selectedId.value)) selectedId.value = page.items.at(0)?.id
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '任务加载失败'
    } finally {
      loading.value = false
    }
  }

  async function create(payload: CreateWritingTask) {
    loading.value = true
    try {
      const task = await createWritingTask(payload)
      tasks.value.unshift(task)
      selectedId.value = task.id
      return task
    } finally {
      loading.value = false
    }
  }

  async function recommend() {
    if (!selectedId.value) return
    suggestions.value = await recommendDocumentType(selectedId.value)
  }

  async function rename(title: string) {
    const task = selectedTask.value
    if (!task || task.lockVersion == null) throw new Error('请刷新任务后重试')
    const updated = await renameWritingTitle(task.id, title, task.lockVersion)
    tasks.value = tasks.value.map((item) => item.id === updated.id ? updated : item)
  }

  async function remove(id: string) {
    await deleteWritingTask(id)
    tasks.value = tasks.value.filter(item => item.id !== id)
    if (selectedId.value === id) selectedId.value = tasks.value.at(0)?.id
    suggestions.value = []
  }

  async function update(id: string, payload: CreateWritingTask) {
    const previous = tasks.value.find(item => item.id === id)
    if (!previous || previous.lockVersion == null) throw new Error('请刷新任务后重试')
    const changed = await updateWritingTask(id, { ...payload, lockVersion: previous.lockVersion })
    tasks.value = tasks.value.map(item => item.id === id ? changed : item)
    return changed
  }

  return { tasks, selectedId, selectedTask, suggestions, loading, error, load, create, recommend, rename, remove, update }
})
