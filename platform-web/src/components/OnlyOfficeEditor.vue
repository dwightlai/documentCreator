<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * ONLYOFFICE 文档编辑器容器：按需加载文档服务器的 api.js，用服务端签名的配置创建编辑器，卸载时销毁。
 * 保存由文档服务器在编辑器关闭后回调平台完成，这里不处理文件内容。
 */
const props = defineProps<{ documentServerUrl: string; config: Record<string, unknown> }>()
const emit = defineEmits<{ error: [message: string] }>()

type DocEditor = { destroyEditor(): void }
type DocsApi = { DocEditor: new (id: string, config: Record<string, unknown>) => DocEditor }
const holderId = `onlyoffice-${Math.random().toString(36).slice(2)}`
let editor: DocEditor | undefined
let unmounted = false

function loadApi(serverUrl: string): Promise<DocsApi> {
  const existing = (window as unknown as { DocsAPI?: DocsApi }).DocsAPI
  if (existing) return Promise.resolve(existing)
  const src = `${serverUrl}/web-apps/apps/api/documents/api.js`
  return new Promise((resolve, reject) => {
    const script = (document.querySelector(`script[src="${src}"]`) as HTMLScriptElement | null) ?? document.createElement('script')
    script.addEventListener('load', () => {
      const api = (window as unknown as { DocsAPI?: DocsApi }).DocsAPI
      if (api) resolve(api)
      else reject(new Error('DocsAPI 未定义'))
    })
    script.addEventListener('error', () => reject(new Error('api.js 加载失败')))
    if (!script.src) {
      script.src = src
      document.head.appendChild(script)
    }
  })
}

onMounted(async () => {
  try {
    const api = await loadApi(props.documentServerUrl)
    if (unmounted) return
    editor = new api.DocEditor(holderId, {
      ...props.config,
      width: '100%',
      height: '100%',
      events: { onError: (event: { data?: { errorDescription?: string } }) => emit('error', event.data?.errorDescription ?? '在线编辑器出错') },
    })
  } catch {
    emit('error', '无法加载 ONLYOFFICE 编辑器，请检查文档服务器地址和网络')
  }
})
onBeforeUnmount(() => {
  unmounted = true
  editor?.destroyEditor()
})
</script>

<template>
  <div class="onlyoffice-frame"><div :id="holderId" /></div>
</template>

<style scoped>
.onlyoffice-frame{height:calc(100vh - 140px);min-height:480px;border:1px solid #ddd7cc;background:#fff}
</style>
