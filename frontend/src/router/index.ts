import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../utils/auth'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../pages/login/LoginPage.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/models',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'models',
        name: 'ModelList',
        component: () => import('../components/model/ModelList.vue')
      },
      {
        path: 'models/:id/edit',
        name: 'ModelEditor',
        component: () => import('../components/model/ModelEditor.vue')
      },
      {
        path: 'models/:id/view',
        name: 'ModelFlow',
        component: () => import('../components/model/ModelFlow.vue')
      },
      {
        path: 'metadata',
        name: 'MetadataViewer',
        component: () => import('../components/metadata/MetadataViewer.vue'),
        meta: { title: '元数据管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth !== false)

  if (requiresAuth && !isLoggedIn()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && isLoggedIn()) {
    next('/models')
  } else {
    next()
  }
})

export default router
