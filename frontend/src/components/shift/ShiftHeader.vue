<script setup lang="ts">
import { getShiftStartedTime, getShiftStatusText } from '@/modules/shifts/shiftDisplay'
import type { ShiftSummary } from '@/types/shift'

defineProps<{
  shift: ShiftSummary
  isOnline: boolean
}>()
</script>

<template>
  <header class="shift-header">
    <div>
      <p class="eyebrow mb-1">Today</p>
      <h1 class="h4 mb-0">Field tasks</h1>
    </div>

    <div class="shift-status text-end">
      <span class="status-pill" :class="{ 'status-pill--offline': !isOnline }">
        {{ isOnline ? getShiftStatusText(shift) : 'Offline' }}
      </span>
      <span class="shift-start">Started {{ getShiftStartedTime(shift) }}</span>
    </div>
  </header>
</template>
