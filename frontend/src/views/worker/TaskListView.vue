<script setup lang="ts">
import AppShell from '@/components/layout/AppShell.vue'
import ShiftHeader from '@/components/shift/ShiftHeader.vue'
import ProgressSummary from '@/components/task/ProgressSummary.vue'
import TaskList from '@/components/task/TaskList.vue'
import { useCurrentShift } from '@/composables/useCurrentShift'
import { useWorkerTasks } from '@/composables/useWorkerTasks'
import { useAuthStore } from '@/stores/auth.store'
import { useConnectivityStore } from '@/stores/connectivity.store'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const connectivity = useConnectivityStore()
const router = useRouter()
const { shift } = useCurrentShift()
const { progress, tasks } = useWorkerTasks(auth.workerId ?? undefined)

async function signOut() {
  await auth.signOut()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <AppShell>
    <section class="account-strip">
      <div>
        <p class="eyebrow">Signed in</p>
        <strong>{{ auth.user?.name }}</strong>
      </div>
      <button class="btn btn-outline-secondary btn-sm" type="button" @click="signOut">Sign out</button>
    </section>
    <ShiftHeader :shift="shift" :is-online="connectivity.isOnline" />
    <ProgressSummary :progress="progress" />
    <TaskList :tasks="tasks" />
  </AppShell>
</template>
