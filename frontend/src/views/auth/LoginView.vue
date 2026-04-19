<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppShell from '@/components/layout/AppShell.vue'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('worker@example.com')
const password = ref('Password123!')
const errorMessage = ref('')

const isSubmitDisabled = computed(() => auth.isLoading || !email.value || !password.value)

async function submit() {
  errorMessage.value = ''

  try {
    await auth.signIn(email.value, password.value)
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/worker/tasks')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Sign in failed.'
  }
}
</script>

<template>
  <AppShell>
    <section class="login-view">
      <div>
        <p class="eyebrow">Field service</p>
        <h1>Start your shift</h1>
        <p class="login-copy">Sign in to load your assignments and keep your route in sync.</p>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>Email</span>
          <input v-model.trim="email" autocomplete="email" inputmode="email" type="email" required />
        </label>

        <label>
          <span>Password</span>
          <input v-model="password" autocomplete="current-password" type="password" required />
        </label>

        <p v-if="errorMessage" class="login-error" role="alert">{{ errorMessage }}</p>

        <button class="btn btn-primary" type="submit" :disabled="isSubmitDisabled">
          {{ auth.isLoading ? 'Signing in...' : 'Sign in' }}
        </button>
      </form>
    </section>
  </AppShell>
</template>
