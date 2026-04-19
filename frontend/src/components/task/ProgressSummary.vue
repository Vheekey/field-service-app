<script setup lang="ts">
import type { TaskProgressSummary } from '@/types/task'

defineProps<{
  progress: TaskProgressSummary
}>()
</script>

<template>
  <section class="progress-summary" aria-label="Task progress">
    <div class="progress-summary__copy">
      <strong>{{ progress.completed }} of {{ progress.total }} done</strong>
      <span>{{ progress.active }} active · {{ progress.blocked }} blocked</span>
    </div>

    <div class="progress-summary__meter">
      <span>{{ progress.percentComplete }}%</span>
      <div class="progress" role="progressbar" :aria-valuenow="progress.percentComplete" aria-valuemin="0" aria-valuemax="100">
        <div class="progress-bar" :style="{ width: `${progress.percentComplete}%` }" />
      </div>
    </div>
  </section>
</template>
