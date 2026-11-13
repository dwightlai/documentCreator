<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listTenants, login, type TenantOption } from '@/api/auth'
import { saveSession, sessionToken } from '@/auth/identity'

const route = useRoute()
const router = useRouter()
const tenants = ref<TenantOption[]>([])
const tenantCode = ref('local-government')
const username = ref('admin')
const password = ref('')
const error = ref('')
const submitting = ref(false)

onMounted(async () => {
  if (sessionToken()) {
    await router.replace('/')
    return
  }
  try { tenants.value = await listTenants() } catch { /* 登录页仍可手工填写租户编码 */ }
})

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    const result = await login(tenantCode.value.trim(), username.value.trim(), password.value)
    saveSession(result.token)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//')
      ? route.query.redirect : '/writing'
    await router.replace(redirect)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '登录失败'
  } finally { submitting.value = false }
}
</script>

<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>政文智枢</h1>
      <p>使用单位账号登录</p>
      <label>租户
        <select v-if="tenants.length" v-model="tenantCode">
          <option v-for="item in tenants" :key="item.tenant_code" :value="item.tenant_code">{{ item.tenant_name }}</option>
        </select>
        <input v-else v-model="tenantCode" required maxlength="32" autocomplete="organization">
      </label>
      <label>账号<input v-model="username" required maxlength="100" autocomplete="username"></label>
      <label>密码<input v-model="password" type="password" required maxlength="72" autocomplete="current-password"></label>
      <p v-if="error" class="login-error">{{ error }}</p>
      <button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '登录中…' : '登录' }}</button>
      <small>本地初始账号 admin，密码 admin123</small>
    </form>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; background: #f6f4f0; }
.login-card { width: 360px; display: grid; gap: 14px; padding: 32px; background: #fff; border: 1px solid #e1e4ea; border-top: 4px solid var(--red); border-radius: 12px; }
.login-card h1 { margin: 0; font-size: 24px; }
.login-card p, .login-card small { margin: 0; color: #667085; }
.login-card label { display: grid; gap: 6px; color: #344054; font-size: 13px; }
.login-card input, .login-card select { padding: 10px; border: 1px solid #d0d5dd; border-radius: 8px; }
.login-error { color: var(--red); }
.login-card button { min-height: 40px; }
</style>
