<script setup lang="ts">
import {
  formatDueTime,
  getTaskPriorityLabel,
  getTaskStatusLabel,
  getTaskTypeLabel,
} from '@/modules/tasks/taskDisplay'
import type { TaskListItem } from '@/types/task'

defineProps<{
  task: TaskListItem
}>()
</script>

<template>
  <article class="task-card" :class="`task-card--${task.status.toLowerCase()}`">
    <div class="task-card__topline">
      <span class="task-type">{{ getTaskTypeLabel(task.type) }}</span>
      <span class="task-status">{{ getTaskStatusLabel(task.status) }}</span>
    </div>

    <h2>{{ task.title }}</h2>

    <div class="task-card__details">
      <span>{{ task.address ?? 'Location pinned' }}</span>
      <span>Due {{ formatDueTime(task.dueAt) }}</span>
    </div>

    <div class="task-card__footer">
      <span class="priority-badge" :class="`priority-badge--${task.priority.toLowerCase()}`">
        {{ getTaskPriorityLabel(task.priority) }}
      </span>
      <button class="btn btn-sm btn-outline-dark" type="button">
        Open
      </button>
    </div>
  </article>
</template>
