import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
  ],
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '工作台')} · 政文智枢`
})

export default router
