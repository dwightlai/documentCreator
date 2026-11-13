<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword, logout } from '@/api/auth'
import { clearSession, usesOidc } from '@/auth/identity'
import { currentUser, type CurrentUser } from '@/api/identityDirectory'

const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path)
const me = ref<CurrentUser | null>(null)
const passwordOpen = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
onMounted(async () => { try { me.value = await currentUser() } catch { /* identity details are optional outside the directory */ } })
const displayName = computed(() => me.value?.display_name || me.value?.displayName || me.value?.username || '')

async function signOut() {
  try { if (!usesOidc()) await logout() } catch { /* 会话已失效时仍退出本地登录态 */ }
  clearSession()
  await router.replace('/login')
}

async function savePassword() {
  try {
    await changePassword(currentPassword.value, newPassword.value)
    clearSession()
    ElMessage.success('密码已更新，请重新登录')
    await router.replace('/login')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '修改密码失败') }
}
</script>

<template>
  <div class="app-shell">
    <aside class="rail" aria-label="主导航">
      <div class="seal" aria-label="政文智枢">文</div>
      <nav>
        <RouterLink to="/writing" :class="{ active: activePath.startsWith('/writing') }">
          <span class="nav-mark">写</span><span>智能写作</span>
        </RouterLink>
        <RouterLink to="/review" :class="{ active: activePath.startsWith('/review') }">
          <span class="nav-mark">校</span><span>智能审校</span>
        </RouterLink>
        <RouterLink to="/rules" :class="{ active: activePath.startsWith('/rules') }">
          <span class="nav-mark">规</span><span>规则词库</span>
        </RouterLink>
        <RouterLink to="/knowledge" :class="{ active: activePath.startsWith('/knowledge') }"><span class="nav-mark">知</span><span>知识依据</span></RouterLink>
        <RouterLink to="/operations" :class="{ active: activePath.startsWith('/operations') }"><span class="nav-mark">运</span><span>任务运行</span></RouterLink>
        <RouterLink to="/templates" :class="{ active: activePath.startsWith('/templates') }"><span class="nav-mark">模</span><span>输出模板</span></RouterLink>
        <RouterLink to="/governance" :class="{ active: activePath.startsWith('/governance') }"><span class="nav-mark">治</span><span>模型治理</span></RouterLink>
        <RouterLink to="/data-dicts" :class="{ active: activePath.startsWith('/data-dicts') }"><span class="nav-mark">典</span><span>数据字典</span></RouterLink>
        <RouterLink to="/open-api" :class="{ active: activePath.startsWith('/open-api') }"><span class="nav-mark">开</span><span>开放文档</span></RouterLink>
        <RouterLink v-if="me?.roles.includes('platform.admin')" to="/open-apps" :class="{ active: activePath.startsWith('/open-apps') }"><span class="nav-mark">三</span><span>第三方</span></RouterLink>
        <RouterLink v-if="me?.roles.includes('platform.admin')" to="/identity" :class="{ active: activePath.startsWith('/identity') }"><span class="nav-mark">人</span><span>用户权限</span></RouterLink>
      </nav>
      <div class="rail-foot">
        <button type="button" aria-label="帮助中心">?</button>
        <div class="avatar" :title="displayName">{{ displayName.slice(0, 1) || '用' }}</div>
      </div>
    </aside>
    <main class="shell-main">
      <header class="topbar">
        <div>
          <h1><slot name="title" /></h1>
        </div>
        <div class="topbar-meta">
          <span class="secure"><i /> 内网安全环境</span>
          <span class="divider" />
          <span>{{ me?.orgName || displayName }}</span>
          <button v-if="!usesOidc()" type="button" @click="passwordOpen = true">修改密码</button>
          <button v-if="!usesOidc()" type="button" @click="signOut">退出</button>
        </div>
      </header>
      <el-dialog v-model="passwordOpen" title="修改密码" width="420px">
        <form class="password-form" @submit.prevent="savePassword">
          <label>当前密码<input v-model="currentPassword" type="password" required maxlength="72"></label>
          <label>新密码<input v-model="newPassword" type="password" required minlength="8" maxlength="72"></label>
          <footer><button type="button" @click="passwordOpen = false">取消</button><button class="primary-button" type="submit">保存</button></footer>
        </form>
      </el-dialog>
      <slot />
    </main>
  </div>
</template>

<style scoped>
.topbar-meta button { border: 0; background: none; color: var(--red); cursor: pointer; }
.password-form { display: grid; gap: 14px; }
.password-form label { display: grid; gap: 6px; }
.password-form input { padding: 9px; border: 1px solid var(--line); border-radius: 6px; }
.password-form footer { display: flex; justify-content: flex-end; gap: 10px; }
</style>
