<script setup lang="ts">
import TaskCard from './TaskCard.vue'
import type { TaskListItem } from '@/types/task'

defineProps<{
  tasks: TaskListItem[]
}>()
</script>

<template>
  <section class="task-list" aria-label="Assigned tasks">
    <TaskCard v-for="task in tasks" :key="task.id" :task="task" />
  </section>
</template>
