import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth.store'
import LoginView from '@/views/auth/LoginView.vue'
import TaskListView from '@/views/worker/TaskListView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/worker/tasks',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/worker/tasks',
      name: 'worker-tasks',
      component: TaskListView,
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  await auth.initialize()

  if (to.meta.public) {
    return auth.isAuthenticated && to.name === 'login' ? { name: 'worker-tasks' } : true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
