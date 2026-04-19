import 'bootstrap/dist/css/bootstrap.min.css'
import './assets/styles.css'

import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { queryClient } from './plugins/queryClient'
import router from './router'

createApp(App)
  .use(createPinia())
  .use(VueQueryPlugin, { queryClient })
  .use(router)
  .mount('#app')
