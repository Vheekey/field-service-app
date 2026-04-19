import { createRouter, createWebHistory } from 'vue-router'

import ShellHomeView from '@/views/ShellHomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'shell-home',
      component: ShellHomeView,
    },
  ],
})

export default router
