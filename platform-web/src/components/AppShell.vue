<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { currentUser, type CurrentUser } from '@/api/identityDirectory'

const route = useRoute()
const activePath = computed(() => route.path)
const me = ref<CurrentUser | null>(null)
onMounted(async () => { try { me.value = await currentUser() } catch { /* identity details are optional outside the directory */ } })
const displayName = computed(() => me.value?.display_name || me.value?.displayName || me.value?.username || '')
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
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
