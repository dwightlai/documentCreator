import { ref } from 'vue'
import { listDataDictItems } from '@/api/dataDict'

const cache = new Map<string, string[]>()
const loading = new Map<string, Promise<string[]>>()

export async function loadDictLabels(typeCode: string, fallback: string[] = []): Promise<string[]> {
  if (cache.has(typeCode)) return cache.get(typeCode)!
  if (loading.has(typeCode)) return loading.get(typeCode)!
  const task = listDataDictItems(typeCode, true)
    .then((items) => {
      const labels = items.map((item) => item.item_label || item.item_code).filter(Boolean)
      const value = labels.length ? labels : fallback
      cache.set(typeCode, value)
      return value
    })
    .catch(() => {
      cache.set(typeCode, fallback)
      return fallback
    })
    .finally(() => loading.delete(typeCode))
  loading.set(typeCode, task)
  return task
}

export function useDict(typeCode: string, fallback: string[] = []) {
  const options = ref<string[]>(cache.get(typeCode) || [...fallback])
  const ready = ref(cache.has(typeCode))
  void loadDictLabels(typeCode, fallback).then((labels) => {
    options.value = labels
    ready.value = true
  })
  return { options, ready }
}

export function clearDictCache(typeCode?: string) {
  if (typeCode) cache.delete(typeCode)
  else cache.clear()
}
