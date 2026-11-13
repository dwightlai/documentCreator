<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { authorizationHeader } from '@/auth/identity'

type SwaggerRequest = { headers: Record<string, string> }
type SwaggerUi = { destroy?: () => void }
type SwaggerBundle = ((config: Record<string, unknown>) => SwaggerUi) & { presets: { apis: unknown } }

const page = window as Window & {
  SwaggerUIBundle?: SwaggerBundle
  SwaggerUIStandalonePreset?: unknown
}

let ui: SwaggerUi | undefined

function loadScript(src: string) {
  return new Promise<void>((resolve, reject) => {
    if (document.querySelector(`script[data-src="${src}"]`)) return resolve()
    const script = document.createElement('script')
    script.src = src
    script.dataset.src = src
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(src))
    document.body.appendChild(script)
  })
}

onMounted(async () => {
  if (!document.querySelector('link[data-swagger]')) {
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = '/swagger-ui/swagger-ui.css'
    link.dataset.swagger = '1'
    document.head.appendChild(link)
  }
  await loadScript('/swagger-ui/swagger-ui-bundle.js')
  await loadScript('/swagger-ui/swagger-ui-standalone-preset.js')
  ui = page.SwaggerUIBundle?.({
    dom_id: '#open-api-swagger',
    urls: [{ url: '/v3/api-docs/open-api', name: '开放 API（第三方集成）' }],
    'urls.primaryName': '开放 API（第三方集成）',
    layout: 'StandaloneLayout',
    docExpansion: 'list',
    validatorUrl: null,
    presets: [page.SwaggerUIBundle?.presets.apis, page.SwaggerUIStandalonePreset],
    requestInterceptor: async (req: SwaggerRequest) => {
      Object.assign(req.headers, await authorizationHeader())
      return req
    },
  })
})

onBeforeUnmount(() => ui?.destroy?.())
</script>

<template>
  <AppShell>
    <template #title>开放 API 文档</template>
    <div class="open-api-page">
      <p>第三方调用请在请求头携带 X-Open-App-Code 与 X-Open-Api-Key。应用和密钥在「第三方」中配置，未加入白名单不能调用。</p>
      <div id="open-api-swagger" />
    </div>
  </AppShell>
</template>

<style scoped>
.open-api-page { min-height: calc(100vh - 96px); padding: 16px 24px 24px; background: #f2f0ea; }
.open-api-page p { margin: 0 0 12px; color: #667085; font-size: 13px; }
#open-api-swagger { background: #fff; }
</style>
