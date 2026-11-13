import { createRouter, createWebHistory } from 'vue-router'
import { sessionToken, usesOidc } from '@/auth/identity'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/', redirect: '/writing' },
    {
      path: '/writing',
      name: 'writing',
      component: () => import('@/views/WritingWorkspaceView.vue'),
      meta: { title: '智能写作' },
    },
    { path: '/writing/:taskId/editor', name: 'writing-editor', component: () => import('@/views/WritingEditorView.vue'), meta: { title: '正文协同工作区' } },
    {
      path: '/review',
      name: 'review',
      component: () => import('@/views/ReviewWorkspaceView.vue'),
      meta: { title: '智能审校' },
    },
    {
      path: '/rules',
      name: 'rules',
      component: () => import('@/views/RuleCenterView.vue'),
      meta: { title: '规则与词库' },
    },
    { path: '/knowledge',name:'knowledge',component:()=>import('@/views/KnowledgeCenterView.vue'),meta:{title:'知识与依据'} },
    { path: '/operations', name: 'operations', component: () => import('@/views/TaskCenterView.vue'), meta: { title: '任务与运行中心' } },
    { path: '/templates', name: 'templates', component: () => import('@/views/TemplateCenterView.vue'), meta: { title: '输出模板中心' } },
    { path: '/governance', name: 'governance', component: () => import('@/views/GovernanceConsoleView.vue'), meta: { title: '模型与配置治理' } },
    { path: '/data-dicts', name: 'data-dicts', component: () => import('@/views/DataDictView.vue'), meta: { title: '数据字典' } },
    { path: '/open-api', name: 'open-api', component: () => import('@/views/OpenApiDocsView.vue'), meta: { title: '开放 API 文档' } },
    { path: '/open-apps', name: 'open-apps', component: () => import('@/views/OpenAppsView.vue'), meta: { title: '第三方应用' } },
    { path: '/identity', name: 'identity', component: () => import('@/views/IdentityDirectoryView.vue'), meta: { title: '用户与权限' } },
  ],
})

router.beforeEach(to => {
  if (usesOidc() || to.meta.public) return true
  if (!sessionToken()) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '工作台')} · 政文智枢`
})

export default router
