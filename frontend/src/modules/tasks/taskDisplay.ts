import type { TaskPriority, TaskStatus, TaskType } from '@/types/task'

const typeLabels: Record<TaskType, string> = {
  BATTERY_SWAP: 'Battery swap',
  REBALANCE: 'Rebalance',
  REPAIR: 'Repair',
  INSPECTION: 'Inspection',
  OTHER: 'Other',
}

const statusLabels: Record<TaskStatus, string> = {
  OPEN: 'Open',
  ASSIGNED: 'Next',
  IN_PROGRESS: 'In progress',
  BLOCKED: 'Blocked',
  COMPLETED: 'Done',
  CANCELLED: 'Cancelled',
}

const priorityLabels: Record<TaskPriority, string> = {
  LOW: 'Low',
  NORMAL: 'Normal',
  HIGH: 'High',
  URGENT: 'Urgent',
}

export function getTaskTypeLabel(type: TaskType) {
  return typeLabels[type]
}

export function getTaskStatusLabel(status: TaskStatus) {
  return statusLabels[status]
}

export function getTaskPriorityLabel(priority: TaskPriority) {
  return priorityLabels[priority]
}

export function formatDueTime(value?: string) {
  if (!value) {
    return 'No due time'
  }

  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
