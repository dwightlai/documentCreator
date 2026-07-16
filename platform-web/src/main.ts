import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/base.css'
import App from './App.vue'
import router from './router'
import { initializeIdentity } from './auth/identity'

async function bootstrap() {
  await initializeIdentity()
  createApp(App).use(createPinia()).use(router).mount('#app')
}

bootstrap().catch((error: unknown) => {
  document.querySelector('#app')!.innerHTML = '<main style="padding:48px;font-family:sans-serif"><h1>身份认证失败</h1><p>请检查统一身份认证服务后刷新页面。</p></main>'
  console.error(error)
})
