import { apiRequest } from './client'
import type { TaskListItem } from '@/types/task'

export function listWorkerTasks(workerId: string) {
  return apiRequest<TaskListItem[]>(`/workers/${workerId}/tasks`)
}

export function listWorkerRoute(workerId: string) {
  return apiRequest<TaskListItem[]>(`/workers/${workerId}/route`)
}
