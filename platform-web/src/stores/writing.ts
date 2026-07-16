import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { createWritingTask, listWritingTasks, recommendDocumentType, type CreateWritingTask, type TypeSuggestion, type WritingTask } from '@/api/writing'

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
      selectedId.value ||= page.items.at(0)?.id
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

  return { tasks, selectedId, selectedTask, suggestions, loading, error, load, create, recommend }
})
