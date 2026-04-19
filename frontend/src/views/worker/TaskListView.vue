<script setup lang="ts">
import AppShell from '@/components/layout/AppShell.vue'
import ShiftHeader from '@/components/shift/ShiftHeader.vue'
import ProgressSummary from '@/components/task/ProgressSummary.vue'
import TaskList from '@/components/task/TaskList.vue'
import { useCurrentShift } from '@/composables/useCurrentShift'
import { useWorkerTasks } from '@/composables/useWorkerTasks'
import { useAuthStore } from '@/stores/auth.store'
import { useConnectivityStore } from '@/stores/connectivity.store'
import type { TaskListItem } from '@/types/task'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const connectivity = useConnectivityStore()
const router = useRouter()
const { shift } = useCurrentShift()
const workerTasks = useWorkerTasks(() => auth.user)

async function signOut() {
  await auth.signOut()
  await router.replace({ name: 'login' })
}

function openTask(task: TaskListItem) {
  workerTasks.selectTask(task.id)

  if (!workerTasks.focusMode.value) {
    workerTasks.toggleFocusMode()
  }
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
    <ProgressSummary :progress="workerTasks.progress.value" />

    <section class="task-toolbar" aria-label="Task controls">
      <div class="task-toolbar__sort" role="group" aria-label="Task order">
        <button
          class="btn btn-sm"
          :class="workerTasks.listMode.value === 'urgency' ? 'btn-dark' : 'btn-outline-dark'"
          type="button"
          @click="workerTasks.setListMode('urgency')"
        >
          Urgency
        </button>
        <button
          class="btn btn-sm"
          :class="workerTasks.listMode.value === 'route' ? 'btn-dark' : 'btn-outline-dark'"
          type="button"
          @click="workerTasks.setListMode('route')"
        >
          Route
        </button>
      </div>
      <button class="btn btn-outline-dark btn-sm" type="button" @click="workerTasks.toggleFocusMode">
        {{ workerTasks.focusMode.value ? 'Show all' : 'Focus mode' }}
      </button>
    </section>

    <p v-if="workerTasks.error.value" class="task-alert">
      Offline view. Showing the last saved task list.
    </p>
    <p v-if="workerTasks.updateError.value" class="task-alert task-alert--error">
      {{ workerTasks.updateError.value.message }}
    </p>

    <TaskList
      :can-mutate-task="workerTasks.canMutateTask"
      :is-updating="workerTasks.isUpdating.value"
      :selected-task-id="workerTasks.selectedTaskId.value"
      :tasks="workerTasks.tasks.value"
      @complete="workerTasks.updateTask('complete', $event)"
      @open="openTask"
      @skip="workerTasks.updateTask('skip', $event)"
      @start="workerTasks.updateTask('start', $event)"
    />
  </AppShell>
</template>
