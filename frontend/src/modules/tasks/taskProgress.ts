import type { TaskListItem, TaskProgressSummary } from '@/types/task'

export function getTaskProgress(tasks: TaskListItem[]): TaskProgressSummary {
  const total = tasks.length
  const completed = tasks.filter((task) => task.status === 'COMPLETED').length
  const blocked = tasks.filter((task) => task.status === 'BLOCKED').length
  const active = tasks.filter((task) => task.status !== 'COMPLETED' && task.status !== 'CANCELLED').length

  return {
    total,
    completed,
    active,
    blocked,
    percentComplete: total === 0 ? 0 : Math.round((completed / total) * 100),
  }
}
