<script setup lang="ts">
import {
  formatDueTime,
  getTaskPriorityLabel,
  getTaskStatusLabel,
  getTaskTypeLabel,
} from '@/modules/tasks/taskDisplay'
import type { TaskListItem } from '@/types/task'

defineProps<{
  canMutate: boolean
  isSelected: boolean
  isUpdating: boolean
  task: TaskListItem
}>()

defineEmits<{
  complete: [task: TaskListItem]
  open: [task: TaskListItem]
  skip: [task: TaskListItem]
  start: [task: TaskListItem]
}>()
</script>

<template>
  <article class="task-card" :class="[`task-card--${task.status.toLowerCase()}`, { 'task-card--selected': isSelected }]">
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
      <button class="btn btn-sm btn-outline-dark task-action" type="button" @click="$emit('open', task)">
        Open
      </button>
    </div>

    <div class="task-card__actions" :aria-label="`Actions for ${task.title}`">
      <button
        v-if="task.status === 'ASSIGNED'"
        class="btn btn-dark task-action"
        type="button"
        :disabled="!canMutate || isUpdating"
        @click="$emit('start', task)"
      >
        In progress
      </button>
      <button
        v-if="canMutate"
        class="btn btn-success task-action"
        type="button"
        :disabled="!canMutate || task.status !== 'IN_PROGRESS' || isUpdating"
        @click="$emit('complete', task)"
      >
        Complete
      </button>
      <button
        class="btn btn-outline-danger task-action"
        type="button"
        :disabled="!canMutate || !['ASSIGNED', 'IN_PROGRESS'].includes(task.status) || isUpdating"
        @click="$emit('skip', task)"
      >
        Skip
      </button>
    </div>

    <p v-if="!canMutate" class="task-card__hint">
      Assigned worker only
    </p>
  </article>
</template>
