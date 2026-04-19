import type { TaskListItem, TaskPriority, TaskStatus } from '@/types/task'

const priorityRank: Record<TaskPriority, number> = {
  URGENT: 0,
  HIGH: 1,
  NORMAL: 2,
  LOW: 3,
}

const statusRank: Record<TaskStatus, number> = {
  IN_PROGRESS: 0,
  ASSIGNED: 1,
  BLOCKED: 2,
  OPEN: 3,
  COMPLETED: 4,
  CANCELLED: 5,
}

function dueTime(task: TaskListItem) {
  return task.dueAt ? new Date(task.dueAt).getTime() : Number.MAX_SAFE_INTEGER
}

export function sortTasksByUrgency(tasks: TaskListItem[]) {
  return [...tasks].sort((first, second) => {
    return (
      statusRank[first.status] - statusRank[second.status]
      || priorityRank[first.priority] - priorityRank[second.priority]
      || dueTime(first) - dueTime(second)
      || first.title.localeCompare(second.title)
    )
  })
}

export function sortTasksByRoute(tasks: TaskListItem[]) {
  return [...tasks].sort((first, second) => {
    return (
      dueTime(first) - dueTime(second)
      || priorityRank[first.priority] - priorityRank[second.priority]
      || first.title.localeCompare(second.title)
    )
  })
}
