<script setup lang="ts">
import TaskCard from './TaskCard.vue'
import type { TaskListItem } from '@/types/task'

defineProps<{
  canMutateTask: (task: TaskListItem) => boolean
  isUpdating: boolean
  selectedTaskId: string | null
  tasks: TaskListItem[]
}>()

defineEmits<{
  complete: [task: TaskListItem]
  open: [task: TaskListItem]
  skip: [task: TaskListItem]
  start: [task: TaskListItem]
}>()
</script>

<template>
  <section class="task-list" aria-label="Assigned tasks">
    <TaskCard
      v-for="task in tasks"
      :key="task.id"
      :can-mutate="canMutateTask(task)"
      :is-selected="selectedTaskId === task.id"
      :is-updating="isUpdating"
      :task="task"
      @complete="$emit('complete', $event)"
      @open="$emit('open', $event)"
      @skip="$emit('skip', $event)"
      @start="$emit('start', $event)"
    />
  </section>
</template>
