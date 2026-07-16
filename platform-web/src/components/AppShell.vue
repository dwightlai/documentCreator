<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activePath = computed(() => route.path)
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
      </nav>
      <div class="rail-foot">
        <button type="button" aria-label="帮助中心">?</button>
        <div class="avatar">林</div>
      </div>
    </aside>
    <main class="shell-main">
      <header class="topbar">
        <div>
          <p class="eyebrow">GOVERNMENT DOCUMENT INTELLIGENCE</p>
          <h1><slot name="title" /></h1>
        </div>
        <div class="topbar-meta">
          <span class="secure"><i /> 内网安全环境</span>
          <span class="divider" />
          <span>综合办公处</span>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
